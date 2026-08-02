package btcrenaud.questcodex.recovery

import btcrenaud.questcodex.entries.QuestCodexRecoveryArtifactEntry
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.ref
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.eventTriggers
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.entry.dialogue.currentDialogue
import com.typewritermc.engine.paper.entry.dialogue.isInDialogue
import com.typewritermc.engine.paper.entry.temporal.TemporalSetFrameTrigger
import com.typewritermc.engine.paper.entry.temporal.TemporalStartTrigger
import com.typewritermc.engine.paper.entry.temporal.currentTemporalFrame
import com.typewritermc.engine.paper.entry.temporal.isPlayingTemporal
import com.typewritermc.engine.paper.entry.entries.DialogueEntry
import com.typewritermc.engine.paper.entry.AssetManager
import com.typewritermc.engine.paper.events.AsyncCinematicEndEvent
import com.typewritermc.engine.paper.events.AsyncCinematicStartEvent
import com.typewritermc.engine.paper.events.AsyncCinematicTickEvent
import com.typewritermc.engine.paper.events.AsyncDialogueEndEvent
import com.typewritermc.engine.paper.events.AsyncDialogueStartEvent
import com.typewritermc.engine.paper.events.AsyncDialogueSwitchEvent
import com.typewritermc.engine.paper.plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.koin.java.KoinJavaComponent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Best-effort recovery for interactions that Typewriter tears down on quit.
 * The latest snapshot is deliberately persisted asynchronously so an abrupt
 * disconnect still has a recent safe point to restore on the next join.
 */
object QuestCodexRecoveryService {
    private const val DEFAULT_RETENTION_SECONDS = 120L
    private const val DEFAULT_RESTORE_DELAY_TICKS = 2L

    private val snapshots = ConcurrentHashMap<UUID, RecoverySnapshot>()
    private val temporalPages = ConcurrentHashMap<UUID, String>()
    /**
     * Typewriter tears down active interactions after PlayerQuitEvent. Those
     * teardown events must not erase the snapshot captured for the disconnect.
     */
    private val disconnectedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val restoringPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val saveMutex = Mutex()
    private val mutationVersion = AtomicLong()
    private val persistedVersion = AtomicLong()
    private val listener = RecoveryListener()

    @Volatile
    private var artifact: QuestCodexRecoveryArtifactEntry? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var retentionSeconds = DEFAULT_RETENTION_SECONDS

    @Volatile
    private var restoreDelayTicks = DEFAULT_RESTORE_DELAY_TICKS

    @Volatile
    private var scope: CoroutineScope? = null

    suspend fun initialize(
        configuredArtifact: QuestCodexRecoveryArtifactEntry?,
        configuredEnabled: Boolean = true,
        configuredRetentionSeconds: Long = DEFAULT_RETENTION_SECONDS,
        configuredRestoreDelayTicks: Long = DEFAULT_RESTORE_DELAY_TICKS,
    ) {
        shutdownInternal()
        artifact = configuredArtifact
        enabled = configuredEnabled && configuredArtifact != null
        retentionSeconds = configuredRetentionSeconds.coerceIn(10L, 86_400L)
        restoreDelayTicks = configuredRestoreDelayTicks.coerceIn(1L, 20L)
        if (!enabled) return

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        withContext(Dispatchers.IO) { load() }
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }

    suspend fun shutdown() = shutdownInternal()

    private suspend fun shutdownInternal() {
        if (enabled) withContext(Dispatchers.IO) { persistUntilClean() }
        HandlerList.unregisterAll(listener)
        scope?.cancel()
        scope = null
        snapshots.clear()
        temporalPages.clear()
        disconnectedPlayers.clear()
        restoringPlayers.clear()
        artifact = null
        enabled = false
        mutationVersion.set(0L)
        persistedVersion.set(0L)
    }

    private suspend fun load() {
        val entry = artifact ?: return
        val manager = KoinJavaComponent.get<AssetManager>(AssetManager::class.java)
        if (!manager.containsAsset(entry)) return
        val raw = manager.fetchStringAsset(entry) ?: return
        val now = System.currentTimeMillis()
        RecoverySnapshotCodec.decode(raw, now).forEach { snapshot ->
            val playerId = runCatching { UUID.fromString(snapshot.playerId) }.getOrNull() ?: return@forEach
            snapshots[playerId] = snapshot
        }
    }

    private fun capture(playerId: UUID, interaction: RecoveryInteraction?, persist: Boolean = true) {
        if (!enabled) return
        if (playerId in restoringPlayers) return
        if (interaction == null) {
            if (snapshots.remove(playerId) != null && persist) markDirty()
            return
        }

        val now = System.currentTimeMillis()
        snapshots[playerId] = RecoverySnapshot(
            playerId = playerId.toString(),
            updatedAt = now,
            expiresAt = now + retentionSeconds * 1_000L,
            interaction = interaction,
        )
        if (persist) markDirty()
    }

    private fun captureCurrent(player: org.bukkit.entity.Player) {
        val playerId = player.uniqueId
        val temporalPage = temporalPages[playerId]
        val temporalFrame = player.currentTemporalFrame()
        if (temporalPage != null && temporalFrame != null && player.isPlayingTemporal()) {
           plugin.logger.info("[QuestCodex Recovery] Snapshot temporal: ${player.name} page=$temporalPage frame=$temporalFrame")
           capture(playerId, RecoveryInteraction.Temporal(temporalPage, temporalFrame))
           return
        }

        val dialogue = player.currentDialogue
        if (player.isInDialogue && dialogue != null) {
           plugin.logger.info("[QuestCodex Recovery] Snapshot dialogue: ${player.name} entry=${dialogue.id}")
           capture(playerId, RecoveryInteraction.Dialogue(dialogue.id))
           return
        }

        // Depending on the event ordering, Typewriter may already have torn
        // down the interaction before PlayerQuitEvent reaches this listener.
        // Keep the latest event-driven snapshot instead of replacing it with
        // an empty one in that case.
        if (snapshots.containsKey(playerId)) {
            plugin.logger.info("[QuestCodex Recovery] Keeping latest snapshot for ${player.name} after interaction teardown")
            return
        }

        plugin.logger.info("[QuestCodex Recovery] No interaction to snapshot for ${player.name}, clearing")

       capture(playerId, null)
   }

    private fun restore(player: org.bukkit.entity.Player) {
        if (!enabled || !player.isOnline) {
            plugin.logger.info("[QuestCodex Recovery] Skip restore ${player.name}: enabled=$enabled online=${player.isOnline}")
            return
        }
        if (player.isPlayingTemporal() || player.isInDialogue) {
            plugin.logger.info("[QuestCodex Recovery] Skip restore ${player.name}: already in temporal=${player.isPlayingTemporal()} dialogue=${player.isInDialogue}")
            return
        }

        val snapshot = snapshots.remove(player.uniqueId)
        if (snapshot == null) {
            plugin.logger.info("[QuestCodex Recovery] No snapshot found for ${player.name} — re-loading from artifact")
            scope?.launch {
                load()
                val reloaded = snapshots.remove(player.uniqueId)
                if (reloaded != null) {
                    // Asset I/O runs on Dispatchers.IO; all player/Typewriter state
                    // must be restored on the player's Folia region thread.
                    player.scheduler.run(plugin, { _ ->
                        if (!enabled || !player.isOnline) {
                            plugin.logger.info("[QuestCodex Recovery] Skip reloaded restore ${player.name}: enabled=${enabled} online=${player.isOnline}")
                            return@run
                        }
                        if (player.isPlayingTemporal() || player.isInDialogue) {
                            plugin.logger.info("[QuestCodex Recovery] Skip reloaded restore ${player.name}: already in temporal=${player.isPlayingTemporal()} dialogue=${player.isInDialogue}")
                            return@run
                        }
                        plugin.logger.info("[QuestCodex Recovery] Restoring reloaded snapshot for ${player.name}: ${reloaded.interaction}")
                       restoreFromSnapshot(player, reloaded)
                       markDirty()
                   }, null)
                } else {
                    plugin.logger.info("[QuestCodex Recovery] Still no snapshot for ${player.name} after reload")
                }
            }
            return
        }
        plugin.logger.info("[QuestCodex Recovery] Restoring snapshot for ${player.name}: ${snapshot.interaction}")
        markDirty()
       restoreFromSnapshot(player, snapshot)
   }

    private fun restoreFromSnapshot(player: org.bukkit.entity.Player, snapshot: RecoverySnapshot) {
        restoringPlayers.add(player.uniqueId)
        try {
            when (val interaction = snapshot.interaction) {
                is RecoveryInteraction.Temporal -> restoreTemporal(player, interaction)
                is RecoveryInteraction.Dialogue -> restoreDialogue(player, interaction)
            }
            // Schedule removal of the flag after a short delay, once the restored interaction has started
            player.scheduler.runDelayed(plugin, { _ ->
                restoringPlayers.remove(player.uniqueId)
            }, null, 40L) // 2 seconds — enough for the restored interaction to fully start
        } catch (error: Throwable) {
            restoringPlayers.remove(player.uniqueId)
            plugin.logger.warning(
                "[QuestCodex] Could not restore interaction for ${player.name}: ${error.message}"
            )
        }
    }

    private fun restoreTemporal(player: org.bukkit.entity.Player, interaction: RecoveryInteraction.Temporal) {
        if (Query.findPageById(interaction.pageId) == null) return
        temporalPages[player.uniqueId] = interaction.pageId
        listOf(TemporalStartTrigger(interaction.pageId)).triggerFor(player, context())
        if (interaction.frame <= 0) return

        player.scheduler.runDelayed(
            plugin,
            { TemporalSetFrameTrigger(interaction.frame).triggerFor(player, context()) },
            null,
            1L,
        )
    }

    private fun restoreDialogue(player: org.bukkit.entity.Player, interaction: RecoveryInteraction.Dialogue) {
        val dialogue = Query.findById<DialogueEntry>(interaction.entryId) ?: return
        listOf(dialogue.ref()).eventTriggers.triggerFor(player, context())
    }

    private fun markDirty() {
        mutationVersion.incrementAndGet()
        scope?.launch { persistUntilClean() }
    }

    private suspend fun persistUntilClean() {
        val entry = artifact ?: return
        saveMutex.withLock {
            val manager = KoinJavaComponent.get<AssetManager>(AssetManager::class.java)
            while (persistedVersion.get() < mutationVersion.get()) {
                val version = mutationVersion.get()
                val now = System.currentTimeMillis()
                val active = snapshots.values.filter { it.expiresAt > now }
                manager.storeStringAsset(entry, RecoverySnapshotCodec.encode(active))
                persistedVersion.set(version)
            }
        }
    }

    private class RecoveryListener : Listener {
        @EventHandler
        fun onCinematicStart(event: AsyncCinematicStartEvent) {
            temporalPages[event.player.uniqueId] = event.pageId
            capture(event.player.uniqueId, RecoveryInteraction.Temporal(event.pageId, 0))
        }

        @EventHandler
        fun onCinematicTick(event: AsyncCinematicTickEvent) {
            val pageId = temporalPages[event.player.uniqueId] ?: return
            capture(
                event.player.uniqueId,
                RecoveryInteraction.Temporal(pageId, event.frame),
                persist = event.frame % 5 == 0,
            )
        }

        @EventHandler
        fun onCinematicEnd(event: AsyncCinematicEndEvent) {
            temporalPages.remove(event.player.uniqueId)
            if (event.player.uniqueId in disconnectedPlayers) return
            if (snapshots[event.player.uniqueId]?.interaction is RecoveryInteraction.Temporal) {
                capture(event.player.uniqueId, null)
            }
        }

        @EventHandler
        fun onDialogueStart(event: AsyncDialogueStartEvent) {
            event.player.currentDialogue?.let {
                capture(event.player.uniqueId, RecoveryInteraction.Dialogue(it.id))
            }
        }

        @EventHandler
        fun onDialogueSwitch(event: AsyncDialogueSwitchEvent) {
            event.player.currentDialogue?.let {
                capture(event.player.uniqueId, RecoveryInteraction.Dialogue(it.id))
            }
        }

        @EventHandler
        fun onDialogueEnd(event: AsyncDialogueEndEvent) {
            if (event.player.uniqueId in disconnectedPlayers) return
            if (snapshots[event.player.uniqueId]?.interaction is RecoveryInteraction.Dialogue) {
                capture(event.player.uniqueId, null)
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun onQuit(event: PlayerQuitEvent) {
            disconnectedPlayers.add(event.player.uniqueId)
            captureCurrent(event.player)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            event.player.scheduler.runDelayed(
                plugin,
                {
                    try {
                        restore(event.player)
                    } finally {
                        disconnectedPlayers.remove(event.player.uniqueId)
                    }
                },
                null,
                restoreDelayTicks,
            )
        }
    }
}

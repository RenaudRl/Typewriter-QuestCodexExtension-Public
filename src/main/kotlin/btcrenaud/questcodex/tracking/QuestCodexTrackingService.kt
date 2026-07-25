package btcrenaud.questcodex.tracking

import btcrenaud.questcodex.entries.QuestCodexTrackingArtifactEntry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.ref
import com.typewritermc.engine.paper.entry.AssetManager
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.isQuestTracked
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.entity.Player
import org.koin.java.KoinJavaComponent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent QuestCodex-owned tracking overlay for the official Quest extension.
 *
 * The official tracker remains the source of the single primary HUD quest. This
 * service persists every additional quest id, in order, through Typewriter's
 * AssetManager so local and custom asset-storage implementations remain supported.
 */
object QuestCodexTrackingService {
    enum class ToggleResult { TRACKED, UNTRACKED, LIMIT_REACHED }

    private const val FORMAT_VERSION = 1
    private val trackedQuestIds = ConcurrentHashMap<UUID, LinkedHashSet<String>>()
    private val saveMutex = Mutex()
    private val mutationVersion = AtomicLong()
    private val persistedVersion = AtomicLong()

    @Volatile
    private var artifact: QuestCodexTrackingArtifactEntry? = null
    @Volatile
    private var maxTrackedQuests: Int = 1
    @Volatile
    private var scope: CoroutineScope? = null

    suspend fun initialize(
        trackingArtifact: QuestCodexTrackingArtifactEntry?,
        configuredLimit: Int,
    ) {
        scope?.cancel()
        trackedQuestIds.clear()
        artifact = trackingArtifact
        maxTrackedQuests = configuredLimit.coerceAtLeast(1)
        mutationVersion.set(0)
        persistedVersion.set(0)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val entry = trackingArtifact ?: return
        val manager = KoinJavaComponent.get<AssetManager>(AssetManager::class.java)
        if (!manager.containsAsset(entry)) return
        val raw = manager.fetchStringAsset(entry) ?: return
        load(raw)
    }

    fun trackedQuestIds(player: Player): List<String> {
        var seeded = false
        val tracked = trackedQuestIds.computeIfAbsent(player.uniqueId) {
            LinkedHashSet<String>().apply {
                Query.find<QuestEntry>()
                    .firstOrNull { candidate ->
                        candidate.questStatus(player) == QuestStatus.ACTIVE &&
                            player isQuestTracked candidate.ref()
                    }
                    ?.let {
                        add(it.id)
                        seeded = true
                    }
            }
        }
        var changed = false
        val result = synchronized(tracked) {
            changed = tracked.removeIf { id ->
                Query.findById<QuestEntry>(id)?.questStatus(player) != QuestStatus.ACTIVE
            }
            while (tracked.size > maxTrackedQuests) {
                tracked.remove(tracked.first())
                changed = true
            }
            tracked.toList()
        }
        if (seeded || changed) markDirty()
        return result
    }

    fun isTracked(player: Player, questId: String): Boolean =
        trackedQuestIds(player).contains(questId)

    fun primaryQuestId(player: Player): String? =
        trackedQuestIds(player).lastOrNull()

    /**
     * Restores the persisted primary quest after the official Quest session has
     * completed its own setup. Invalid/completed ids are pruned by trackedQuestIds.
     */
    fun restorePrimary(player: Player) {
        val quest = trackedQuestIds(player)
            .lastOrNull()
            ?.let { Query.findById<QuestEntry>(it) }
            ?: return
        if (!(player isQuestTracked quest.ref())) {
            player trackQuest quest.ref()
        }
    }

    fun toggle(
        player: Player,
        quest: QuestEntry,
        multiTrackingEnabled: Boolean,
        maxTrackedQuests: Int,
    ): ToggleResult {
        val questId = quest.id
        val tracked = trackedQuestIds.computeIfAbsent(player.uniqueId) {
            LinkedHashSet<String>().apply {
                Query.find<QuestEntry>()
                    .firstOrNull { candidate ->
                        candidate.questStatus(player) == QuestStatus.ACTIVE &&
                            player isQuestTracked candidate.ref()
                    }
                    ?.let { add(it.id) }
            }
        }
        val result = synchronized(tracked) {
            if (tracked.remove(questId)) {
                val questRef = quest.ref()
                if (player isQuestTracked questRef) {
                    player.unTrackQuest()
                    tracked.lastOrNull()
                        ?.let { Query.findById<QuestEntry>(it) }
                        ?.let { player trackQuest it.ref() }
                }
                ToggleResult.UNTRACKED
            } else {
                if (!multiTrackingEnabled) {
                    tracked.clear()
                } else if (tracked.size >= maxTrackedQuests.coerceAtLeast(1)) {
                    return@synchronized ToggleResult.LIMIT_REACHED
                }
                tracked.add(questId)
                player trackQuest quest.ref()
                ToggleResult.TRACKED
            }
        }
        if (result != ToggleResult.LIMIT_REACHED) markDirty()
        return result
    }

    suspend fun shutdown() {
        persistUntilClean()
        scope?.cancel()
        scope = null
        artifact = null
        trackedQuestIds.clear()
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
                manager.storeStringAsset(entry, serialize())
                persistedVersion.set(version)
            }
        }
    }

    private fun serialize(): String {
        val root = JsonObject()
        root.addProperty("version", FORMAT_VERSION)
        val players = JsonObject()
        trackedQuestIds.entries
            .sortedBy { it.key.toString() }
            .forEach { (uuid, quests) ->
                val values = synchronized(quests) { quests.toList() }
                if (values.isNotEmpty()) {
                    players.add(uuid.toString(), JsonArray().apply {
                        values.forEach { add(it) }
                    })
                }
            }
        root.add("players", players)
        return root.toString()
    }

    private fun load(raw: String) {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return
        if (root.get("version")?.asInt != FORMAT_VERSION) return
        val players = root.getAsJsonObject("players") ?: return
        players.entrySet().forEach { (uuidText, value) ->
            val uuid = runCatching { UUID.fromString(uuidText) }.getOrNull() ?: return@forEach
            val ids = value.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element ->
                    element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.takeLast(maxTrackedQuests)
                .orEmpty()
            if (ids.isNotEmpty()) trackedQuestIds[uuid] = LinkedHashSet(ids)
        }
    }
}

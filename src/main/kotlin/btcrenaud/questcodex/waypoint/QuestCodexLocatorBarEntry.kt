package btcrenaud.questcodex.waypoint

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.AudienceDisplay
import com.typewritermc.engine.paper.entry.entries.AudienceEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.TickableDisplay
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.toBukkitLocation
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Renders the same targets as `Quest Codex Waypoint` on the vanilla locator bar.
 *
 * It costs no entity and no packet display, so it pairs well with a 3D waypoint
 * rather than replacing it. Requires Minecraft 1.21.6+.
 */
@Entry("quest_codex_locator_bar", "Quest Codex Locator Bar", Colors.CYAN, "mdi:map-marker-path")
@Tags("quest", "waypoint", "audience")
class QuestCodexLocatorBarEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Target resolved from a position, active objective or entity.")
    val target: WaypointTarget = TrackedObjectiveWaypointTarget(TrackedObjectiveSelection.ONE_PER_QUEST),
    @Help("Colour of the locator bar dots, in hex format like '#55FF55'.")
    val color: Var<String> = ConstVar("#55FF55"),
    @Help("Optional icon style key, for example 'minecraft:default'.")
    val style: Var<String> = ConstVar(""),
    @Help("Maximum number of simultaneous dots.")
    val maxTargets: Int = 5,
) : AudienceEntry {
    override suspend fun display(): AudienceDisplay = QuestCodexLocatorBarDisplay(this)
}

/** A tracked dot and the block position it was created for. */
private class ActiveLocatorWaypoint(
    val handle: Any,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val color: String,
    val style: String,
)

class QuestCodexLocatorBarDisplay(
    private val entry: QuestCodexLocatorBarEntry,
) : AudienceDisplay(), TickableDisplay {
    private val tracked = ConcurrentHashMap<UUID, MutableMap<String, ActiveLocatorWaypoint>>()
    private val resolver = WaypointTargetResolver()

    /** Previous values of the two things whose change resets the client's waypoint list. */
    private val wasDead = ConcurrentHashMap<UUID, Boolean>()
    private val lastWorld = ConcurrentHashMap<UUID, UUID>()

    override fun onPlayerAdd(player: Player) {
        tracked.computeIfAbsent(player.uniqueId) { HashMap() }
    }

    /**
     * A respawn or dimension change resets the client's waypoint list: untrack the
     * stale handles, since keeping them would leave duplicates once [update] re-tracks.
     *
     * Polled from the tick loop for the same reason as the 3D waypoint display —
     * a Bukkit listener on an `AudienceDisplay` was verified not to receive
     * `PlayerRespawnEvent`.
     */
    private fun detectSessionReset(player: Player, active: MutableMap<String, ActiveLocatorWaypoint>) {
        val worldId = player.world.uid
        val dead = player.isDead
        val respawned = wasDead.put(player.uniqueId, dead) == true && !dead
        val previousWorld = lastWorld.put(player.uniqueId, worldId)
        if (!respawned && (previousWorld == null || previousWorld == worldId)) return
        active.values.forEach { LocatorBarBridge.untrack(player, it.handle) }
        active.clear()
    }

    override fun onPlayerRemove(player: Player) {
        tracked.remove(player.uniqueId)?.values?.forEach { LocatorBarBridge.untrack(player, it.handle) }
        wasDead.remove(player.uniqueId)
        lastWorld.remove(player.uniqueId)
    }

    override fun tick() {
        if (!LocatorBarBridge.isSupported) return
        players.forEach { player -> update(player) }
    }

    override fun dispose() {
        tracked.forEach { (uuid, active) ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach
            active.values.forEach { LocatorBarBridge.untrack(player, it.handle) }
        }
        tracked.clear()
        super.dispose()
    }

    private fun update(player: Player) {
        val active = tracked.computeIfAbsent(player.uniqueId) { HashMap() }
        detectSessionReset(player, active)
        val color = entry.color.get(player)
        val style = entry.style.get(player).ifBlank { "minecraft:default" }

        val targets = resolver.resolveAll(entry.target, player, entry.maxTargets)
            .mapNotNull { target ->
                val location = target.position.toBukkitLocation()
                if (!matchesPlayerWorld(target.position.world.identifier, player.world.name, player.world.uid)) {
                    return@mapNotNull null
                }
                target.key to location
            }

        val liveKeys = targets.mapTo(HashSet()) { it.first }
        active.entries.removeIf { (key, waypoint) ->
            if (key in liveKeys) false else { LocatorBarBridge.untrack(player, waypoint.handle); true }
        }

        targets.forEach { (key, location) ->
            val blockX = location.x.roundToInt()
            val blockY = location.y.roundToInt()
            val blockZ = location.z.roundToInt()
            val current = active[key]
            // Re-tracking every tick floods the client; only do it when something moved.
            if (current != null &&
                current.blockX == blockX && current.blockY == blockY && current.blockZ == blockZ &&
                current.color == color && current.style == style
            ) {
                return@forEach
            }
            current?.let { LocatorBarBridge.untrack(player, it.handle) }
            val handle = LocatorBarBridge.create(blockX, blockY, blockZ, color, style) ?: return@forEach
            LocatorBarBridge.track(player, handle)
            active[key] = ActiveLocatorWaypoint(handle, blockX, blockY, blockZ, color, style)
        }
    }
}

/**
 * Bridge to the Adventure Waypoint API (Minecraft 1.21.6+), reached by reflection
 * so the extension still loads on older servers.
 *
 * Lookups are resolved once. Unlike a silent try/catch, the first failure is
 * reported so a broken bridge cannot masquerade as "no waypoints to show".
 */
internal object LocatorBarBridge {
    private val waypointClass = runCatching { Class.forName("net.kyori.adventure.audience.Waypoint") }.getOrNull()
    private val audienceClass = runCatching { Class.forName("net.kyori.adventure.audience.Audience") }.getOrNull()
    private val textColorClass = runCatching { Class.forName("net.kyori.adventure.text.format.TextColor") }.getOrNull()
    private val keyClass = runCatching { Class.forName("net.kyori.adventure.key.Key") }.getOrNull()

    private val fromHexString = runCatching {
        textColorClass?.getMethod("fromHexString", String::class.java)
    }.getOrNull()
    private val keyOf = runCatching { keyClass?.getMethod("key", String::class.java) }.getOrNull()
    private val waypointFactory = runCatching { audienceClass?.getMethod("waypoint") }.getOrNull()
    private val trackMethod = runCatching {
        waypointClass?.let { Class.forName("org.bukkit.entity.Player").getMethod("trackWaypoint", it) }
    }.getOrNull()
    private val untrackMethod = runCatching {
        waypointClass?.let { Class.forName("org.bukkit.entity.Player").getMethod("removeWaypoint", it) }
    }.getOrNull()

    val isSupported: Boolean =
        waypointClass != null && trackMethod != null && untrackMethod != null && waypointFactory != null

    @Volatile
    private var reportedFailure = false

    fun create(x: Int, y: Int, z: Int, colorHex: String, styleKey: String): Any? {
        if (!isSupported) return null
        return try {
            val color = fromHexString?.invoke(null, colorHex)
            val key = keyOf?.invoke(null, styleKey)
            val factory = waypointFactory?.invoke(null) ?: return null
            factory.javaClass
                .getMethod("vector", textColorClass, keyClass, Int::class.java, Int::class.java, Int::class.java)
                .invoke(factory, color, key, x, y, z)
        } catch (error: Throwable) {
            report("create", error)
            null
        }
    }

    fun track(player: Player, waypoint: Any) {
        if (!isSupported) return
        try {
            trackMethod?.invoke(player, waypoint)
        } catch (error: Throwable) {
            report("track", error)
        }
    }

    fun untrack(player: Player, waypoint: Any) {
        if (!isSupported) return
        try {
            untrackMethod?.invoke(player, waypoint)
        } catch (error: Throwable) {
            report("untrack", error)
        }
    }

    private fun report(operation: String, error: Throwable) {
        if (reportedFailure) return
        reportedFailure = true
        plugin.logger.warning(
            "[QuestCodex] Locator bar unavailable ($operation): ${error.message}. Requires Minecraft 1.21.6+."
        )
    }
}

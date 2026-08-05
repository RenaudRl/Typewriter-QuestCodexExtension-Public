package btcrenaud.questcodex.waypoint

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared vertical decluttering across every waypoint entry shown to one player.
 *
 * [declutterOffsets] only sees the markers of a single entry, so two entries whose
 * destinations line up — the common case in `HUD_LOCKED`, where every marker sits
 * straight ahead — end up on the exact same spot. This registry is the missing
 * cross-entry half: each entry publishes the offsets it took, and the entries that
 * render after it step over them.
 *
 * Entries render on independent tasks, so a claim is a fact about a previous tick,
 * not a lock. Two rules keep that stable:
 *
 * - **Priority is the entry id.** An entry only yields to entries whose id sorts
 *   before its own. Without a total order, two entries would each move away from
 *   the other every tick and oscillate forever.
 * - **Claims expire.** An entry that stops rendering — quest untracked, player gone
 *   to another world — would otherwise reserve its levels for good.
 *
 * Free of Bukkit types, like the rest of the placement maths.
 */
internal object WaypointDeclutterRegistry {
    /** Long enough to survive a slow render tick, short enough to forgive a crash. */
    private const val CLAIM_TTL_MILLIS = 2_000L

    private class EntryClaims(val claims: List<DeclutterClaim>, val stampMillis: Long)

    private val byPlayer = ConcurrentHashMap<UUID, ConcurrentHashMap<String, EntryClaims>>()

    /**
     * Resolves the vertical offset of every marker of [entryId], avoiding both its
     * own markers and those held by higher-priority entries, then records the
     * result for the entries rendering after it.
     */
    fun offsets(
        playerId: UUID,
        entryId: String,
        directions: List<Vec3>,
        minAngleDegrees: Double,
        spacing: Double,
    ): List<Double> {
        if (minAngleDegrees <= 0.0 || spacing == 0.0 || directions.isEmpty()) {
            release(playerId, entryId)
            return List(directions.size) { 0.0 }
        }

        val entries = byPlayer.computeIfAbsent(playerId) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        entries.entries.removeIf { (_, held) -> now - held.stampMillis > CLAIM_TTL_MILLIS }

        val occupied = entries.entries
            .filter { (otherId, _) -> otherId < entryId }
            .flatMap { (_, held) -> held.claims }

        val resolved = declutterOffsets(directions, minAngleDegrees, spacing, occupied)
        val claims = directions.mapIndexed { index, direction ->
            DeclutterClaim(direction.normalized(), resolved[index])
        }
        entries[entryId] = EntryClaims(claims, now)
        return resolved
    }

    /** Drops the claims of [entryId] so other entries can reuse its levels at once. */
    fun release(playerId: UUID, entryId: String) {
        val entries = byPlayer[playerId] ?: return
        entries.remove(entryId)
        if (entries.isEmpty()) byPlayer.remove(playerId)
    }

    /** Drops every claim of a player at once. Used to isolate tests from one another. */
    fun releasePlayer(playerId: UUID) {
        byPlayer.remove(playerId)
    }
}

package btcrenaud.questcodex.waypoint

import btcrenaud.questcodex.tracking.QuestCodexTrackingService
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.ref
import com.typewritermc.core.entries.priority
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entity.AudienceEntityDisplay
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.quest.entries.ObjectiveEntry
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.entries.interfaces.LocatableObjective
import com.typewritermc.quest.entries.questShowingObjectives
import com.typewritermc.engine.paper.plugin
import org.bukkit.entity.Player
import java.util.UUID

/**
 * A single waypoint destination.
 *
 * [key] identifies the destination across ticks so the renderer can keep the
 * packet entities of a target alive instead of respawning them every refresh.
 */
data class ResolvedWaypointTarget(
    val key: String,
    val position: Position,
    val label: String = "",
    val priority: Int = 0,
)

class WaypointTargetResolver {

    /**
     * Resolves every destination a waypoint entry should render, capped at [maxTargets].
     *
     * Returns an empty list when nothing is currently targetable.
     */
    fun resolveAll(
        target: WaypointTarget,
        player: Player,
        maxTargets: Int,
    ): List<ResolvedWaypointTarget> {
        val limit = maxTargets.coerceAtLeast(1)
        return when (target) {
            is FixedWaypointTarget ->
                listOf(ResolvedWaypointTarget(FIXED_TARGET_KEY, target.position.get(player)))

            is ObjectiveWaypointTarget ->
                listOfNotNull(resolveObjective(target.objective.get(), player))

            is TrackedObjectiveWaypointTarget ->
                resolveTrackedObjectives(target.selection, player, limit)

            is EntityWaypointTarget ->
                listOfNotNull(resolveEntity(target.entity.get(), player))
        }.take(limit)
    }

    /**
     * Objectives of every quest QuestCodex tracks.
     *
     * The official tracker only knows the primary quest, so multi-tracking is
     * read from QuestCodex's own tracking service instead.
     */
    private fun trackedObjectives(player: Player): List<LocatableObjective> {
        val questIds = QuestCodexTrackingService.trackedQuestIds(player)
        if (questIds.isEmpty()) return emptyList()
        return questIds
            .mapNotNull { Query.findById<QuestEntry>(it)?.ref() }
            .flatMap { quest -> player.questShowingObjectives(quest).toList() }
            .filterIsInstance<LocatableObjective>()
            .distinctBy { it.id }
    }

    private fun resolveObjective(objective: LocatableObjective?, player: Player): ResolvedWaypointTarget? {
        if (objective == null || !isShowingObjective(objective, player)) return null
        return closest(objective, player)
    }

    private fun resolveTrackedObjectives(
        selection: TrackedObjectiveSelection,
        player: Player,
        limit: Int,
    ): List<ResolvedWaypointTarget> {
        val objectives = trackedObjectives(player)
        plugin.logger.fine("[QuestCodex Waypoint] Tracked objectives for ${player.name}: ${objectives.size}")
        if (objectives.isEmpty()) return emptyList()

        val selected: List<LocatableObjective> = when (selection) {
            TrackedObjectiveSelection.HIGHEST_PRIORITY -> listOfNotNull(objectives.maxByOrNull { it.priority })
            TrackedObjectiveSelection.FIRST -> listOfNotNull(objectives.firstOrNull())
            TrackedObjectiveSelection.CLOSEST -> listOfNotNull(objectives.minByOrNull { objectiveDistance(it, player) })
            TrackedObjectiveSelection.ALL ->
                objectives.sortedWith(compareByDescending<LocatableObjective> { it.priority }
                    .thenBy { objectiveDistance(it, player) })
            TrackedObjectiveSelection.ONE_PER_QUEST ->
                objectives
                    .groupBy { (it as? ObjectiveEntry)?.quest }
                    .values
                    .mapNotNull { questObjectives -> questObjectives.maxByOrNull { it.priority } }
                    .sortedWith(compareByDescending<LocatableObjective> { it.priority }
                        .thenBy { objectiveDistance(it, player) })
        }

        return selected.take(limit).mapNotNull { closest(it, player) }
    }

    private fun resolveEntity(entity: EntityInstanceEntry?, player: Player): ResolvedWaypointTarget? {
        if (entity == null) return null
        val display = entity.ref().findDisplay<AudienceEntityDisplay>() ?: return null
        val position = display.position(player.uniqueId) ?: return null
        return ResolvedWaypointTarget("entity:${entity.id}", position, entity.displayName.get(player))
    }

    private fun closest(objective: LocatableObjective, player: Player): ResolvedWaypointTarget? {
        val objectiveId = objective.id
        val positions = objective.positions(player)
        val inWorld = positions.filter {
            matchesPlayerWorld(it.world.identifier, player.world.name, player.world.uid)
        }
        plugin.logger.fine("[QuestCodex Waypoint] Objective $objectiveId: totalPositions=${positions.size} inWorld=${inWorld.size}")
        val position = inWorld
            .minByOrNull { it.toBukkitLocation().distanceSquared(player.location) }
            ?: run {
                plugin.logger.fine("[QuestCodex Waypoint] No in-world position for objective")
                return null
            }
        val label = (objective as? ObjectiveEntry)?.display(player).orEmpty()
        plugin.logger.fine("[QuestCodex Waypoint] closest found: label=$label pos=$position")
        return ResolvedWaypointTarget("objective:$objectiveId", position, label, objective.priority)
    }

    private fun objectiveDistance(objective: LocatableObjective, player: Player): Double {
        return objective.positions(player)
            .filter {
                matchesPlayerWorld(it.world.identifier, player.world.name, player.world.uid)
            }
            .minOfOrNull { it.toBukkitLocation().distanceSquared(player.location) }
            ?: Double.POSITIVE_INFINITY
    }

    private fun isShowingObjective(objective: LocatableObjective, player: Player): Boolean {
        return trackedObjectives(player).any { it.id == objective.id }
    }

    companion object {
        const val FIXED_TARGET_KEY = "fixed"
    }
}

/**
 * Typewriter's Bukkit bridge serializes world identifiers as UUIDs. Older public
 * pages may still contain the Bukkit world name, so both representations are
 * intentionally accepted at this extension boundary.
 */
internal fun matchesPlayerWorld(
    positionWorldIdentifier: String,
    playerWorldName: String,
    playerWorldUuid: UUID,
): Boolean {
    val identifier = positionWorldIdentifier.trim()
    return identifier.equals(playerWorldUuid.toString(), ignoreCase = true) ||
        identifier.equals(playerWorldName.trim(), ignoreCase = true)
}

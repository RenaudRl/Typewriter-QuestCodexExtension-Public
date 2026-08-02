package btcrenaud.questcodex.waypoint

import com.typewritermc.core.entries.ref
import com.typewritermc.core.entries.priority
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.entry.entity.AudienceEntityDisplay
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.quest.entries.ObjectiveEntry
import com.typewritermc.quest.entries.interfaces.LocatableObjective
import com.typewritermc.quest.entries.trackedShowingObjectives
import com.typewritermc.engine.paper.plugin
import org.bukkit.entity.Player
import java.util.UUID

data class ResolvedWaypointTarget(
    val position: Position,
    val label: String = "",
)

class WaypointTargetResolver {

    fun resolve(target: WaypointTarget, player: Player): ResolvedWaypointTarget? = when (target) {
        is FixedWaypointTarget -> target.position.get(player).let { ResolvedWaypointTarget(it) }
        is ObjectiveWaypointTarget -> resolveObjective(target.objective.get(), player)
        is TrackedObjectiveWaypointTarget -> resolveTrackedObjective(target.selection, player)
        is EntityWaypointTarget -> resolveEntity(target.entity.get(), player)
    }

    private fun resolveObjective(objective: LocatableObjective?, player: Player): ResolvedWaypointTarget? {
        if (objective == null || !isShowingObjective(objective, player)) return null
        return closest(objective, player)
    }

    private fun resolveTrackedObjective(
        selection: TrackedObjectiveSelection,
        player: Player,
    ): ResolvedWaypointTarget? {
        val objectives = player.trackedShowingObjectives()
            .filterIsInstance<LocatableObjective>()
            .toList()
        plugin.logger.fine("[QuestCodex Waypoint] Tracked objectives for ${player.name}: ${objectives.size}")
        if (objectives.isEmpty()) return null

        val objective = when (selection) {
            TrackedObjectiveSelection.HIGHEST_PRIORITY -> objectives.maxByOrNull { it.priority }
            TrackedObjectiveSelection.FIRST -> objectives.firstOrNull()
            TrackedObjectiveSelection.CLOSEST -> objectives.minByOrNull { objectiveDistance(it, player) }
        } ?: return null
        return closest(objective, player)
    }

    private fun resolveEntity(entity: EntityInstanceEntry?, player: Player): ResolvedWaypointTarget? {
        if (entity == null) return null
        val display = entity.ref().findDisplay<AudienceEntityDisplay>() ?: return null
        val position = display.position(player.uniqueId) ?: return null
        return ResolvedWaypointTarget(position, entity.displayName.get(player))
    }

    private fun closest(objective: LocatableObjective, player: Player): ResolvedWaypointTarget? {
        val positions = objective.positions(player)
        val inWorld = positions.filter {
            matchesPlayerWorld(it.world.identifier, player.world.name, player.world.uid)
        }
        plugin.logger.fine("[QuestCodex Waypoint] Objective ${(objective as? ObjectiveEntry)?.id}: totalPositions=${positions.size} inWorld=${inWorld.size}")
        val position = inWorld
            .minByOrNull { it.toBukkitLocation().distanceSquared(player.location) }
            ?: run {
                plugin.logger.fine("[QuestCodex Waypoint] No in-world position for objective")
                return null
            }
        val label = (objective as? ObjectiveEntry)?.display(player).orEmpty()
        plugin.logger.fine("[QuestCodex Waypoint] closest found: label=$label pos=$position")
        return ResolvedWaypointTarget(position, label)
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
        return player.trackedShowingObjectives().any { it.id == objective.id }
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

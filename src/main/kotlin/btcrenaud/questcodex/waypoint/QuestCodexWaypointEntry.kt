package btcrenaud.questcodex.waypoint

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.AlgebraicTypeInfo
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.core.utils.point.Position
import com.typewritermc.core.utils.point.Vector
import com.typewritermc.engine.paper.entry.entries.AudienceDisplay
import com.typewritermc.engine.paper.entry.entries.AudienceEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.quest.entries.interfaces.LocatableObjective
import kotlinx.serialization.Serializable
import org.bukkit.Material

enum class WaypointLayerPlacement {
    HUD,
    TARGET,
}

enum class TrackedObjectiveSelection {
    HIGHEST_PRIORITY,
    FIRST,
    CLOSEST,

    /** Every tracked locatable objective, best priority first, capped by `maxTargets`. */
    ALL,

    /** The best objective of each tracked quest, capped by `maxTargets`. */
    ONE_PER_QUEST,
}

/**
 * How a waypoint is placed in the world.
 *
 * `HUD_LOCKED` is the historical behaviour and stays the default so existing
 * pages keep rendering exactly as before.
 */
enum class WaypointDisplayMode {
    /** Pinned in front of the player's eyes, restricted to `hudVisibilityAngle`. */
    HUD_LOCKED,

    /**
     * Projected onto a sphere around the player's eyes, along the true direction
     * of the target. Looking at the marker means looking at the target, so no
     * visibility cone is applied.
     */
    WORLD_DIRECTIONAL,

    /** Placed on the target itself. Only visible within the client's entity range. */
    TARGET_ANCHORED,

    /** `TARGET_ANCHORED` up close, `WORLD_DIRECTIONAL` further away, blended in between. */
    ADAPTIVE,
}

/** Per-layer placement override. `INHERIT` uses the entry's `displayMode`. */
enum class WaypointLayerMode {
    INHERIT,
    HUD_LOCKED,
    WORLD_DIRECTIONAL,
    TARGET_ANCHORED,
    ADAPTIVE,
}

enum class BeaconMode {
    HUD,
    TARGET,
    BOTH,
}

@AlgebraicTypeInfo("fixed_waypoint_target", Colors.GREEN, "mdi:map-marker")
data class FixedWaypointTarget(
    @Help("Fixed target position.")
    val position: Var<Position> = ConstVar(Position.ORIGIN),
) : WaypointTarget

@AlgebraicTypeInfo("objective_waypoint_target", Colors.BLUE_VIOLET, "mdi:target")
data class ObjectiveWaypointTarget(
    @Help("Locatable objective used as the target. It is shown only while the objective is active and tracked.")
    val objective: Ref<out LocatableObjective> = emptyRef(),
) : WaypointTarget

@AlgebraicTypeInfo("tracked_objective_waypoint_target", Colors.BLUE_VIOLET, "mdi:target-account")
data class TrackedObjectiveWaypointTarget(
    @Help("How the active locatable objective is selected from the tracked quest.")
    val selection: TrackedObjectiveSelection = TrackedObjectiveSelection.HIGHEST_PRIORITY,
) : WaypointTarget

@AlgebraicTypeInfo("entity_waypoint_target", Colors.ORANGE, "mdi:account-marker")
data class EntityWaypointTarget(
    @Help("Entity instance whose current per-player position is used as the target.")
    val entity: Ref<out EntityInstanceEntry> = emptyRef(),
) : WaypointTarget

@Serializable
sealed interface WaypointTarget

@Serializable
sealed interface WaypointLayer

@AlgebraicTypeInfo("waypoint_text_layer", Colors.BLUE, "mdi:format-text")
data class WaypointTextLayer(
    @Help("Whether this layer is rendered for the current player.")
    val enabled: Var<Boolean> = ConstVar(true),
    @Help("Multi-line text displayed. Use \\n for newlines. Placeholders: {icon}, {distance}, {distance_m}, {distance_km}, {elevation}, {direction}, {vertical_direction}, {target}, {world_name}, {height_diff}, {eta}.")
    @Placeholder
    @Colored
    val text: Var<String> = ConstVar("<white>{distance}</white>"),
    @Help("Position relative to the selected anchor.")
    val offset: Vector = Vector(0.0, 0.0, 0.0),
    @Help("HUD or target placement. Only used when the resolved display mode is HUD_LOCKED.")
    val placement: WaypointLayerPlacement = WaypointLayerPlacement.HUD,
    @Help("Overrides the entry's display mode for this layer.")
    val mode: WaypointLayerMode = WaypointLayerMode.INHERIT,
    @Help("Display scale.")
    val scale: Float = 1.0f,
    @Help("Maximum text line width.")
    val lineWidth: Int = 200,
    @Help("Show a text shadow.")
    val shadow: Boolean = true,
    @Help("Render the text through blocks.")
    val seeThrough: Boolean = true,
    @Help("Enable gentle vertical oscillation (breathing animation).")
    val breathing: Boolean = false,
    @Help("Breathing amplitude in blocks.")
    val breathingAmplitude: Double = 0.3,
    @Help("Breathing period in seconds.")
    val breathingPeriod: Double = 2.0,
    @Help("Opacity (0.0-1.0). Requires a shadow for the text background.")
    val backgroundOpacity: Byte = 0,
) : WaypointLayer

@AlgebraicTypeInfo("waypoint_block_layer", Colors.ORANGE, "mdi:cube-outline")
data class WaypointBlockLayer(
    @Help("Whether this layer is rendered for the current player.")
    val enabled: Var<Boolean> = ConstVar(true),
    @Help("Block displayed by the block display.")
    val material: Var<Material> = ConstVar(Material.BEACON),
    @Help("Position relative to the selected anchor.")
    val offset: Vector = Vector(0.0, 0.0, 0.0),
    @Help("HUD or target placement. Only used when the resolved display mode is HUD_LOCKED.")
    val placement: WaypointLayerPlacement = WaypointLayerPlacement.TARGET,
    @Help("Overrides the entry's display mode for this layer.")
    val mode: WaypointLayerMode = WaypointLayerMode.INHERIT,
    @Help("Display scale.")
    val scale: Float = 1.0f,
) : WaypointLayer

@AlgebraicTypeInfo("waypoint_beacon_layer", Colors.PURPLE, "mdi:beacon")
data class WaypointBeaconLayer(
    @Help("Whether this layer is rendered for the current player.")
    val enabled: Var<Boolean> = ConstVar(true),
    @Help("Controls where the beacon appears: HUD, at the target, or both.")
    val mode: BeaconMode = BeaconMode.TARGET,
    @Help("Block used for the vertical beacon beam.")
    val material: Var<Material> = ConstVar(Material.BEACON),
    @Help("Beam height in blocks. Set to zero to reach the world's maximum height.")
    val height: Var<Int> = ConstVar(64),
    @Help("Beam thickness.")
    val thickness: Var<Float> = ConstVar(0.35f),
    @Help("Rotation speed in degrees per refresh.")
    val rotationSpeed: Var<Float> = ConstVar(0.0f),
    @Help("Vertical offset from the target position.")
    val offsetY: Var<Double> = ConstVar(1.0),
    @Help("Maximum distance at which the beacon is sent to the player.")
    val viewDistance: Var<Double> = ConstVar(200.0),
    @Help("Block light level used by the client.")
    val blockLight: Var<Int> = ConstVar(15),
    @Help("Sky light level used by the client.")
    val skyLight: Var<Int> = ConstVar(15),
) : WaypointLayer

@Entry("quest_codex_waypoint", "Quest Codex Waypoint", Colors.BLUE, "mdi:map-marker-radius")
@Tags("quest", "waypoint", "audience")
class QuestCodexWaypointEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Target resolved from a position, active objective or entity.")
    val target: WaypointTarget = TrackedObjectiveWaypointTarget(),
    @Help("Optional text or resource-pack glyph inserted by the {icon} placeholder in text layers.")
    @Placeholder
    @Colored
    val icon: Var<String> = ConstVar(""),
    @Help("Display layers rendered for the target.")
    val layers: List<WaypointLayer> = listOf(WaypointTextLayer()),
    @Help("How waypoints are placed in the world. Layers may override this individually.")
    val displayMode: WaypointDisplayMode = WaypointDisplayMode.HUD_LOCKED,
    @Help("Maximum number of simultaneous targets rendered. Only selections that yield several targets are affected.")
    val maxTargets: Int = 5,
    @Help("Sphere radius, in blocks, used by WORLD_DIRECTIONAL and ADAPTIVE placement.")
    val projectionRadius: Double = 12.0,
    @Help("Keep a constant apparent size when a projected marker sits closer than the projection radius.")
    val constantApparentSize: Boolean = true,
    @Help("Blend distance above nearTargetDistance over which ADAPTIVE moves from the target to the projection sphere.")
    val adaptiveTransitionBand: Double = 4.0,
    @Help("Minimum angular gap, in degrees, kept between two projected markers. Set to zero to disable decluttering.")
    val declutterAngle: Double = 4.0,
    @Help("Vertical spacing, in blocks, applied to projected markers that would otherwise overlap.")
    val declutterSpacing: Double = 0.6,
    @Help("Refresh interval in ticks. One tick gives the smoothest HUD tracking.")
    val refreshTicks: Int = 1,
    @Help("Client-side interpolation of marker movement, in ticks. Raise it if markers stutter while running or flying; lower it for a more immediate response.")
    val interpolationTicks: Int = 2,
    @Help("Optional hard-hide distance. Set to zero to keep the text visible above the target when close.")
    val hideWithinDistance: Double = 0.0,
    @Help("Distance within which text layers move above the target instead of staying in front of the player.")
    val nearTargetDistance: Double = 10.0,
    @Help("Vertical offset of near-target text above the target position.")
    val nearTargetVerticalOffset: Double = 2.25,
    @Help("Distance at which target-anchored layers are hidden.")
    val targetViewDistance: Double = 200.0,
    @Help("Horizontal visibility angle for the waypoint. 180 degrees means the front hemisphere; near-target text ignores this filter.")
    val hudVisibilityAngle: Double = 180.0,
    @Help("Distance in front of the player's eyes for HUD layers.")
    val hudForwardDistance: Double = 5.5,
    @Help("Vertical offset for HUD layers.")
    val hudVerticalOffset: Double = 1.0,
) : AudienceEntry {
    override suspend fun display(): AudienceDisplay = QuestCodexWaypointDisplay(this)
}

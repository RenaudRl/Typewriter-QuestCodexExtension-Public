package btcrenaud.questcodex.waypoint

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3f
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.extensions.packetevents.meta
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.engine.paper.utils.toPacketLocation
import me.tofaa.entitylib.EntityLib
import me.tofaa.entitylib.meta.EntityMeta
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta
import me.tofaa.entitylib.meta.display.BlockDisplayMeta
import me.tofaa.entitylib.meta.display.TextDisplayMeta
import me.tofaa.entitylib.wrapper.WrapperEntity
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

private const val LOCATION_EPSILON_SQUARED = 0.000025
private const val OCCLUSION_CHECK_TICKS = 5
private const val PLAYER_WALK_SPEED_MS = 4.317

internal fun isWithinHorizontalViewCone(
    playerYaw: Float,
    playerX: Double,
    playerZ: Double,
    targetX: Double,
    targetZ: Double,
    visibilityAngle: Double,
): Boolean {
    val dx = targetX - playerX
    val dz = targetZ - playerZ
    if (dx * dx + dz * dz <= 1.0E-8) return true

    val angle = visibilityAngle.coerceIn(0.0, 360.0)
    if (angle >= 360.0) return true

    val yawRadians = Math.toRadians(playerYaw.toDouble())
    val forwardX = -sin(yawRadians)
    val forwardZ = cos(yawRadians)
    val length = hypot(dx, dz)
    val dot = (forwardX * dx + forwardZ * dz) / length
    return dot >= cos(Math.toRadians(angle / 2.0)) - 1.0E-9
}

internal data class DisplayTranslation(val x: Float, val y: Float, val z: Float)

internal fun centeredDisplayTranslation(
    scale: Float,
    scaleY: Float,
    rotation: Float,
    centerVertically: Boolean,
): DisplayTranslation {
    val safeScale = scale.coerceAtLeast(0.01f)
    val safeScaleY = scaleY.coerceAtLeast(0.01f)
    val halfWidth = safeScale / 2f
    val halfDepth = safeScale / 2f
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    val cosRotation = cos(radians)
    val sinRotation = sin(radians)
    // Display transformations apply translation before the left rotation.
    // Compensate the rotated center so the block never pivots around a corner.
    val rotatedCenterX = cosRotation * halfWidth + sinRotation * halfDepth
    val rotatedCenterZ = -sinRotation * halfWidth + cosRotation * halfDepth
    return DisplayTranslation(
        -rotatedCenterX,
        if (centerVertically) -safeScaleY / 2f else 0f,
        -rotatedCenterZ,
    )
}

private class PacketWaypointEntity(
    private val player: Player,
    private val type: com.github.retrooper.packetevents.protocol.entity.type.EntityType,
) {
    private val wrapper: WrapperEntity by lazy(LazyThreadSafetyMode.NONE) {
        val uuid = EntityLib.getPlatform().entityUuidProvider.provide(type)
        val entityId = EntityLib.getPlatform().entityIdProvider.provide(uuid, type)
        WrapperEntity(entityId, uuid, type, EntityMeta.createMeta(entityId, type))
    }

    private var spawned = false

    fun spawn(location: Location) {
        if (spawned) return
        wrapper.spawn(location.toPacketLocation())
        wrapper.addViewer(player.uniqueId)
        spawned = true
    }

    fun teleport(location: Location) {
        if (!spawned) spawn(location) else wrapper.teleport(location.toPacketLocation())
    }

    fun text(
        value: Component,
        scale: Float,
        lineWidth: Int,
        shadow: Boolean,
        seeThrough: Boolean,
        backgroundOpacity: Byte,
    ) {
        wrapper.meta<TextDisplayMeta> {
            text = value
            this.lineWidth = lineWidth.coerceIn(1, 4_096)
            isShadow = shadow
            isSeeThrough = seeThrough
            backgroundColor = (backgroundOpacity.coerceIn(0, 127).toInt() shl 24) or 0x0F_0F_0F
        }
        wrapper.meta<AbstractDisplayMeta> {
            billboardConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
            positionRotationInterpolationDuration = 1
        }
        displayScale(scale)
    }

    fun block(
        material: Material,
        scale: Float,
        scaleY: Float = scale,
        rotation: Float,
        blockLight: Int,
        skyLight: Int,
        centerVertically: Boolean = true,
    ) {
        wrapper.meta<BlockDisplayMeta> {
            blockId = SpigotConversionUtil.fromBukkitBlockData(material.createBlockData()).globalId
        }
        wrapper.meta<AbstractDisplayMeta> {
            billboardConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
            this.brightnessOverride =
                (blockLight.coerceIn(0, 15) shl 4) or (skyLight.coerceIn(0, 15) shl 20)
            val radians = Math.toRadians(rotation.toDouble()).toFloat()
            leftRotation = Quaternion4f(0f, sin(radians / 2f), 0f, cos(radians / 2f))
            val centeredTranslation = centeredDisplayTranslation(scale, scaleY, rotation, centerVertically)
            translation = Vector3f(
                centeredTranslation.x,
                centeredTranslation.y,
                centeredTranslation.z,
            )
            positionRotationInterpolationDuration = 1
        }
        displayScale(scale, scaleY = scaleY)
    }

    private fun displayScale(scale: Float, scaleY: Float = scale) {
        wrapper.meta<AbstractDisplayMeta> {
            this.scale = Vector3f(scale.coerceAtLeast(0.01f), scaleY.coerceAtLeast(0.01f), scale.coerceAtLeast(0.01f))
        }
    }

    fun dispose() {
        if (!spawned) return
        wrapper.despawn()
        wrapper.remove()
        spawned = false
    }
}

private class ActiveWaypointLayer(
    val entity: PacketWaypointEntity,
    var lastLocation: Location? = null,
    var lastPayload: Any? = null,
)

private class WaypointPlayerState {
    val pending = AtomicBoolean(false)
    val layers = ConcurrentHashMap<Int, ActiveWaypointLayer>()
    var beaconAngle = 0f
    var startTimeMillis = System.currentTimeMillis()
}

class QuestCodexWaypointDisplay(
    private val entry: QuestCodexWaypointEntry,
) : AudienceDisplay(), TickableDisplay {
    private val states = ConcurrentHashMap<UUID, WaypointPlayerState>()
    private var tickCounter = 0
    private val resolver = WaypointTargetResolver()

    override fun onPlayerAdd(player: Player) {
        states.computeIfAbsent(player.uniqueId) { WaypointPlayerState() }
        plugin.logger.info("[QuestCodex Waypoint] Player added: ${player.name}")
    }

    override fun onPlayerRemove(player: Player) {
        val state = states.remove(player.uniqueId) ?: return
        clearLayersImmediate(state)
        plugin.logger.info("[QuestCodex Waypoint] Player removed: ${player.name}")
    }

    override fun tick() {
        plugin.server.onlinePlayers.forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            val refreshTicks = entry.refreshTicks.coerceIn(1, 20)
            if (tickCounter % refreshTicks != 0) return@forEach
            if (!state.pending.compareAndSet(false, true)) return@forEach

            player.scheduler.run(plugin, { _ ->
                try {
                    renderPlayer(player, state)
                } catch (error: Throwable) {
                    plugin.logger.warning(
                        "[QuestCodex] Waypoint '${entry.id}' failed for ${player.name}: ${error.message}"
                    )
                    clearLayers(state)
                } finally {
                    state.pending.set(false)
                }
            }, null)
        }
        tickCounter = (tickCounter + 1) % 20
    }

    override fun dispose() {
        states.values.forEach { clearLayersImmediate(it) }
        states.clear()
        super.dispose()
    }

    private fun clearLayersImmediate(state: WaypointPlayerState) {
        state.layers.values.forEach { it.entity.dispose() }
        state.layers.clear()
    }

    private fun renderPlayer(player: Player, state: WaypointPlayerState) {
        if (!player.isOnline) {
            clearLayers(state)
            return
        }

        val target = resolver.resolve(entry.target, player)
        if (target == null) {
            clearLayers(state)
            return
        }

        plugin.logger.fine("[QuestCodex Waypoint] Rendering for ${player.name}: target=${target.label} pos=${target.position}")

        val targetLocation = target.position.toBukkitLocation().apply {
            // Objective positions may carry capture yaw/pitch; displays must stay level.
            yaw = 0f
            pitch = 0f
        }
        val playerLocation = player.location
        if (targetLocation.world != playerLocation.world) {
            clearLayers(state)
            return
        }

        val distance = playerLocation.distance(targetLocation)
        if (!distance.isFinite()) {
            clearLayers(state)
            return
        }
        val nearTarget = distance <= entry.nearTargetDistance.coerceAtLeast(0.0)
        val targetInView = isWithinHorizontalViewCone(
            playerYaw = playerLocation.yaw,
            playerX = playerLocation.x,
            playerZ = playerLocation.z,
            targetX = targetLocation.x,
            targetZ = targetLocation.z,
            visibilityAngle = entry.hudVisibilityAngle,
        )
        if (!nearTarget && !targetInView) {
            clearLayers(state)
            return
        }
        if (!nearTarget && distance <= entry.hideWithinDistance.coerceAtLeast(0.0)) {
            clearLayers(state)
            return
        }

        val direction = directionFor(playerLocation, targetLocation)
        val elapsedSeconds = (System.currentTimeMillis() - state.startTimeMillis) / 1_000.0
        val desired = mutableSetOf<Int>()
        val interactionContext = context()
        entry.layers.forEachIndexed { index, layer ->
            if (!layerEnabled(layer, player, interactionContext)) return@forEachIndexed

            val placement = layerPlacement(layer)
            val isBeacon = layer is WaypointBeaconLayer

            val location = when {
                isBeacon && layer.mode == BeaconMode.HUD -> hudLocation(
                    player, layerOffset(layer), entry.hudForwardDistance, entry.hudVerticalOffset,
                    occlusionCheck = tickCounter % OCCLUSION_CHECK_TICKS == 0,
                )
                layer is WaypointTextLayer && nearTarget -> targetTextLocation(
                    targetLocation, layer, entry.nearTargetVerticalOffset,
                )
                isBeacon && layer.mode == BeaconMode.BOTH -> {
                    if (distance > layer.viewDistance.get(player, interactionContext).coerceAtLeast(1.0)) {
                        return@forEachIndexed
                    }
                    state.beaconAngle = (
                        state.beaconAngle + layer.rotationSpeed.get(player, interactionContext)
                    ) % 360f
                    val targetPos = targetLocation.clone().add(
                        0.0, layer.offsetY.get(player, interactionContext), 0.0,
                    )
                    val hudPos = hudLocation(
                        player, layerOffset(layer), entry.hudForwardDistance, entry.hudVerticalOffset,
                        occlusionCheck = tickCounter % OCCLUSION_CHECK_TICKS == 0,
                    )
                    desired += index
                    desired += -(index + 1)
                    spawnBeaconAt(state, index, targetPos, layer, player, state.beaconAngle, interactionContext)
                    spawnBeaconAt(state, -(index + 1), hudPos, layer, player, state.beaconAngle, interactionContext)
                    return@forEachIndexed
                }
                isBeacon -> {
                    if (distance > layer.viewDistance.get(player, interactionContext).coerceAtLeast(1.0)) {
                        return@forEachIndexed
                    }
                    targetLocation.clone().add(
                        0.0, layer.offsetY.get(player, interactionContext), 0.0,
                    )
                }
                placement == WaypointLayerPlacement.HUD -> hudLocation(
                    player, layerOffset(layer), entry.hudForwardDistance, entry.hudVerticalOffset,
                    occlusionCheck = tickCounter % OCCLUSION_CHECK_TICKS == 0,
                )
                placement == WaypointLayerPlacement.TARGET -> {
                    if (distance > entry.targetViewDistance) return@forEachIndexed
                    val offset = layerOffset(layer)
                    targetLocation.clone().add(offset.x, offset.y, offset.z)
                }
                else -> return@forEachIndexed
            }

            desired += index
            val active = state.layers[index] ?: createLayer(player, layer).also { state.layers[index] = it }
            val loc = applyBreathing(location, layer, elapsedSeconds, animate = nearTarget)
            if (active.lastLocation == null || active.lastLocation!!.world != loc.world ||
                active.lastLocation!!.distanceSquared(loc) > LOCATION_EPSILON_SQUARED
            ) {
                active.entity.teleport(loc)
            }
            applyLayer(active, layer, player, target, distance, direction, state, interactionContext)
            active.lastLocation = loc
        }

        state.layers.entries.removeIf { (index, active) ->
            if (index in desired) false else { active.entity.dispose(); true }
        }
    }

    private fun spawnBeaconAt(
        state: WaypointPlayerState, index: Int, location: Location,
        layer: WaypointBeaconLayer, player: Player, angle: Float,
        interactionContext: com.typewritermc.core.interaction.InteractionContext,
    ) {
        val active = state.layers[index] ?: ActiveWaypointLayer(
            PacketWaypointEntity(player, EntityTypes.BLOCK_DISPLAY)
        ).also { state.layers[index] = it }
        if (active.lastLocation == null || active.lastLocation!!.world != location.world ||
            active.lastLocation!!.distanceSquared(location) > LOCATION_EPSILON_SQUARED
        ) {
            active.entity.teleport(location)
        }
        applyBeaconLayer(active, layer, player, angle, interactionContext)
        active.lastLocation = location
    }

    private fun applyBreathing(
        location: Location,
        layer: WaypointLayer,
        elapsedSeconds: Double,
        animate: Boolean,
    ): Location {
        if (layer !is WaypointTextLayer || !layer.breathing || !animate) return location
        val period = layer.breathingPeriod.coerceAtLeast(1.0)
        val amplitude = layer.breathingAmplitude.coerceAtLeast(0.0)
        if (amplitude <= 0.0) return location
        val offset = sin(elapsedSeconds * 2.0 * PI / period) * amplitude
        return location.clone().add(0.0, offset, 0.0)
    }

    private fun createLayer(player: Player, layer: WaypointLayer): ActiveWaypointLayer {
        val type = when (layer) {
            is WaypointTextLayer -> EntityTypes.TEXT_DISPLAY
            is WaypointBlockLayer, is WaypointBeaconLayer -> EntityTypes.BLOCK_DISPLAY
        }
        return ActiveWaypointLayer(PacketWaypointEntity(player, type))
    }

    private fun applyLayer(
        active: ActiveWaypointLayer, layer: WaypointLayer, player: Player,
        target: ResolvedWaypointTarget, distance: Double, direction: WaypointDirection,
        state: WaypointPlayerState,
        interactionContext: com.typewritermc.core.interaction.InteractionContext,
    ) {
        when (layer) {
            is WaypointTextLayer -> {
                val text = formatText(
                    layer.text.get(player, interactionContext),
                    player,
                    target,
                    distance,
                    direction,
                    entry.icon.get(player, interactionContext),
                )
                val rendered = text.parsePlaceholders(player).asMini()
                val payload = listOf(rendered, layer.scale, layer.lineWidth, layer.shadow, layer.seeThrough, layer.backgroundOpacity)
                if (payload != active.lastPayload) {
                    active.entity.text(rendered, layer.scale, layer.lineWidth, layer.shadow, layer.seeThrough, layer.backgroundOpacity)
                    active.lastPayload = payload
                }
            }

            is WaypointBlockLayer -> {
                val material = layer.material.get(player, interactionContext)
                val payload = listOf(material, layer.scale)
                if (payload != active.lastPayload) {
                    active.entity.block(material, layer.scale, rotation = 0f, blockLight = 15, skyLight = 15, centerVertically = true)
                    active.lastPayload = payload
                }
            }

            is WaypointBeaconLayer -> {
                state.beaconAngle = (state.beaconAngle + layer.rotationSpeed.get(player, interactionContext)) % 360f
                applyBeaconLayer(active, layer, player, state.beaconAngle, interactionContext)
            }
        }
    }

    private fun applyBeaconLayer(
        active: ActiveWaypointLayer,
        layer: WaypointBeaconLayer,
        player: Player,
        angle: Float,
        interactionContext: com.typewritermc.core.interaction.InteractionContext,
    ) {
        val material = layer.material.get(player, interactionContext)
        val requestedHeight = layer.height.get(player, interactionContext)
        val height = if (requestedHeight <= 0) {
            (player.world.maxHeight - player.location.y).coerceAtLeast(1.0).toFloat()
        } else {
            requestedHeight.toFloat()
        }.coerceAtMost(512.0f)
        val thickness = layer.thickness.get(player, interactionContext).coerceAtLeast(0.05f)
        val blockLight = layer.blockLight.get(player, interactionContext)
        val skyLight = layer.skyLight.get(player, interactionContext)
        val payload = listOf(material, height, thickness, angle, blockLight, skyLight)
        if (payload != active.lastPayload) {
            active.entity.block(
                material = material,
                scale = thickness,
                scaleY = height,
                rotation = angle,
                blockLight = blockLight,
                skyLight = skyLight,
                centerVertically = false,
            )
            active.lastPayload = payload
        }
    }

    private fun clearLayers(state: WaypointPlayerState) {
        state.layers.values.forEach { it.entity.dispose() }
        state.layers.clear()
    }

    private fun layerPlacement(layer: WaypointLayer): WaypointLayerPlacement = when (layer) {
        is WaypointTextLayer -> layer.placement
        is WaypointBlockLayer -> layer.placement
        is WaypointBeaconLayer -> WaypointLayerPlacement.TARGET
    }

    private fun layerEnabled(
        layer: WaypointLayer,
        player: Player,
        interactionContext: com.typewritermc.core.interaction.InteractionContext,
    ): Boolean = when (layer) {
        is WaypointTextLayer -> layer.enabled.get(player, interactionContext)
        is WaypointBlockLayer -> layer.enabled.get(player, interactionContext)
        is WaypointBeaconLayer -> layer.enabled.get(player, interactionContext)
    }

    private fun layerOffset(layer: WaypointLayer): com.typewritermc.core.utils.point.Vector = when (layer) {
        is WaypointTextLayer -> layer.offset
        is WaypointBlockLayer -> layer.offset
        is WaypointBeaconLayer -> com.typewritermc.core.utils.point.Vector.ZERO
    }

    private fun hudLocation(
        player: Player, offset: com.typewritermc.core.utils.point.Vector,
        distance: Double, verticalOffset: Double, occlusionCheck: Boolean = false,
    ): Location {
        val location = player.eyeLocation
        val forward = location.direction.normalize()
        val yawRadians = Math.toRadians(player.location.yaw.toDouble())
        val right = if (forward.clone().crossProduct(org.bukkit.util.Vector(0.0, 1.0, 0.0)).lengthSquared() > 1.0E-6) {
            org.bukkit.util.Vector(0.0, 1.0, 0.0).crossProduct(forward).normalize()
        } else {
            org.bukkit.util.Vector(cos(yawRadians), 0.0, sin(yawRadians))
        }
        val up = forward.clone().crossProduct(right).normalize()
        val relativeOffset = right.multiply(offset.x).add(up.multiply(offset.y + verticalOffset)).add(forward.clone().multiply(offset.z))
        val effectiveDistance = if (occlusionCheck) {
            computeOcclusionDistance(player, location, forward, distance.coerceAtLeast(1.0))
        } else {
            distance.coerceAtLeast(1.0)
        }
        return location.add(forward.clone().multiply(effectiveDistance)).add(relativeOffset).apply {
            yaw = player.location.yaw; pitch = 0f
        }
    }

    private fun computeOcclusionDistance(
        player: Player, eyeLocation: Location, forward: org.bukkit.util.Vector, idealDistance: Double,
    ): Double {
        val world = player.world
        val distances = listOf(idealDistance, (idealDistance * 0.5).coerceAtLeast(1.0), 2.0, 1.0).distinct()
        for (d in distances) {
            val point = eyeLocation.clone().add(forward.clone().multiply(d))
            if (!hasBlockBetween(eyeLocation, point, world)) return d
        }
        return 1.0
    }

    private fun hasBlockBetween(from: Location, to: Location, world: org.bukkit.World): Boolean {
        val vector = to.toVector().subtract(from.toVector())
        val distance = vector.length()
        if (distance < 0.5) return false
        val step = vector.normalize().multiply(0.5)
        var current = from.toVector().clone()
        val steps = (distance / 0.5).roundToInt().coerceAtMost(40)
        for (i in 0..steps) {
            val block = world.getBlockAt(current.blockX, current.blockY, current.blockZ)
            if (block.type.isSolid && !block.isPassable) return true
            if (i < steps) current.add(step)
        }
        return false
    }

    private fun directionFor(playerLocation: Location, target: Location): WaypointDirection {
        val dx = target.x - playerLocation.x
        val dz = target.z - playerLocation.z
        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        val relative = WaypointDirectionMath.wrapDegrees(targetYaw - playerLocation.yaw)
        return WaypointDirectionMath.fromRelativeYaw(relative)
    }

    private fun formatText(
        source: String, player: Player, target: ResolvedWaypointTarget,
        distance: Double, direction: WaypointDirection, icon: String,
    ): String {
        val elevation = target.position.y - player.location.y
        val vertical = when {
            elevation > 1.5 -> "\u2191"; elevation < -1.5 -> "\u2193"; else -> ""
        }
        val worldName = player.world.name
        val heightDiff = if (elevation >= 0) "+${elevation.roundToInt()}" else elevation.roundToInt().toString()
        val etaSeconds = (distance / PLAYER_WALK_SPEED_MS).roundToInt()
        return source
            .replace("{distance}", distance.roundToInt().toString())
            .replace("{distance_m}", distance.roundToInt().toString())
            .replace("{distance_km}", "%.2f".format(java.util.Locale.US, distance / 1_000.0))
            .replace("{elevation}", elevation.roundToInt().toString())
            .replace("{direction}", direction.name)
            .replace("{icon}", icon)
            .replace("{vertical_direction}", vertical)
            .replace("{target}", target.label)
            .replace("{world_name}", worldName)
            .replace("{height_diff}", heightDiff)
            .replace("{eta}", etaSeconds.toString())
    }

    private fun targetTextLocation(
        targetLocation: Location, layer: WaypointTextLayer, verticalOffset: Double,
    ): Location = targetLocation.clone().add(layer.offset.x, layer.offset.y + verticalOffset, layer.offset.z)
}

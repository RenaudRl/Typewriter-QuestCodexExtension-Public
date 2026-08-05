package btcrenaud.questcodex.waypoint

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3f
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.core.interaction.context
import com.typewritermc.core.utils.point.Vector
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
    private val interpolationTicks: Int,
) {
    private val wrapper: WrapperEntity by lazy(LazyThreadSafetyMode.NONE) {
        // A dedicated UUID per marker: several markers of the same type coexist,
        // and a shared UUID makes the client treat the later spawns as duplicates.
        val uuid = UUID.randomUUID()
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
            positionRotationInterpolationDuration = interpolationTicks.coerceIn(0, 20)
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
            positionRotationInterpolationDuration = interpolationTicks.coerceIn(0, 20)
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

/** A beacon in `BOTH` mode needs two entities for the same layer of the same target. */
internal enum class LayerSlot { PRIMARY, HUD_COPY }

internal data class LayerKey(
    val targetKey: String,
    val layerIndex: Int,
    val slot: LayerSlot = LayerSlot.PRIMARY,
)

private class ActiveWaypointLayer(
    val entity: PacketWaypointEntity,
    var lastLocation: Location? = null,
    var lastPayload: Any? = null,
)

private class WaypointPlayerState {
    val pending = AtomicBoolean(false)
    val layers = ConcurrentHashMap<LayerKey, ActiveWaypointLayer>()

    /** Beacon rotation is kept per target so several beacons never share a phase. */
    val beaconAngles = ConcurrentHashMap<String, Float>()
    val startTimeMillis = System.currentTimeMillis()

    /**
     * Timestamp before which nothing is spawned again. Respawning in the same tick
     * as the client reset would send the markers into the state being torn down.
     */
    @Volatile
    var resyncAtMillis = 0L

    /** When the in-flight render was submitted, so a dropped one can be detected. */
    @Volatile
    var pendingSinceMillis = 0L

    /** Previous values of the two things whose change resets the client's entity list. */
    @Volatile
    var wasDead = false

    @Volatile
    var lastWorldId: UUID? = null
}

/** A resolved target with everything the renderer needs for the current tick. */
private class TargetRender(
    val target: ResolvedWaypointTarget,
    val location: Location,
    val distance: Double,
    val direction: Vec3,
    val stackIndex: Int,
    var declutterOffset: Double = 0.0,
)

class QuestCodexWaypointDisplay(
    private val entry: QuestCodexWaypointEntry,
) : AudienceDisplay(), TickableDisplay {
    private val states = ConcurrentHashMap<UUID, WaypointPlayerState>()
    private var tickCounter = 0
    private val resolver = WaypointTargetResolver()

    override fun onPlayerAdd(player: Player) {
        states.computeIfAbsent(player.uniqueId) { WaypointPlayerState() }
    }

    /**
     * Notices that the client threw its entity list away, and forgets the markers so
     * the next render spawns fresh ones instead of teleporting ids the client no
     * longer knows. A respawn and a dimension change both do that.
     *
     * This is polled from the render loop rather than driven by a Bukkit listener on
     * purpose. An `AudienceDisplay` is a `Listener` registered by `initialize()`, so a
     * listener looks like the natural choice — but `PlayerRespawnEvent` was verified
     * not to reach a display registered that way, while the render loop demonstrably
     * keeps running for the player. Polling two fields costs nothing next to a render
     * and needs no event registration to be correct.
     */
    private fun detectSessionReset(player: Player, state: WaypointPlayerState) {
        val worldId = player.world.uid
        val dead = player.isDead
        val respawned = state.wasDead && !dead
        // Null on the first render: joining is not a reset, the state is already empty.
        val changedWorld = state.lastWorldId != null && state.lastWorldId != worldId
        state.wasDead = dead
        state.lastWorldId = worldId
        if (!respawned && !changedWorld) return

        clearLayers(state)
        WaypointDeclutterRegistry.release(player.uniqueId, entry.id)
        val now = System.currentTimeMillis()
        state.resyncAtMillis = now + SESSION_RESYNC_DELAY_MILLIS
    }

    override fun onPlayerRemove(player: Player) {
        val state = states.remove(player.uniqueId) ?: return
        clearLayers(state)
        WaypointDeclutterRegistry.release(player.uniqueId, entry.id)
    }

    override fun tick() {
        plugin.server.onlinePlayers.forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            val refreshTicks = entry.refreshTicks.coerceIn(1, 20)
            if (tickCounter % refreshTicks != 0) return@forEach
            if (!state.pending.compareAndSet(false, true)) {
                releaseStalledRender(player, state)
                return@forEach
            }

            state.pendingSinceMillis = System.currentTimeMillis()
            // Nothing else will clear the flag if the render never runs.
            if (!scheduleRender(player, state)) state.pending.set(false)
        }
        tickCounter = (tickCounter + 1) % 20
    }

    /**
     * Runs a render on the player's thread. Returns `false` when the render could
     * not be scheduled at all, so the caller can release the pending flag.
     *
     * The entity scheduler refuses work while the player's server entity is removed,
     * which is precisely what a death screen is, and it signals that by returning
     * `null` rather than by throwing. Left unhandled, the pending flag stayed armed
     * for good and the entry never rendered again until the player reconnected. The
     * region scheduler is not tied to an entity, so it takes over during that window.
     */
    private fun scheduleRender(player: Player, state: WaypointPlayerState): Boolean {
        val render = Runnable {
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
        }

        // The third argument is the retired callback: without it, a render accepted
        // just before the player's entity is removed is dropped without a trace.
        val retired = Runnable { state.pending.set(false) }
        if (player.scheduler.run(plugin, { _ -> render.run() }, retired) != null) return true
        return runCatching {
            plugin.server.regionScheduler.run(plugin, player.location) { render.run() }
        }.isSuccess
    }

    override fun dispose() {
        states.forEach { (playerId, state) ->
            clearLayers(state)
            WaypointDeclutterRegistry.release(playerId, entry.id)
        }
        states.clear()
        super.dispose()
    }

    /**
     * Frees a render that was accepted by a scheduler and then never ran.
     *
     * Without this the entry goes dark permanently and only a reconnect brings it
     * back, because the in-flight flag is cleared exclusively by the render itself.
     * The retired callback covers the known cause; this covers every other one.
     */
    private fun releaseStalledRender(player: Player, state: WaypointPlayerState) {
        if (System.currentTimeMillis() - state.pendingSinceMillis < RENDER_TIMEOUT_MILLIS) return
        state.pending.set(false)
        plugin.logger.warning(
            "[QuestCodex] '${entry.id}' render for ${player.name} was dropped by its scheduler; recovering"
        )
    }

    private fun renderPlayer(player: Player, state: WaypointPlayerState) {
        if (!player.isOnline) {
            stopRendering(player, state)
            return
        }
        detectSessionReset(player, state)
        // Let the client settle after a respawn or a dimension change.
        if (System.currentTimeMillis() < state.resyncAtMillis) return

        val targets = resolver.resolveAll(entry.target, player, entry.maxTargets)
        if (targets.isEmpty()) {
            stopRendering(player, state)
            return
        }

        val playerLocation = player.location
        val eyeLocation = player.eyeLocation
        val eye = eyeLocation.toVec3()

        val renders = targets.mapNotNull { target ->
            // Objective positions may carry capture yaw/pitch; displays must stay level.
            val location = target.position.toBukkitLocation().apply { yaw = 0f; pitch = 0f }
            if (location.world != playerLocation.world) return@mapNotNull null
            val distance = playerLocation.distance(location)
            if (!distance.isFinite()) return@mapNotNull null
            Triple(target, location, distance)
        }.sortedBy { it.third }
            .mapIndexed { index, (target, location, distance) ->
                TargetRender(
                    target = target,
                    location = location,
                    distance = distance,
                    direction = location.toVec3().minus(eye).normalized(),
                    stackIndex = index,
                )
            }

        if (renders.isEmpty()) {
            stopRendering(player, state)
            return
        }

        val offsets = WaypointDeclutterRegistry.offsets(
            player.uniqueId,
            entry.id,
            renders.map { it.direction },
            entry.declutterAngle,
            entry.declutterSpacing,
        )
        renders.forEachIndexed { index, render -> render.declutterOffset = offsets[index] }

        val elapsedSeconds = (System.currentTimeMillis() - state.startTimeMillis) / 1_000.0
        val interactionContext = context()
        val desired = HashSet<LayerKey>()

        renders.forEach { render ->
            renderTarget(player, state, render, eyeLocation, elapsedSeconds, interactionContext, desired)
        }

        state.layers.entries.removeIf { (key, active) ->
            if (key in desired) false else { active.entity.dispose(); true }
        }
        val liveKeys = renders.mapTo(HashSet()) { it.target.key }
        state.beaconAngles.keys.removeIf { it !in liveKeys }
    }

    private fun renderTarget(
        player: Player,
        state: WaypointPlayerState,
        render: TargetRender,
        eyeLocation: Location,
        elapsedSeconds: Double,
        interactionContext: InteractionContext,
        desired: MutableSet<LayerKey>,
    ) {
        val distance = render.distance
        val direction = directionFor(player.location, render.location)

        entry.layers.forEachIndexed { index, layer ->
            if (!layer.isEnabled(player, interactionContext)) return@forEachIndexed

            if (layer is WaypointBeaconLayer) {
                renderBeacon(player, state, render, layer, index, interactionContext, desired)
                return@forEachIndexed
            }

            val placement = resolvePlacement(player, layer, render, eyeLocation) ?: return@forEachIndexed
            val key = LayerKey(render.target.key, index)
            desired += key

            val active = state.layers[key] ?: createLayer(player, layer).also { state.layers[key] = it }
            val location = applyBreathing(placement.location, layer, elapsedSeconds, animate = placement.anchored)
            val previous = active.lastLocation
            if (previous == null || previous.world != location.world ||
                previous.distanceSquared(location) > LOCATION_EPSILON_SQUARED
            ) {
                active.entity.teleport(location)
            }
            applyLayer(active, layer, player, render.target, distance, direction, placement.scale, interactionContext)
            active.lastLocation = location
        }
    }

    /** Resolved placement of a non-beacon layer, or `null` when it must be hidden this tick. */
    private class LayerPlacementResult(
        val location: Location,
        val scale: Float,
        val anchored: Boolean,
    )

    private fun resolvePlacement(
        player: Player,
        layer: WaypointLayer,
        render: TargetRender,
        eyeLocation: Location,
    ): LayerPlacementResult? {
        val mode = resolvedMode(layer)
        val hideWithin = entry.hideWithinDistance.coerceAtLeast(0.0)
        if (mode != WaypointDisplayMode.HUD_LOCKED && hideWithin > 0.0 && render.distance <= hideWithin) {
            return null
        }

        return when (mode) {
            WaypointDisplayMode.HUD_LOCKED -> hudLockedPlacement(player, layer, render, eyeLocation)
            WaypointDisplayMode.TARGET_ANCHORED -> targetAnchoredPlacement(layer, render)
            WaypointDisplayMode.WORLD_DIRECTIONAL -> projectedPlacement(
                layer, render, eyeLocation, entry.projectionRadius,
            )
            WaypointDisplayMode.ADAPTIVE -> projectedPlacement(
                layer, render, eyeLocation,
                adaptiveRadius(
                    distance = render.distance,
                    nearDistance = entry.nearTargetDistance.coerceAtLeast(0.0),
                    band = entry.adaptiveTransitionBand.coerceAtLeast(0.0),
                    projectionRadius = entry.projectionRadius.coerceAtLeast(1.0),
                ),
            )
        }
    }

    /** Historical behaviour: pinned in front of the eyes, gated by the visibility cone. */
    private fun hudLockedPlacement(
        player: Player,
        layer: WaypointLayer,
        render: TargetRender,
        eyeLocation: Location,
    ): LayerPlacementResult? {
        val distance = render.distance
        val nearTarget = distance <= entry.nearTargetDistance.coerceAtLeast(0.0)
        val inView = isWithinHorizontalViewCone(
            playerYaw = eyeLocation.yaw,
            playerX = eyeLocation.x,
            playerZ = eyeLocation.z,
            targetX = render.location.x,
            targetZ = render.location.z,
            visibilityAngle = entry.hudVisibilityAngle,
        )
        if (!nearTarget && !inView) return null
        if (!nearTarget && distance <= entry.hideWithinDistance.coerceAtLeast(0.0)) return null

        if (layer is WaypointTextLayer && nearTarget) {
            return LayerPlacementResult(
                targetTextLocation(render.location, layer.offset, entry.nearTargetVerticalOffset),
                layer.scale,
                anchored = true,
            )
        }

        return when (layer.placementOrNull()) {
            WaypointLayerPlacement.TARGET -> {
                if (distance > entry.targetViewDistance) return null
                val offset = layer.offsetOrZero()
                LayerPlacementResult(
                    render.location.clone().add(offset.x, offset.y, offset.z),
                    layer.scaleOrOne(),
                    anchored = true,
                )
            }

            else -> LayerPlacementResult(
                // Several targets would otherwise land on the exact same HUD point.
                hudLocation(player, render, layer.offsetOrZero()),
                layer.scaleOrOne(),
                anchored = false,
            )
        }
    }

    private fun targetAnchoredPlacement(layer: WaypointLayer, render: TargetRender): LayerPlacementResult? {
        if (render.distance > entry.targetViewDistance) return null
        return LayerPlacementResult(
            anchorPoint(layer, render).toBukkitLocation(render.location),
            layer.scaleOrOne(),
            anchored = true,
        )
    }

    private fun projectedPlacement(
        layer: WaypointLayer,
        render: TargetRender,
        eyeLocation: Location,
        radius: Double,
    ): LayerPlacementResult {
        val marker = projectOntoSphere(
            eye = eyeLocation.toVec3(),
            target = anchorPoint(layer, render),
            radius = radius,
            referenceRadius = entry.projectionRadius.coerceAtLeast(1.0),
            constantApparentSize = entry.constantApparentSize,
        )
        val location = marker.position.toBukkitLocation(render.location).apply {
            yaw = eyeLocation.yaw
            pitch = 0f
        }
        return LayerPlacementResult(
            location,
            (layer.scaleOrOne() * marker.scaleFactor).toFloat(),
            anchored = radius >= render.distance - 1.0E-6,
        )
    }

    /**
     * World point a layer aims at, including its offset, the text vertical
     * offset and the anti-overlap spacing.
     */
    private fun anchorPoint(layer: WaypointLayer, render: TargetRender): Vec3 {
        val offset = layer.offsetOrZero()
        val vertical = if (layer is WaypointTextLayer) entry.nearTargetVerticalOffset else 0.0
        return Vec3(
            render.location.x + offset.x,
            render.location.y + offset.y + vertical + render.declutterOffset,
            render.location.z + offset.z,
        )
    }

    private fun renderBeacon(
        player: Player,
        state: WaypointPlayerState,
        render: TargetRender,
        layer: WaypointBeaconLayer,
        index: Int,
        interactionContext: InteractionContext,
        desired: MutableSet<LayerKey>,
    ) {
        val viewDistance = layer.viewDistance.get(player, interactionContext).coerceAtLeast(1.0)
        val wantsTarget = layer.mode != BeaconMode.HUD
        val wantsHud = layer.mode != BeaconMode.TARGET
        if (wantsTarget && render.distance > viewDistance && !wantsHud) return

        val angle = state.beaconAngles.compute(render.target.key) { _, current ->
            ((current ?: 0f) + layer.rotationSpeed.get(player, interactionContext)) % 360f
        } ?: 0f

        if (wantsTarget && render.distance <= viewDistance) {
            val location = render.location.clone()
                .add(0.0, layer.offsetY.get(player, interactionContext) + render.declutterOffset, 0.0)
            spawnBeaconAt(player, state, LayerKey(render.target.key, index, LayerSlot.PRIMARY), location, layer, angle, interactionContext, desired)
        }
        if (wantsHud) {
            val location = hudLocation(player, render, Vector.ZERO)
            spawnBeaconAt(player, state, LayerKey(render.target.key, index, LayerSlot.HUD_COPY), location, layer, angle, interactionContext, desired)
        }
    }

    private fun spawnBeaconAt(
        player: Player,
        state: WaypointPlayerState,
        key: LayerKey,
        location: Location,
        layer: WaypointBeaconLayer,
        angle: Float,
        interactionContext: InteractionContext,
        desired: MutableSet<LayerKey>,
    ) {
        desired += key
        val active = state.layers[key] ?: ActiveWaypointLayer(
            PacketWaypointEntity(player, EntityTypes.BLOCK_DISPLAY, entry.interpolationTicks)
        ).also { state.layers[key] = it }
        val previous = active.lastLocation
        if (previous == null || previous.world != location.world ||
            previous.distanceSquared(location) > LOCATION_EPSILON_SQUARED
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
        return ActiveWaypointLayer(PacketWaypointEntity(player, type, entry.interpolationTicks))
    }

    private fun applyLayer(
        active: ActiveWaypointLayer,
        layer: WaypointLayer,
        player: Player,
        target: ResolvedWaypointTarget,
        distance: Double,
        direction: WaypointDirection,
        scale: Float,
        interactionContext: InteractionContext,
    ) {
        // Projected scales drift continuously; quantize so metadata is not resent every tick.
        val quantizedScale = round(scale * 100f) / 100f
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
                val payload = listOf(
                    rendered, quantizedScale, layer.lineWidth, layer.shadow, layer.seeThrough, layer.backgroundOpacity,
                )
                if (payload != active.lastPayload) {
                    active.entity.text(
                        rendered, quantizedScale, layer.lineWidth, layer.shadow, layer.seeThrough, layer.backgroundOpacity,
                    )
                    active.lastPayload = payload
                }
            }

            is WaypointBlockLayer -> {
                val material = layer.material.get(player, interactionContext)
                val payload = listOf(material, quantizedScale)
                if (payload != active.lastPayload) {
                    active.entity.block(
                        material, quantizedScale, rotation = 0f, blockLight = 15, skyLight = 15, centerVertically = true,
                    )
                    active.lastPayload = payload
                }
            }

            is WaypointBeaconLayer -> Unit // Beacons are rendered by renderBeacon.
        }
    }

    private fun applyBeaconLayer(
        active: ActiveWaypointLayer,
        layer: WaypointBeaconLayer,
        player: Player,
        angle: Float,
        interactionContext: InteractionContext,
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
        state.beaconAngles.clear()
    }

    /**
     * Despawns everything shown to [player] and hands the vertical levels this
     * entry was holding back to the other waypoint entries.
     */
    private fun stopRendering(player: Player, state: WaypointPlayerState) {
        clearLayers(state)
        WaypointDeclutterRegistry.release(player.uniqueId, entry.id)
    }

    private fun resolvedMode(layer: WaypointLayer): WaypointDisplayMode = when (val override = layer.modeOverride()) {
        WaypointLayerMode.INHERIT -> entry.displayMode
        else -> WaypointDisplayMode.valueOf(override.name)
    }

    private fun hudLocation(player: Player, render: TargetRender, offset: Vector): Location {
        // Stack HUD markers so several targets never collapse onto the same point.
        val stacked = Vector(
            offset.x,
            offset.y + render.stackIndex * entry.declutterSpacing,
            offset.z,
        )
        return hudLocation(
            player,
            stacked,
            entry.hudForwardDistance,
            entry.hudVerticalOffset,
            occlusionCheck = tickCounter % OCCLUSION_CHECK_TICKS == 0,
        )
    }

    private fun hudLocation(
        player: Player, offset: Vector,
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
        val current = from.toVector().clone()
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
        if ('{' !in source) return source
        val elevation = target.position.y - player.location.y
        val vertical = when {
            elevation > 1.5 -> "↑"; elevation < -1.5 -> "↓"; else -> ""
        }
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
            .replace("{world_name}", player.world.name)
            .replace("{height_diff}", heightDiff)
            .replace("{eta}", etaSeconds.toString())
    }

    private fun targetTextLocation(
        targetLocation: Location, offset: Vector, verticalOffset: Double,
    ): Location = targetLocation.clone().add(offset.x, offset.y + verticalOffset, offset.z)
}

private fun Location.toVec3() = Vec3(x, y, z)

private fun Vec3.toBukkitLocation(reference: Location): Location = Location(reference.world, x, y, z)

private fun WaypointLayer.isEnabled(player: Player, context: InteractionContext): Boolean = when (this) {
    is WaypointTextLayer -> enabled.get(player, context)
    is WaypointBlockLayer -> enabled.get(player, context)
    is WaypointBeaconLayer -> enabled.get(player, context)
}

private fun WaypointLayer.offsetOrZero(): Vector = when (this) {
    is WaypointTextLayer -> offset
    is WaypointBlockLayer -> offset
    is WaypointBeaconLayer -> Vector.ZERO
}

private fun WaypointLayer.scaleOrOne(): Float = when (this) {
    is WaypointTextLayer -> scale
    is WaypointBlockLayer -> scale
    is WaypointBeaconLayer -> 1.0f
}

private fun WaypointLayer.placementOrNull(): WaypointLayerPlacement? = when (this) {
    is WaypointTextLayer -> placement
    is WaypointBlockLayer -> placement
    is WaypointBeaconLayer -> null
}

private fun WaypointLayer.modeOverride(): WaypointLayerMode = when (this) {
    is WaypointTextLayer -> mode
    is WaypointBlockLayer -> mode
    is WaypointBeaconLayer -> WaypointLayerMode.INHERIT
}

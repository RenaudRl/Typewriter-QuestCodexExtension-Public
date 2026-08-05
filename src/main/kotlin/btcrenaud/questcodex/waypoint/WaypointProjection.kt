package btcrenaud.questcodex.waypoint

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure placement maths for waypoint markers.
 *
 * Everything here is deliberately free of Bukkit types so the geometry can be
 * unit tested without a server.
 */

internal data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    fun times(factor: Double) = Vec3(x * factor, y * factor, z * factor)
    fun length() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val length = length()
        return if (length <= 1.0E-9) ZERO else Vec3(x / length, y / length, z / length)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

/**
 * Marker placement resolved for a single target.
 *
 * [position] is colinear with the eye/target axis, so aiming at the marker aims
 * at the real destination. [scaleFactor] multiplies the layer scale to keep a
 * constant apparent size regardless of how far the marker was pulled in.
 */
internal data class ProjectedMarker(
    val position: Vec3,
    val scaleFactor: Double,
)

/**
 * Places a marker on the sphere of radius [radius] centred on [eye], along the
 * direction of [target]. Markers closer than the radius stay on the target.
 */
internal fun projectOntoSphere(
    eye: Vec3,
    target: Vec3,
    radius: Double,
    referenceRadius: Double,
    constantApparentSize: Boolean,
): ProjectedMarker {
    val delta = target.minus(eye)
    val distance = delta.length()
    if (distance <= 1.0E-6) {
        return ProjectedMarker(target, if (constantApparentSize) MIN_SCALE_FACTOR else 1.0)
    }
    val effectiveRadius = min(radius, distance).coerceAtLeast(0.0)
    val position = eye.plus(delta.normalized().times(effectiveRadius))
    val reference = referenceRadius.coerceAtLeast(1.0E-3)
    val scaleFactor = if (constantApparentSize) {
        (effectiveRadius / reference).coerceAtLeast(MIN_SCALE_FACTOR)
    } else {
        1.0
    }
    return ProjectedMarker(position, scaleFactor)
}

/**
 * Radius used by `ADAPTIVE`: the marker sits on the target while the player is
 * within [nearDistance], then eases onto the projection sphere over [band]
 * blocks. The curve is continuous at both ends, so the marker never jumps.
 */
internal fun adaptiveRadius(
    distance: Double,
    nearDistance: Double,
    band: Double,
    projectionRadius: Double,
): Double {
    if (distance <= nearDistance) return distance
    val clamped = min(distance, projectionRadius)
    if (band <= 0.0) return clamped
    val t = ((distance - nearDistance) / band).coerceIn(0.0, 1.0)
    val smooth = t * t * (3.0 - 2.0 * t)
    return distance + (clamped - distance) * smooth
}

/** A marker direction and the vertical offset it occupies. */
internal data class DeclutterClaim(val direction: Vec3, val offset: Double)

/**
 * Vertical offsets keeping projected markers readable when several targets sit
 * on nearly the same line of sight.
 *
 * [directions] must be ordered by ascending distance: the closest marker keeps
 * its exact position and further ones are pushed up in [spacing] steps.
 *
 * [occupied] carries the offsets already taken by markers this call does not own —
 * the waypoints of other entries, supplied by [WaypointDeclutterRegistry]. Passing
 * an empty list declutters a single entry against itself only.
 */
internal fun declutterOffsets(
    directions: List<Vec3>,
    minAngleDegrees: Double,
    spacing: Double,
    occupied: List<DeclutterClaim> = emptyList(),
): List<Double> {
    if (minAngleDegrees <= 0.0 || spacing == 0.0 || directions.isEmpty()) {
        return List(directions.size) { 0.0 }
    }
    if (directions.size < 2 && occupied.isEmpty()) {
        return List(directions.size) { 0.0 }
    }
    val minAngle = Math.toRadians(minAngleDegrees)
    val halfStep = abs(spacing) * 0.5
    val taken = ArrayList(occupied)
    val resolved = ArrayList<Double>(directions.size)

    directions.map { it.normalized() }.forEach { unit ->
        var level = 0
        while (level < MAX_DECLUTTER_LEVELS) {
            val candidate = level * spacing
            val collides = taken.any { claim ->
                abs(claim.offset - candidate) < halfStep &&
                    angleBetweenVectors(unit, claim.direction) < minAngle
            }
            if (!collides) break
            level++
        }
        val offset = level * spacing
        taken += DeclutterClaim(unit, offset)
        resolved += offset
    }
    return resolved
}

internal fun angleBetweenVectors(a: Vec3, b: Vec3): Double {
    val dot = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0)
    return acos(dot)
}

/** Guard against a pathological configuration pushing markers out of sight. */
private const val MAX_DECLUTTER_LEVELS = 32

private const val MIN_SCALE_FACTOR = 0.02

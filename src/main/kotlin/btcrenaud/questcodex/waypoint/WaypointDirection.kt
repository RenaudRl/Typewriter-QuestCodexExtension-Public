package btcrenaud.questcodex.waypoint

enum class WaypointDirection {
    NORTH,
    NORTH_EAST,
    EAST,
    SOUTH_EAST,
    SOUTH,
    SOUTH_WEST,
    WEST,
    NORTH_WEST,
}

object WaypointDirectionMath {
    fun fromRelativeYaw(relativeYaw: Double): WaypointDirection {
        val yaw = wrapDegrees(relativeYaw)
        return when {
            yaw in -22.5..22.5 -> WaypointDirection.NORTH
            yaw in 22.5..67.5 -> WaypointDirection.NORTH_EAST
            yaw in 67.5..112.5 -> WaypointDirection.EAST
            yaw in 112.5..157.5 -> WaypointDirection.SOUTH_EAST
            yaw > 157.5 || yaw <= -157.5 -> WaypointDirection.SOUTH
            yaw in -157.5..-112.5 -> WaypointDirection.SOUTH_WEST
            yaw in -112.5..-67.5 -> WaypointDirection.WEST
            else -> WaypointDirection.NORTH_WEST
        }
    }

    fun wrapDegrees(value: Double): Double {
        var result = value % 360.0
        if (result > 180.0) result -= 360.0
        if (result <= -180.0) result += 360.0
        return result
    }
}

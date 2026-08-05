package btcrenaud.questcodex.waypoint

/**
 * How long to wait after a client reset before spawning markers again.
 *
 * Respawning in the same tick as the reset sends the markers into the state the
 * client is tearing down.
 */
internal const val SESSION_RESYNC_DELAY_MILLIS = 500L

/**
 * How long a render may stay in flight before the entry assumes it will never run.
 *
 * A render that is accepted by a scheduler and then dropped — the entity being
 * retired mid-death is the case that bit us — never reaches its `finally`, so the
 * in-flight flag would stay armed for good and the entry would go dark until the
 * player reconnected. Generous next to a render, short next to a player noticing.
 */
internal const val RENDER_TIMEOUT_MILLIS = 2_000L

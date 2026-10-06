package ru.rkhamatyarov.service.mvi

import kotlin.math.abs

/** Sweeps each straight portion of travel, preserving wall/paddle contact order. */
internal object BoundaryPhysics {
    fun resolve(
        state: MviGameState,
        advanced: MviPuck,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        var start = advanced.copy(x = state.puck.x, y = state.puck.y)
        var dx = advanced.x - start.x
        var dy = advanced.y - start.y
        var ledger = state.touchLedger
        repeat(MAX_WALL_CONTACTS) {
            val wallY = if (dy < 0.0) start.radius else state.canvasHeight - start.radius
            val fraction =
                when {
                    abs(dy) < EPSILON -> Double.POSITIVE_INFINITY
                    if (dy < 0.0) start.y <= wallY else start.y >= wallY -> 0.0
                    else -> (wallY - start.y) / dy
                }
            val hitsWall = fraction in 0.0..1.0
            val travel = if (hitsWall) fraction else 1.0
            val end = start.copy(x = start.x + dx * travel, y = start.y + dy * travel)
            val frame = TickFrame(end, ledger)
            val resolved = PaddlePhysics.resolve(frame, state.copy(puck = start), effectiveSpeed, elapsedNs)
            if (resolved !== frame) return resolved
            if (!hitsWall) return frame

            val bounced = PuckPhysics.resolveWalls(frame, state.canvasHeight, elapsedNs, effectiveSpeed)
            start = bounced.puck
            ledger = bounced.touchLedger
            dx *= 1.0 - travel
            dy *= -(1.0 - travel)
            if (abs(dx) < EPSILON && abs(dy) < EPSILON) return bounced
        }
        return TickFrame(start, ledger)
    }

    private const val MAX_WALL_CONTACTS = 64
    private const val EPSILON = 1e-9
}

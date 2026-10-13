package ru.rkhamatyarov.service.mvi

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test oracle using the height at face arrival, independent of the production collision sweep. */
internal object PaddleInterception {
    /** Approaches an edge while horizontal motion is away from the goal, not away from the paddle body. */
    fun edgeApproach(
        side: PaddleSide,
        fromAbove: Boolean,
        verticalSpeed: Double = 2000.0,
        horizontalSpeed: Double = 10.0,
    ): MviGameState {
        val direction = if (fromAbove) 1.0 else -1.0
        val edge = if (fromAbove) 240.0 else 360.0
        return MviGameState(
            puck =
                MviPuck(
                    x = if (side == PaddleSide.A) 15.0 else 785.0,
                    y = edge - direction * verticalSpeed * 0.008,
                    vx = if (side == PaddleSide.A) horizontalSpeed else -horizontalSpeed,
                    vy = direction * verticalSpeed,
                ),
        )
    }

    fun verifyEdge(
        before: MviGameState,
        after: MviGameState,
        side: PaddleSide,
        fromAbove: Boolean,
        elapsedNs: Long,
    ) {
        val context = "side=$side above=$fromAbove before=${before.puck} after=${after.puck}"
        assertEquals(before.score, after.score, context)
        assertTrue(after.puck.vy * before.puck.vy < 0.0, "Puck crossed paddle edge without rebounding: $context")
        assertEquals(if (fromAbove) 240.0 else 360.0, after.puck.y, 1e-6, context)
        assertTrue(
            after.touchLedger.entries.any {
                it.source == TouchSource.PADDLE && it.ownerSide == side && it.elapsedNs == elapsedNs
            },
            "Missing edge contact: $context",
        )
    }

    fun verify(
        before: MviGameState,
        after: MviGameState,
        tick: GameAction.Tick,
    ): PaddleSide? {
        if (before.paused || before.ghostMode) return null
        val contact = contact(before, tick) ?: return null
        val paddleY = if (contact.side == PaddleSide.A) before.paddle1Y else before.paddle2Y
        val radius = before.puck.radius
        if (contact.y !in (paddleY - radius)..(paddleY + before.paddleHeight + radius)) return null

        val context = "contact=$contact tick=$tick before=$before after=${after.puck}"
        assertEquals(before.score, after.score, "Goal despite interception: $context")
        assertTrue(
            after.touchLedger.entries.any {
                it.source == TouchSource.PADDLE && it.ownerSide == contact.side && it.elapsedNs == tick.elapsedNs
            },
            "Missing current-tick paddle contact: $context",
        )
        val face =
            if (contact.side == PaddleSide.A) {
                PADDLE_WIDTH + radius
            } else {
                before.canvasWidth - PADDLE_WIDTH - radius
            }
        assertEquals(face, after.puck.x, 1e-6, "Puck was not stopped at the paddle face: $context")
        val movingAway = if (contact.side == PaddleSide.A) after.puck.vx > 0.0 else after.puck.vx < 0.0
        assertTrue(movingAway, "Puck kept moving through the paddle after contact: $context")
        return contact.side
    }

    fun contact(
        state: MviGameState,
        tick: GameAction.Tick,
    ): Contact? {
        val puck = state.puck
        if (puck.vx == 0.0 || tick.deltaSeconds <= 0.0) return null
        val side = if (puck.vx < 0.0) PaddleSide.A else PaddleSide.B
        val face =
            if (side == PaddleSide.A) {
                PADDLE_WIDTH + puck.radius
            } else {
                state.canvasWidth - PADDLE_WIDTH - puck.radius
            }
        // Reuse only free-flight integration so spin is included, never collision or scoring logic.
        val advanced = PuckPhysics.advance(puck, tick.deltaSeconds, effectiveSpeed(state, tick))
        val fraction = (face - puck.x) / (advanced.x - puck.x)
        if (!fraction.isFinite() || fraction !in 0.0..1.0) return null

        val span = state.canvasHeight - 2 * puck.radius
        val unfolded = (puck.y + (advanced.y - puck.y) * fraction - puck.radius).mod(2 * span)
        val contactY = puck.radius + if (unfolded <= span) unfolded else 2 * span - unfolded
        return Contact(side, contactY)
    }

    private fun effectiveSpeed(
        state: MviGameState,
        tick: GameAction.Tick,
    ): Double {
        val config = state.speedConfig
        val progressive =
            (
                config.baseMultiplier + state.elapsedSeconds / 60 * config.timeAccelerationRate +
                    state.lines.size * config.levelAccelerationPerLine
            ).coerceAtMost(config.maxMultiplier)
        return state.speedMultiplier * progressive * tick.turboSpeedMultiplier
    }

    data class Contact(
        val side: PaddleSide,
        val y: Double,
    )

    private const val PADDLE_WIDTH = 20.0
}

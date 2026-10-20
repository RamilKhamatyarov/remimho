package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rally speed exercised through the real reducer, so the wiring is covered, not just the formula. */
class RallySpeedTest {
    @Test
    fun `face return speeds the puck up`() {
        val next = reduce(incomingAtPaddleA(), GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(500.0 * 1.06, next.puck.rawSpeed(), TOLERANCE)
        assertTrue(next.puck.vx > 0.0)
    }

    @Test
    fun `a swinging paddle smashes harder than a still one`() {
        val swung = reduce(incomingAtPaddleA(), GameAction.MovePaddle(330.0, PaddleSide.A))

        val next = reduce(swung, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        val travel = 30.0
        assertEquals(500.0 * 1.06 * (1.0 + 0.45 * travel / 80.0), next.puck.rawSpeed(), TOLERANCE)
    }

    @Test
    fun `an edge glance is not a stroke and keeps its speed`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 100.0, vx = -1_000.0, vy = 1_647.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertTrue(next.puck.vy < 0.0, "precondition: the puck rebounded off the paddle's top edge")
        assertEquals(hypot(1_000.0, 1_647.0), next.puck.rawSpeed(), TOLERANCE)
    }

    @Test
    fun `rally speed does not survive a goal`() {
        val missed = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = -3_000.0, vy = 0.0), paddle1Y = 250.0)

        val next = reduce(missed, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(1, next.score.playerB, "precondition: the puck genuinely missed")
        assertTrue(next.puck.rawSpeed() < 500.0, "the serve restarts at serve speed, not rally speed")
    }

    @Test
    fun `the effective cap bounds how far the puck travels in a tick`() {
        val state = MviGameState(puck = MviPuck(x = 400.0, y = 300.0, vx = 3_200.0, vy = 0.0), speedMultiplier = 1.5)

        val next =
            reduce(state, GameAction.Tick(deltaSeconds = 0.016, elapsedNs = 16_000_000L, turboSpeedMultiplier = 2.0))

        assertEquals(
            400.0 + 4_800.0 * 0.016,
            next.puck.x,
            TOLERANCE,
            "3200 raw * 1.5 * 2 turbo is clamped to 4800 px/s",
        )
    }

    @Test
    fun `a one-timer never brakes a puck a rally drove past its cap`() {
        val fast = MviPuck(vx = 1_500.0, vy = 0.0)

        assertEquals(
            1_500.0,
            OneTimerMechanic.apply(fast, multiplier = 2.0, maximumRawSpeed = 800.0).rawSpeed(),
            TOLERANCE,
        )
    }

    @Test
    fun `a one-timer still boosts a puck below its cap`() {
        val slow = MviPuck(vx = 300.0, vy = 0.0)

        assertEquals(
            600.0,
            OneTimerMechanic.apply(slow, multiplier = 2.0, maximumRawSpeed = 800.0).rawSpeed(),
            TOLERANCE,
        )
    }

    @Test
    fun `swept collision still catches a puck at the effective ceiling`() {
        // 4800 px/s for 0.1 s crosses the whole 800 px field in a single tick.
        val state = MviGameState(puck = MviPuck(x = 400.0, y = 300.0, vx = -3_200.0, vy = 0.0), speedMultiplier = 1.5)

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(MviScore(), next.score, "the paddle at the puck's height must save it")
        assertTrue(next.puck.vx > 0.0)
    }

    private fun incomingAtPaddleA(): MviGameState =
        MviGameState(
            puck = MviPuck(x = 100.0, y = 300.0, vx = -500.0, vy = 0.0),
            paddle1Y = 250.0,
        )

    private fun MviPuck.rawSpeed(): Double = hypot(vx, vy)

    private companion object {
        const val TOLERANCE = 1e-6
    }
}

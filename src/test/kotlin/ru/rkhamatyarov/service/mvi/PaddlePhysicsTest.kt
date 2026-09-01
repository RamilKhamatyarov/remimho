package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddlePhysicsTest {
    @Test
    fun `fast puck cannot tunnel through left paddle`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 300.0, vx = -1_000.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(30.0, next.puck.x, 0.0001)
        assertTrue(next.puck.vx > 0.0)
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `fast puck cannot tunnel through right paddle`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 700.0, y = 300.0, vx = 1_000.0, vy = 0.0),
                paddle2Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(770.0, next.puck.x, 0.0001)
        assertTrue(next.puck.vx < 0.0)
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `puck tangent to paddle edge at crossing is caught`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 170.0, vx = -1_000.0, vy = 1_000.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.12, elapsedNs = 120_000_000L))

        assertEquals(30.0, next.puck.x, 0.0001)
        assertEquals(240.0, next.puck.y, 0.0001)
        assertTrue(next.puck.vx > 0.0)
    }

    @Test
    fun `puck outside paddle at crossing is not caught`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 160.0, vx = -1_000.0, vy = 1_000.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.12, elapsedNs = 120_000_000L))

        assertEquals(1, next.score.playerB)
        assertEquals(state.canvasWidth / 2.0, next.puck.x, 0.0001)
    }
}

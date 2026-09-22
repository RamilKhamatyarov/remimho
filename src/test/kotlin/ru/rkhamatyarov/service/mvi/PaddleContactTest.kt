package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddleContactTest {
    @Test
    fun `fast puck aimed at the centre of a stationary paddle is deflected`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 400.0, y = 300.0, vx = -5_000.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(PADDLE_A_FACE_X, next.puck.x, TOLERANCE)
        assertEquals(300.0, next.puck.y, TOLERANCE)
        assertTrue(next.puck.vx > 0.0, "puck must rebound away from the left paddle")
        assertEquals(MviScore(), next.score, "a deflected puck must never concede")
    }

    @Test
    fun `puck resting inside the paddle with zero velocity is ejected at a playable speed`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = 0.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(PADDLE_A_FACE_X, next.puck.x, TOLERANCE)
        assertTrue(next.puck.vx >= MIN_PLAYABLE_SPEED, "a resting puck must leave the face, not crawl")
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `puck already behind the paddle face is still deflected`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = -1_000.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(PADDLE_A_FACE_X, next.puck.x, TOLERANCE)
        assertTrue(next.puck.vx > 0.0)
        assertEquals(MviScore(), next.score, "the escape window behind the face must not concede")
    }

    @Test
    fun `puck crossing the paddle face in one tick contacts at the swept point`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 230.0, y = 200.0, vx = -4_000.0, vy = 1_000.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(PADDLE_A_FACE_X, next.puck.x, TOLERANCE)
        assertEquals(250.0, next.puck.y, TOLERANCE, "contact Y is interpolated along the swept path")
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `puck rebounding off a wall in the same tick still lands on the paddle`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 200.0, y = 50.0, vx = -1_700.0, vy = -800.0),
                paddle1Y = 20.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertEquals(PADDLE_A_FACE_X, next.puck.x, TOLERANCE)
        assertEquals(50.0, next.puck.y, TOLERANCE, "the swept Y is mirrored back into the playfield")
        assertTrue(next.puck.vx > 0.0)
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `deflected puck is not caught again on the following tick`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 400.0, y = 300.0, vx = -5_000.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val deflected = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))
        val next = reduce(deflected, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 200_000_000L))

        assertTrue(next.puck.x > PADDLE_A_FACE_X, "a separating puck must not be pinned to the face")
        assertTrue(next.puck.vx > 0.0)
    }

    @Test
    fun `paddle moves within one frame accumulate into the tick`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 300.0, vx = -500.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val movedOnce = reduce(state, GameAction.MovePaddle(330.0, PaddleSide.A))
        val movedTwice = reduce(movedOnce, GameAction.MovePaddle(350.0, PaddleSide.A))
        val next = reduce(movedTwice, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(50.0, movedTwice.paddle1Velocity, TOLERANCE, "both moves must reach the tick")
        assertEquals(0.0, next.paddle1Velocity, TOLERANCE, "the tick consumes the accumulated travel")
        assertTrue(abs(next.puck.spin) > 0.0, "paddle travel must impart spin")
        assertEquals(MviScore(), next.score)
    }

    @Test
    fun `puck curving in over the paddle edge rebounds instead of entering the body`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 100.0, vx = -1_000.0, vy = 1_647.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.1, elapsedNs = 100_000_000L))

        assertTrue(next.puck.vy < 0.0, "the puck must rebound off the top edge")
        assertTrue(
            next.puck.y <= state.paddle1Y - next.puck.radius + TOLERANCE,
            "the puck must end the tick outside the paddle body",
        )
        assertEquals(MviScore(), next.score, "an edge contact must not concede")
    }

    @Test
    fun `puck is never left inside the paddle rectangle`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 35.0, y = 300.0, vx = -260.0, vy = 40.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.05, elapsedNs = 50_000_000L))

        val insideBody =
            next.puck.x - next.puck.radius < PADDLE_WIDTH &&
                next.puck.y + next.puck.radius > state.paddle1Y &&
                next.puck.y - next.puck.radius < state.paddle1Y + state.paddleHeight
        assertTrue(!insideBody, "puck at ${next.puck.x}, ${next.puck.y} ended inside the paddle")
    }

    @Test
    fun `move paddle positions the paddle centre on the requested y`() {
        val state = MviGameState()

        val next = reduce(state, GameAction.MovePaddle(300.0, PaddleSide.B))

        assertEquals(250.0, next.paddle2Y, TOLERANCE, "requested centre 300 puts the top edge at 250")
        assertEquals(300.0, next.paddle2Y + next.paddleHeight / 2.0, TOLERANCE)
    }

    @Test
    fun `puck that genuinely misses the paddle still concedes a goal`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 100.0, vx = -1_000.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(deltaSeconds = 0.2, elapsedNs = 200_000_000L))

        assertEquals(1, next.score.playerB, "a real miss must still score")
        assertEquals(state.canvasWidth / 2.0, next.puck.x, TOLERANCE, "puck returns to the serve spot")
    }

    private companion object {
        const val PADDLE_A_FACE_X = 30.0
        const val PADDLE_WIDTH = 20.0
        const val MIN_PLAYABLE_SPEED = 100.0
        const val TOLERANCE = 0.0001
    }
}

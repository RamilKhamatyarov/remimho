package ru.rkhamatyarov.service.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.PowerUpType
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MviGameEngineTest {
    @Test
    fun `tick action updates puck position`() {
        val state = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = 50.0, vy = 0.0))

        val next = reduce(state, GameAction.Tick(0.5))

        assertEquals(125.0, next.puck.x, 0.0001)
        assertEquals(100.0, next.puck.y, 0.0001)
    }

    @Test
    fun `negative tick delta is ignored`() {
        val state = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = 50.0, vy = 0.0))

        val next = reduce(state, GameAction.Tick(-0.5))

        assertEquals(state, next)
    }

    @Test
    fun `move paddle action clamps to canvas bounds`() {
        val state = MviGameState()

        val next = reduce(state, GameAction.MovePaddle(10_000.0))

        assertEquals(500.0, next.paddle2Y, 0.0001)
    }

    @Test
    fun `move paddle can target left paddle when side is A`() {
        val state = MviGameState()

        val next = reduce(state, GameAction.MovePaddle(123.0, PaddleSide.A))

        assertEquals(123.0, next.paddle1Y, 0.0001)
        assertEquals(state.paddle2Y, next.paddle2Y, 0.0001)
    }

    @Test
    fun `tick applies turbo speed multiplier without storing turbo state`() {
        val state = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = 50.0, vy = 0.0))

        val next = reduce(state, GameAction.Tick(0.5, turboSpeedMultiplier = 2.5))

        assertEquals(162.5, next.puck.x, 0.0001)
    }

    @Test
    fun `left paddle collision requires leftward puck velocity`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = 50.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 1L))

        assertTrue(next.puck.vx > 0.0)
        assertNotEquals(30.0, next.puck.x)
    }

    @Test
    fun `left paddle collision flips leftward puck on y overlap`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = -50.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 1L))

        assertEquals(50.0, next.puck.vx, 0.0001)
        assertEquals(30.0, next.puck.x, 0.0001)
    }

    @Test
    fun `tick does not move bot paddle inside pure reducer`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 300.0, vx = -50.0, vy = 0.0),
                paddle1Y = 300.0,
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 16_000_000L))

        assertEquals(state.paddle1Y, next.paddle1Y, 0.0001)
    }

    @Test
    fun `magnet effect scales with delta seconds`() {
        val elapsedNs = 1_000_000_000L
        val state =
            MviGameState(
                puck = MviPuck(x = 700.0, y = 300.0, vx = 0.0, vy = 0.0),
                paddle2Y = 250.0,
                activePowerUps =
                    listOf(
                        MviActivePowerUp(
                            type = PowerUpType.MAGNET_BALL,
                            activatedNs = elapsedNs,
                            durationNs = 10_000_000_000L,
                        ),
                    ),
            )

        val smallDelta = reduce(state, GameAction.Tick(0.016, elapsedNs = elapsedNs))
        val largeDelta = reduce(state, GameAction.Tick(0.032, elapsedNs = elapsedNs))

        assertTrue(largeDelta.puck.vx > smallDelta.puck.vx * 1.5)
    }

    @Test
    fun `paddle contact point redirects puck vertically`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 255.0, vx = -50.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 1L))

        assertTrue(next.puck.vx > 0.0)
        assertTrue(next.puck.vy < 0.0)
        assertEquals(30.0, next.puck.x, 0.0001)
    }

    @Test
    fun `moving paddle hit applies deterministic spin and consumes paddle velocity`() {
        val moved = reduce(MviGameState(paddle2Y = 250.0), GameAction.MovePaddle(200.0, PaddleSide.B))
        val colliding =
            moved.copy(
                puck = MviPuck(x = 775.0, y = 250.0, vx = 100.0, vy = 0.0),
            )

        val next = reduce(colliding, GameAction.Tick(0.016, elapsedNs = 1L))

        assertTrue(next.puck.vx < 0.0)
        assertTrue(next.puck.spin < 0.0)
        assertTrue(next.puck.spinRemainingNs > 0L)
        assertEquals(0.0, next.paddle2Velocity, 0.0001)
    }

    @Test
    fun `active spin curves puck and decays on tick`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 400.0, y = 300.0, vx = 100.0, vy = 0.0, spin = 0.5, spinRemainingNs = 750_000_000L),
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 16_000_000L))

        assertTrue(next.puck.vy > 0.0)
        assertTrue(next.puck.spin in 0.0..0.5)
        assertTrue(next.puck.spinRemainingNs < 750_000_000L)
    }

    @Test
    fun `spin expires deterministically`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 400.0, y = 300.0, vx = 100.0, vy = 0.0, spin = 0.5, spinRemainingNs = 1_000_000L),
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 16_000_000L))

        assertEquals(0.0, next.puck.spin, 0.0001)
        assertEquals(0L, next.puck.spinRemainingNs)
    }

    @Test
    fun `line reflection dampens spin without removing replayable state`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 300.0, vx = 50.0, vy = 0.0, spin = 0.5, spinRemainingNs = 750_000_000L),
                lines = listOf(MviLine("wall", listOf(MviPoint(100.0, 0.0), MviPoint(100.0, 600.0)))),
            )

        val next = reduce(state, GameAction.Tick(0.016, elapsedNs = 16_000_000L))

        assertTrue(next.puck.vx < 0.0)
        assertTrue(next.puck.spin > 0.0)
        assertTrue(next.puck.spin < state.puck.spin)
        assertTrue(next.puck.spinRemainingNs > 0L)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `actor dispatch updates state flow`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine = MviGameEngine(dispatcher, testOnly = true)

            val sent = engine.tryDispatch(GameAction.Tick(1.0))
            advanceUntilIdle()

            assertTrue(sent)
            assertEquals(700.0, engine.state.value.puck.x, 0.0001)
            engine.close()
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `actor survives reducer exception and processes next action`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine = MviGameEngine(dispatcher, testOnly = true)

            val badTickSent = engine.tryDispatch(GameAction.Tick(Double.NaN, elapsedNs = 1L))
            val moveSent = engine.tryDispatch(GameAction.MovePaddle(123.0))
            advanceUntilIdle()

            assertTrue(badTickSent)
            assertTrue(moveSent)
            assertEquals(123.0, engine.state.value.paddle2Y, 0.0001)
            engine.close()
        }
}

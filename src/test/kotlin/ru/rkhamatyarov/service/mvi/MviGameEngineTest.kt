package ru.rkhamatyarov.service.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
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
    fun `left paddle collision requires leftward puck velocity`() {
        val state =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = 50.0, vy = 0.0),
                paddle1Y = 250.0,
            )

        val next = reduce(state, GameAction.Tick(0.016, nowNs = 1L))

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

        val next = reduce(state, GameAction.Tick(0.016, nowNs = 1L))

        assertEquals(50.0, next.puck.vx, 0.0001)
        assertEquals(30.0, next.puck.x, 0.0001)
    }

    @Test
    fun `ai tracking error changes deterministically with elapsed time`() {
        val aiConfig =
            AiOpponentConfig(
                enabled = true,
                reactionDelayMs = 1,
                maxSpeed = 1000.0,
                trackingError = 50.0,
                reactZoneRatio = 1.0,
            )
        val baseState =
            MviGameState(
                puck = MviPuck(x = 100.0, y = 300.0, vx = -50.0, vy = 0.0),
                paddle1Y = 300.0,
                aiSmoothedPuckY = 300.0,
                aiConfig = aiConfig,
            )

        val withoutError = reduce(baseState.copy(elapsedSeconds = 0.0), GameAction.Tick(0.016, nowNs = 1L))
        val withError = reduce(baseState.copy(elapsedSeconds = Math.PI / 5.0), GameAction.Tick(0.016, nowNs = 1L))

        assertNotEquals(withoutError.paddle1Y, withError.paddle1Y)
    }

    @Test
    fun `magnet effect scales with delta seconds`() {
        val nowNs = 1_000_000_000L
        val state =
            MviGameState(
                puck = MviPuck(x = 700.0, y = 300.0, vx = 0.0, vy = 0.0),
                paddle2Y = 250.0,
                activePowerUps =
                    listOf(
                        MviActivePowerUp(
                            type = PowerUpType.MAGNET_BALL,
                            activatedNs = nowNs,
                            durationNs = 10_000_000_000L,
                        ),
                    ),
            )

        val smallDelta = reduce(state, GameAction.Tick(0.016, nowNs = nowNs))
        val largeDelta = reduce(state, GameAction.Tick(0.032, nowNs = nowNs))

        assertTrue(largeDelta.puck.vx > smallDelta.puck.vx * 1.5)
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

            val badTickSent = engine.tryDispatch(GameAction.Tick(Double.NaN, nowNs = 1L))
            val moveSent = engine.tryDispatch(GameAction.MovePaddle(123.0))
            advanceUntilIdle()

            assertTrue(badTickSent)
            assertTrue(moveSent)
            assertEquals(123.0, engine.state.value.paddle2Y, 0.0001)
            engine.close()
        }
}

package ru.rkhamatyarov.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore
import ru.rkhamatyarov.service.mvi.PaddleSide
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaddleContactIntegrationTest {
    @Test
    fun `fast puck at the paddle centre is deflected without conceding`() =
        runTest {
            val room = testRoom()
            restore(
                room,
                MviGameState(
                    puck = MviPuck(x = 400.0, y = 300.0, vx = -5_000.0, vy = 0.0),
                    paddle1Y = 250.0,
                    aiConfig = DISABLED_AI,
                ),
            )

            room.dispatch(GameIntent.Reliable(GameAction.Tick(TICK_SECONDS, TICK_NS)))
            advanceUntilIdle()

            val state = room.reliableState.value
            assertEquals(PADDLE_A_FACE_X, state.puck.x, TOLERANCE)
            assertTrue(state.puck.vx > 0.0)
            assertEquals(MviScore(), state.score)
            room.shutdown()
        }

    @Test
    fun `puck resting inside the paddle is released instead of conceding`() =
        runTest {
            val room = testRoom()
            restore(
                room,
                MviGameState(
                    puck = MviPuck(x = 25.0, y = 300.0, vx = 0.0, vy = 0.0),
                    paddle1Y = 250.0,
                    aiConfig = DISABLED_AI,
                ),
            )

            room.dispatch(GameIntent.Reliable(GameAction.Tick(TICK_SECONDS, TICK_NS)))
            advanceUntilIdle()

            val state = room.reliableState.value
            assertEquals(PADDLE_A_FACE_X, state.puck.x, TOLERANCE)
            assertTrue(state.puck.vx > 0.0, "a stalled puck must be released")
            assertEquals(MviScore(), state.score)
            room.shutdown()
        }

    @Test
    fun `tracking paddles hold a long rally without conceding`() =
        runTest {
            val room = testRoom()
            restore(
                room,
                MviGameState(
                    puck = MviPuck(x = 400.0, y = 300.0, vx = -900.0, vy = 260.0),
                    aiConfig = DISABLED_AI,
                ),
            )

            repeat(RALLY_TICKS) { index ->
                val current = room.reliableState.value
                val target =
                    (current.puck.y - current.paddleHeight / 2.0)
                        .coerceIn(0.0, current.canvasHeight - current.paddleHeight)
                room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(target, PaddleSide.A)))
                room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(target, PaddleSide.B)))
                room.dispatch(GameIntent.Reliable(GameAction.Tick(TICK_SECONDS, (index + 1) * TICK_NS)))
                advanceUntilIdle()
            }

            val state = room.reliableState.value
            assertEquals(MviScore(), state.score, "paddles tracking the puck must never concede")
            assertTrue(state.puck.x > 0.0 && state.puck.x < state.canvasWidth, "puck must stay on the field")
            room.shutdown()
        }

    private fun TestScope.restore(
        room: GameRoom,
        state: MviGameState,
    ) {
        room.dispatch(GameIntent.Reliable(GameAction.RestoreSnapshot(state)))
        advanceUntilIdle()
    }

    private fun TestScope.testRoom(): GameRoom {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        return GameRoom("paddle-contact-room", scope = scope, autoPowerUpsEnabled = false)
    }

    private companion object {
        val DISABLED_AI = AiOpponentConfig(enabled = false)
        const val PADDLE_A_FACE_X = 30.0
        const val TICK_SECONDS = 0.1
        const val TICK_NS = 100_000_000L
        const val RALLY_TICKS = 180
        const val TOLERANCE = 0.0001
    }
}

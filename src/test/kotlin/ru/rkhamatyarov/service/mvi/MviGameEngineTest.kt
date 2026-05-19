package ru.rkhamatyarov.service.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MviGameEngineTest {
    @Test
    fun test_tickAction_updatesPuckPosition() {
        val state = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = 50.0, vy = 0.0))

        val next = reduce(state, GameAction.Tick(0.5))

        assertEquals(125.0, next.puck.x, 0.0001)
        assertEquals(100.0, next.puck.y, 0.0001)
    }

    @Test
    fun test_negativeTickDelta_isIgnored() {
        val state = MviGameState(puck = MviPuck(x = 100.0, y = 100.0, vx = 50.0, vy = 0.0))

        val next = reduce(state, GameAction.Tick(-0.5))

        assertEquals(state, next)
    }

    @Test
    fun test_movePaddleAction_clampsToCanvasBounds() {
        val state = MviGameState()

        val next = reduce(state, GameAction.MovePaddle(10_000.0))

        assertEquals(500.0, next.paddle2Y, 0.0001)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun test_actorDispatch_updatesStateFlow() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine = MviGameEngine(dispatcher, testOnly = true)

            val sent = engine.tryDispatch(GameAction.Tick(1.0))
            advanceUntilIdle()

            assertTrue(sent)
            assertEquals(700.0, engine.state.value.puck.x, 0.0001)
            engine.close()
        }
}

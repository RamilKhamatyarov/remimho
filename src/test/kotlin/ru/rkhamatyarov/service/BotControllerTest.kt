package ru.rkhamatyarov.service

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotControllerTest {
    @Test
    fun `reaction delay uses logical elapsed time`() {
        val controller = BotController("room")
        val state = botState(AiOpponentConfig(reactionDelayMs = 100, aimError = 0.0))

        assertNull(controller.nextMove(state, 1_000_000L))
        assertNull(controller.nextMove(state, 100_000_000L))
        assertTrue(controller.nextMove(state, 101_000_000L)!!.y < state.paddle1Y)
    }

    @Test
    fun `wall prediction changes target direction`() {
        val puck = MviPuck(x = 400.0, y = 100.0, vx = -100.0, vy = -200.0)
        val directController = BotController("room")
        val predictedController = BotController("room")
        val directState = botState(AiOpponentConfig(reactionDelayMs = 0, aimError = 0.0, predictionDepth = 0), puck)
        val predictedState = botState(AiOpponentConfig(reactionDelayMs = 0, aimError = 0.0, predictionDepth = 1), puck)

        directController.nextMove(directState, 0L)
        predictedController.nextMove(predictedState, 0L)
        val directMove = directController.nextMove(directState, 16_000_000L)!!
        val predictedMove = predictedController.nextMove(predictedState, 16_000_000L)!!

        assertTrue(directMove.y < directState.paddle1Y)
        assertTrue(predictedMove.y > predictedState.paddle1Y)
    }

    @Test
    fun `time scaling is capped after three minutes`() {
        val config = AiOpponentConfig(reactionDelayMs = 300, aimError = 20.0, predictionDepth = 1, aggression = 0.4)

        val scaled = config.scaledForElapsed(180_000_000_000L)
        val overtime = config.scaledForElapsed(600_000_000_000L)

        assertEquals(210L, scaled.reactionDelayMs)
        assertEquals(14.0, scaled.aimError, 0.0001)
        assertEquals(2, scaled.predictionDepth)
        assertEquals(0.55, scaled.aggression, 0.0001)
        assertEquals(scaled, overtime)
    }

    @Test
    fun `impossible values remain at their limits while scaling`() {
        val impossible = AiOpponentConfig(reactionDelayMs = 0, aimError = 0.0, predictionDepth = 4, aggression = 1.0)

        assertEquals(impossible, impossible.scaledForElapsed(600_000_000_000L))
    }

    @Test
    fun `disabled bot emits no move`() {
        val controller = BotController("room")
        val state = botState(AiOpponentConfig(enabled = false, reactionDelayMs = 0))

        assertNull(controller.nextMove(state, 16_000_000L))
    }

    private fun botState(
        config: AiOpponentConfig,
        puck: MviPuck = MviPuck(x = 300.0, y = 120.0, vx = -100.0, vy = 0.0),
    ): MviGameState =
        MviGameState(
            puck = puck,
            paddle1Y = 250.0,
            aiConfig = config,
        )
}

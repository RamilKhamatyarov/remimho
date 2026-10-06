package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.SpeedConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddleBoundaryTest {
    @Test
    fun `paddle contact before a wall is not lost by folding the end of the tick`() {
        for (side in PaddleSide.entries) {
            val state = opening(side, y = 500.0, vy = 1000.0, paddleY = 500.0)
            val next = reduce(state, GameAction.Tick(0.3, 300_000_000L))
            assertEquals(state.score, next.score, "side=$side")
            assertEquals(TouchSource.PADDLE, next.touchLedger.entries.last().source)
            assertEquals(side, next.touchLedger.entries.last().ownerSide)
        }
    }

    @Test
    fun `wall rebound followed by a paddle hit follows the reflected path`() {
        for (side in PaddleSide.entries) {
            val state = opening(side, y = 580.0, vy = 1000.0, paddleY = 490.0)
            val next = reduce(state, GameAction.Tick(0.3, 300_000_000L))
            assertEquals(state.score, next.score, "side=$side")
            assertEquals(listOf(TouchSource.WALL, TouchSource.PADDLE), next.touchLedger.entries.map { it.source })
            assertTrue(if (side == PaddleSide.A) next.puck.vx > 0 else next.puck.vx < 0)
        }
    }

    @Test
    fun `puck starting inside a wall zone bounces instead of leaving the field`() {
        val cases =
            listOf(
                Triple("wall:top", 8.0, -100.0),
                Triple("wall:bottom", 592.0, 100.0),
            )
        for ((wallId, y, vy) in cases) {
            var state = MviGameState(puck = MviPuck(x = 400.0, y = y, vx = 0.0, vy = vy))

            state = reduce(state, GameAction.Tick(0.016, 16_000_000L))
            assertEquals(wallId, state.touchLedger.entries.single().identifier)
            assertTrue(state.puck.vy * vy < 0.0, "$wallId must reverse the puck")

            repeat(60) { tick -> state = reduce(state, GameAction.Tick(0.016, (tick + 2) * 16_000_000L)) }
            assertTrue(
                state.puck.y - state.puck.radius >= 0.0 && state.puck.y + state.puck.radius <= state.canvasHeight,
                "$wallId: puck escaped the field at y=${state.puck.y}",
            )
        }
    }

    @Test
    fun `reflected shot clear of the paddle still scores`() {
        for (side in PaddleSide.entries) {
            val state = opening(side, y = 580.0, vy = 1000.0, paddleY = 100.0)
            val next = reduce(state, GameAction.Tick(0.3, 300_000_000L))
            assertEquals(1, next.score.playerA + next.score.playerB)
        }
    }

    private fun opening(
        side: PaddleSide,
        y: Double,
        vy: Double,
        paddleY: Double,
    ): MviGameState =
        MviGameState(
            puck =
                MviPuck(
                    x = if (side == PaddleSide.A) 100.0 else 700.0,
                    y = y,
                    vx = if (side == PaddleSide.A) -1000.0 else 1000.0,
                    vy = vy,
                ),
            paddle1Y = paddleY,
            paddle2Y = paddleY,
            speedConfig = SpeedConfig(timeAccelerationRate = 0.0, levelAccelerationPerLine = 0.0),
        )
}

package ru.rkhamatyarov.service.turbo

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviDomainEvent
import ru.rkhamatyarov.service.mvi.PaddleSide
import kotlin.test.assertEquals

class TurboBoostStrategyTest {
    @Test
    fun `paddle hits charge turbo until ready`() {
        val strategy = TurboBoostStrategy()

        repeat(5) {
            strategy.onEvents(listOf(MviDomainEvent.PaddleHit(PaddleSide.B)), it.toLong())
        }

        val side =
            strategy.hudState.value.states
                .first { it.side == PaddleSide.B }
        assertEquals(100.0, side.charge, 0.0001)
        assertEquals(TurboStatus.READY, side.status)
    }

    @Test
    fun `activation applies speed multiplier then enters cooldown and resets charge`() {
        val strategy = TurboBoostStrategy()
        repeat(5) {
            strategy.onEvents(listOf(MviDomainEvent.PaddleHit(PaddleSide.B)), it.toLong())
        }

        strategy.onAction(GameAction.ActivateTurbo(PaddleSide.B), 10L)
        assertEquals(2.5, strategy.speedMultiplier(10L), 0.0001)

        strategy.onAction(GameAction.Tick(0.016, elapsedNs = 1_000_000_010L), 1_000_000_010L)
        val cooldown =
            strategy.hudState.value.states
                .first { it.side == PaddleSide.B }
        assertEquals(TurboStatus.COOLDOWN, cooldown.status)
        assertEquals(100.0, cooldown.charge, 0.0001)

        strategy.onAction(GameAction.Tick(0.016, elapsedNs = 7_000_000_010L), 7_000_000_010L)
        val charging =
            strategy.hudState.value.states
                .first { it.side == PaddleSide.B }
        assertEquals(TurboStatus.CHARGING, charging.status)
        assertEquals(0.0, charging.charge, 0.0001)
    }

    @Test
    fun `line deflect charges last paddle touch side`() {
        val strategy = TurboBoostStrategy()

        strategy.onEvents(listOf(MviDomainEvent.PaddleHit(PaddleSide.A)), 1L)
        strategy.onEvents(listOf(MviDomainEvent.LineDeflect), 2L)

        val sideA =
            strategy.hudState.value.states
                .first { it.side == PaddleSide.A }
        val sideB =
            strategy.hudState.value.states
                .first { it.side == PaddleSide.B }
        assertEquals(40.0, sideA.charge, 0.0001)
        assertEquals(0.0, sideB.charge, 0.0001)
    }
}

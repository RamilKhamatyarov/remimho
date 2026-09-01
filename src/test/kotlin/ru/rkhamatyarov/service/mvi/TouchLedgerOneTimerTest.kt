package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchLedgerOneTimerTest {
    @Test
    fun `touch ledger retains only the eight most recent contacts`() {
        val ledger =
            (0 until 10).fold(TouchLedger()) { current, index ->
                current.append(PuckTouch(TouchSource.WALL, null, "wall-$index", index.toLong(), index.toDouble()))
            }

        assertEquals(TouchLedger.MAX_ENTRIES, ledger.entries.size)
        assertEquals("wall-2", ledger.entries.first().identifier)
        assertEquals("wall-9", ledger.entries.last().identifier)
    }

    @Test
    fun `hot direct return fires one timer and records incoming effective speed`() {
        val state = directReturnState(rawSpeed = 400.0)

        val captured =
            MviDomainEvents.capture {
                reduce(state, GameAction.Tick(0.016, elapsedNs = 1_000_000_000L, turboSpeedMultiplier = 1.25))
            }

        val event = captured.events.filterIsInstance<MviDomainEvent.OneTimerFired>().single()
        assertEquals(PaddleSide.A, event.side)
        assertEquals(500.0, event.incomingSpeed, 0.0001)
        assertEquals(2.0, event.multiplier, 0.0001)
        assertEquals(800.0, hypot(captured.value.puck.vx, captured.value.puck.vy), 0.0001)
        assertEquals(
            500.0,
            captured.value.touchLedger.entries
                .last()
                .speedAtContact,
            0.0001,
        )
    }

    @Test
    fun `full strength one timer is visibly faster`() {
        val captured =
            MviDomainEvents.capture {
                reduce(
                    directReturnState(rawSpeed = 400.0),
                    GameAction.Tick(0.01, elapsedNs = 1_000_000_000L, turboSpeedMultiplier = 2.0),
                )
            }

        val event = captured.events.filterIsInstance<MviDomainEvent.OneTimerFired>().single()
        assertEquals(2.0, event.multiplier, 0.0001)
        assertEquals(800.0, hypot(captured.value.puck.vx, captured.value.puck.vy), 0.0001)
    }

    @Test
    fun `one timer respects raw speed cap`() {
        val captured =
            MviDomainEvents.capture {
                reduce(
                    directReturnState(rawSpeed = 700.0),
                    GameAction.Tick(0.01, elapsedNs = 1_000_000_000L, turboSpeedMultiplier = 8.0 / 7.0),
                )
            }

        assertEquals(800.0, hypot(captured.value.puck.vx, captured.value.puck.vy), 0.0001)
    }

    @Test
    fun `intervening contact prevents one timer`() {
        val state =
            directReturnState(rawSpeed = 500.0).copy(
                touchLedger =
                    TouchLedger(
                        listOf(
                            PuckTouch(TouchSource.PADDLE, PaddleSide.B, "paddle:B", 0L, 500.0),
                            PuckTouch(TouchSource.WALL, null, "wall:top", 500_000_000L, 500.0),
                        ),
                    ),
            )

        val captured =
            MviDomainEvents.capture {
                reduce(state, GameAction.Tick(0.016, elapsedNs = 1_000_000_000L))
            }

        assertTrue(captured.events.none { it is MviDomainEvent.OneTimerFired })
        assertEquals(500.0, hypot(captured.value.puck.vx, captured.value.puck.vy), 0.0001)
    }

    @Test
    fun `stale opponent touch prevents one timer`() {
        val captured =
            MviDomainEvents.capture {
                reduce(directReturnState(rawSpeed = 500.0), GameAction.Tick(0.016, elapsedNs = 2_000_000_001L))
            }

        assertTrue(captured.events.none { it is MviDomainEvent.OneTimerFired })
    }

    @Test
    fun `wall line and powerup contacts append stable attribution`() {
        val wallState =
            reduce(
                MviGameState(puck = MviPuck(x = 400.0, y = 8.0, vx = 0.0, vy = -100.0)),
                GameAction.Tick(0.016, elapsedNs = 16_000_000L),
            )
        assertEquals(
            TouchSource.WALL,
            wallState.touchLedger.entries
                .single()
                .source,
        )
        assertEquals(
            "wall:top",
            wallState.touchLedger.entries
                .single()
                .identifier,
        )

        val line = MviLine("line-1", listOf(MviPoint(100.0, 0.0), MviPoint(100.0, 600.0)), ownerSide = PaddleSide.B)
        val lineState =
            reduce(
                MviGameState(puck = MviPuck(x = 105.0, y = 300.0, vx = -100.0, vy = 0.0), lines = listOf(line)),
                GameAction.Tick(0.016, elapsedNs = 16_000_000L),
            )
        assertEquals(
            PuckTouch(TouchSource.DRAWN_LINE, PaddleSide.B, "line-1", 16_000_000L, 102.0),
            lineState.touchLedger.entries.single(),
        )

        val powerUp = MviPowerUp("power-1", 400.0, 300.0, PowerUpType.SPEED_BOOST, 0L)
        val powerUpState =
            reduce(
                MviGameState(puck = MviPuck(x = 400.0, y = 300.0, vx = 0.0, vy = 0.0), powerUps = listOf(powerUp)),
                GameAction.Tick(0.016, elapsedNs = 16_000_000L),
            )
        assertEquals(
            TouchSource.POWER_UP,
            powerUpState.touchLedger.entries
                .single()
                .source,
        )
        assertEquals(
            "power-1",
            powerUpState.touchLedger.entries
                .single()
                .identifier,
        )
    }

    @Test
    fun `score and reset clear combo context`() {
        val ledger = TouchLedger(listOf(PuckTouch(TouchSource.PADDLE, PaddleSide.B, "paddle:B", 1L, 500.0)))
        val scoringState =
            MviGameState(
                puck = MviPuck(x = 5.0, y = 500.0, vx = -100.0, vy = 0.0),
                touchLedger = ledger,
            )

        val afterScore = reduce(scoringState, GameAction.Tick(0.016, elapsedNs = 16_000_000L))
        val afterReset = reduce(scoringState, GameAction.Reset)

        assertTrue(afterScore.touchLedger.entries.isEmpty())
        assertTrue(afterReset.touchLedger.entries.isEmpty())
    }

    private fun directReturnState(rawSpeed: Double): MviGameState =
        MviGameState(
            puck = MviPuck(x = 25.0, y = 300.0, vx = -rawSpeed, vy = 0.0),
            paddle1Y = 250.0,
            touchLedger =
                TouchLedger(
                    listOf(PuckTouch(TouchSource.PADDLE, PaddleSide.B, "paddle:B", 0L, rawSpeed)),
                ),
        )
}

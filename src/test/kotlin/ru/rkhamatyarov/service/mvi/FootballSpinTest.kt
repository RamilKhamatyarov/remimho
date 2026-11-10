package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.util.PaddleContactMother.edge
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FootballSpinTest {
    @Test
    fun `edge swipes add signed momentum and spin on both sides and edges`() {
        for (side in PaddleSide.entries) {
            for (above in listOf(true, false)) {
                for (travel in listOf(-20.0, 20.0)) {
                    val captured = MviDomainEvents.capture { edge(side, above, travel) }
                    val result = captured.value
                    assertEquals(10.0 + travel * 12.0, result.puck.vx, 1e-9)
                    assertEquals(if (above) -2000.0 else 2000.0, result.puck.vy)
                    assertEquals(travel / 45.0 * 1.25, result.puck.spin, 1e-9)
                    assertEquals(2_000_000_000L, result.puck.spinRemainingNs)
                    val touch = result.touchLedger.entries.single()
                    assertEquals(TouchSource.PADDLE, touch.source)
                    assertEquals(side, touch.ownerSide)
                    assertEquals(16_000_000L, touch.elapsedNs)
                    assertEquals(MviPuck(vx = 10.0, vy = 2000.0).speed(1.0), touch.speedAtContact)
                    assertEquals(listOf(MviDomainEvent.PaddleHit(side, PaddleContactAxis.EDGE)), captured.events)
                }
            }
        }
    }

    @Test
    fun `edge spin is additive bounded and cancels without refreshing a stationary edge`() {
        assertEquals(1.5, edge(travel = 100.0, spin = 1.0).puck.spin)
        assertEquals(370.0, edge(travel = 100.0).puck.vx)
        assertEquals(-350.0, edge(travel = -100.0).puck.vx)
        assertEquals(-1.5, edge(travel = -100.0, spin = -1.0).puck.spin)
        val stationary = edge(travel = 0.0, spin = -0.5).puck
        assertEquals(-0.5, stationary.spin)
        assertEquals(700_000_000L, stationary.spinRemainingNs)
        val tiny = edge(travel = 0.1, spin = 0.5).puck
        assertEquals(0.5, tiny.spin)
        assertEquals(700_000_000L, tiny.spinRemainingNs)
        val cancelled = edge(travel = 18.0, spin = -0.5).puck
        assertEquals(0.0, cancelled.spin)
        assertEquals(0L, cancelled.spinRemainingNs)
        assertTrue(edge(travel = 9.0, spin = -1.0).puck.spin < 0.0)
    }

    @Test
    fun `historical edge response preserves signed zero momentum and clears spin`() {
        for (rules in listOf(PhysicsRules.V1, PhysicsRules.V2)) {
            val puck = edge(travel = 20.0, spin = 0.5, vx = -0.0, rules = rules).puck
            assertEquals((-0.0).toBits(), puck.vx.toBits())
            assertEquals(0.0, puck.spin)
            assertEquals(0L, puck.spinRemainingNs)
        }
    }

    @Test
    fun `edge impulse does not replace stronger incoming horizontal momentum`() {
        assertEquals(240.0, edge(travel = 20.0, vx = 0.0).puck.vx)
        assertEquals(-760.0, edge(travel = 20.0, vx = -1000.0).puck.vx)
        assertEquals(760.0, edge(travel = -20.0, vx = 1000.0).puck.vx)
    }

    @Test
    fun `spin half life follows logical duration rather than tick count`() {
        val start = MviPuck(spin = 1.0, spinRemainingNs = 2_000_000_000L)
        val one = PuckPhysics.advance(start, 0.8, 1.0)
        val many = (1..50).fold(start) { puck, _ -> PuckPhysics.advance(puck, 0.016, 1.0) }
        assertEquals(0.5, one.spin, 1e-12)
        assertEquals(one.spin, many.spin, 1e-12)
        assertEquals(1_200_000_000L, many.spinRemainingNs)
        assertTrue(many.vy > start.vy)
        assertTrue(many.spin > 0.0)
    }

    @Test
    fun `expiry tick curves only for the remaining lifetime`() {
        val start = MviPuck(spin = 0.5, spinRemainingNs = 1_000_000L)
        val short = PuckPhysics.advance(start, 0.001, 1.0)
        val long = PuckPhysics.advance(start, 0.1, 1.0)
        assertEquals(short.vy, long.vy)
        assertTrue(long.vy > start.vy)
        assertEquals(0.0, long.spin)
        assertEquals(0L, long.spinRemainingNs)
    }

    @Test
    fun `face velocity rules remain unchanged while ordinary movement generates longer spin`() {
        val state = MviGameState(puck = MviPuck(x = 40.0, vx = -1000.0, vy = 0.0), paddle1Velocity = 1.0)
        val frame = TickFrame(state.puck.copy(x = 20.0), state.touchLedger)
        val old = PaddlePhysics.resolve(frame, state, 1.0, 16_000_000L, PhysicsRules.V2).puck
        val current = PaddlePhysics.resolve(frame, state, 1.0, 16_000_000L).puck
        assertEquals(old.vx, current.vx)
        assertEquals(old.vy, current.vy)
        assertEquals(0.0, old.spin)
        assertTrue(current.spin > 0.02)
        assertEquals(2_000_000_000L, current.spinRemainingNs)
    }

    @Test
    fun `wall retention stays unchanged and pause does not consume spin`() {
        val puck = MviPuck(y = 10.0, vy = -100.0, spin = 1.5, spinRemainingNs = 2_000_000_000L)
        val bounced = PuckPhysics.resolveWalls(TickFrame(puck, TouchLedger()), 600.0, 16_000_000L, 1.0)
        assertEquals(1.5 * 0.75, bounced.puck.spin)
        assertEquals(puck.spinRemainingNs, bounced.puck.spinRemainingNs)
        val paused = MviGameState(puck = puck, paused = true)
        assertEquals(paused, reduce(paused, GameAction.Tick(0.016, 16_000_000L)))
    }

    @Test
    fun `edge spin survives reduction and restore without a goal or leaked event`() {
        val start =
            MviGameState(
                puck = MviPuck(x = 15.0, y = 224.0, vx = 10.0, vy = 2000.0),
                paddle1Velocity = 20.0,
            )
        val action = GameAction.Tick(0.016, 16_000_000L)
        val captured = MviDomainEvents.capture { reduce(start, action) }
        val hit = captured.value
        assertEquals(start.score, hit.score)
        assertEquals(2_000_000_000L, hit.puck.spinRemainingNs)
        assertTrue(hit.puck.spin > 0.0)
        assertEquals(hit, reduce(start, action))
        assertEquals(hit, reduce(MviGameState(), GameAction.RestoreSnapshot(hit)))
        assertTrue(MviDomainEvents.capture { reduce(hit, GameAction.TogglePause) }.events.isEmpty())
        val next = reduce(hit, GameAction.Tick(0.016, 32_000_000L))
        assertTrue(abs(next.puck.spin) < abs(hit.puck.spin))
        assertTrue(next.puck.vy > hit.puck.vy)
    }
}

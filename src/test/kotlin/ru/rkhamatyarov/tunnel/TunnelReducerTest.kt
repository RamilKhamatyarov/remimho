package ru.rkhamatyarov.tunnel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TunnelReducerTest {
    @Test
    fun `paddle advances by a bounded amount each fixed step`() {
        val rules = TunnelRules(paddleTravelPerStep = TunnelMath.fromInt(8L))
        val target = TunnelVector2(TunnelMath.fromInt(20L), TunnelMath.fromInt(16L))
        var state = TunnelState(rules = rules)

        repeat(3) { state = reduceTunnel(state, listOf(TunnelInput.PaddleTarget(TunnelSide.A, target)), rules) }

        assertEquals(TunnelMath.fromInt(20L), state.paddleA.position.x)
        assertEquals(TunnelMath.fromInt(16L), state.paddleA.position.y)
        assertEquals(3L, state.step)
        assertEquals(TunnelState(rules = rules).puck.position, state.puck.position)
    }

    @Test
    fun `targets clamp to arena and serve changes only velocity`() {
        val rules = TunnelRules()
        val state = TunnelState()
        val served = TunnelVector3(TunnelMath.fromInt(1L), TunnelMath.fromInt(2L), TunnelMath.fromInt(3L))
        val next =
            reduceTunnel(
                state,
                listOf(
                    TunnelInput.PaddleTarget(
                        TunnelSide.B,
                        TunnelVector2(TunnelMath.fromInt(900L), TunnelMath.fromInt(-5L)),
                    ),
                    TunnelInput.Serve(served),
                ),
                rules,
            )

        assertEquals(rules.width, next.paddleB.target.x)
        assertEquals(TunnelScalar.ZERO, next.paddleB.target.y)
        assertEquals(served, next.puck.velocity)
        assertEquals(1L, next.step)
    }

    @Test
    fun `reset restores initial gameplay values and preserves logical step progression`() {
        val start =
            TunnelState(
                puck =
                    TunnelState().puck.copy(
                        velocity = TunnelVector3(TunnelMath.fromInt(1L), TunnelScalar.ZERO, TunnelScalar.ZERO),
                    ),
                scoreA = 3,
                scoreB = 2,
                step = 8L,
            )
        val reset = reduceTunnel(start, listOf(TunnelInput.Reset), start.rules)

        assertEquals(TunnelState(rules = start.rules).puck, reset.puck)
        assertEquals(0, reset.scoreA)
        assertEquals(0, reset.scoreB)
        assertEquals(9L, reset.step)
        assertNotEquals(start.puck, reset.puck)
    }

    @Test
    fun `rules reject dimensions outside the supported fixed point arena range`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            TunnelRules(width = TunnelScalar(TunnelRules.MAX_DIMENSION_RAW + 1L))
        }
    }
}

package ru.rkhamatyarov.tunnel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelPhysicsTest {
    private val rules = TunnelRules()

    @Test
    fun `zero spin keeps the puck on a straight line`() {
        val start = served(vx = 0L, vy = 0L, vz = 2L)
        val end = repeatSteps(start, rules, 30)

        assertEquals(start.puck.position.x, end.puck.position.x)
        assertEquals(start.puck.position.y, end.puck.position.y)
        assertTrue(end.puck.position.z > start.puck.position.z)
        assertEquals(TunnelScalar.ZERO, end.puck.spin.x)
    }

    @Test
    fun `puck never leaves the cross-section at any speed`() {
        val speeds = listOf(-400L, -40L, -4L, 0L, 4L, 40L, 400L)
        speeds.forEach { vx ->
            speeds.forEach { vy ->
                val start = served(vx = vx, vy = vy, vz = 0L)
                val end = repeatSteps(start, rules, 240)
                assertInside(end, rules, "vx=$vx vy=$vy")
            }
        }
    }

    @Test
    fun `constant spin bends toward its sign and reverses symmetrically`() {
        val positive = repeatSteps(withSpin(served(0L, 0L, 0L), 0.5), rules, 120)
        val negative = repeatSteps(withSpin(served(0L, 0L, 0L), -0.5), rules, 120)

        assertTrue(positive.puck.position.x > TunnelMath.fromInt(400L))
        assertTrue(negative.puck.position.x < TunnelMath.fromInt(400L))
        val offset = TunnelMath.subtract(positive.puck.position.x, TunnelMath.fromInt(400L))
        val mirrored = TunnelMath.subtract(TunnelMath.fromInt(400L), negative.puck.position.x)
        assertEquals(offset, mirrored)
    }

    @Test
    fun `stationary paddle reflects the puck without imparting spin`() {
        val start = coveringPaddle(served(0L, 0L, -4L))
        val end = repeatSteps(start, rules, 60)

        assertTrue(end.puck.velocity.z > TunnelScalar.ZERO)
        assertEquals(TunnelScalar.ZERO, end.puck.spin.x)
        assertEquals(TunnelScalar.ZERO, end.puck.spin.y)
        assertEquals(0, end.scoreB)
    }

    @Test
    fun `moving paddle imparts bounded spin in its movement direction`() {
        val start = coveringPaddle(served(0L, 0L, -40L))
        val target = TunnelVector2(TunnelMath.fromInt(800L), TunnelMath.fromInt(300L))
        var state = start
        repeat(10) {
            state = reduceTunnel(state, listOf(TunnelInput.PaddleTarget(TunnelSide.A, target)), rules)
        }

        assertTrue(state.puck.spin.x > TunnelScalar.ZERO)
        assertTrue(state.puck.spin.x <= rules.maxSpin)

        val fastRules = rules.copy(paddleSpinGain = TunnelMath.fromInt(50L))
        var capped = start.copy(rules = fastRules)
        repeat(10) {
            capped = reduceTunnel(capped, listOf(TunnelInput.PaddleTarget(TunnelSide.A, target)), fastRules)
        }
        assertTrue(capped.puck.spin.x <= fastRules.maxSpin)
    }

    @Test
    fun `a fast puck is stopped by a covering paddle`() {
        val start = coveringPaddle(served(0L, 0L, -20L))
        val end = repeatSteps(start, rules, 10)

        assertEquals(0, end.scoreB)
        assertTrue(end.puck.velocity.z > TunnelScalar.ZERO)
    }

    @Test
    fun `a puck outside the paddle extent scores`() {
        val start = served(0L, 0L, -4L)
        val end = repeatSteps(start, rules, 60)

        assertEquals(1, end.scoreB)
        assertEquals(TunnelVector3(TunnelScalar.ZERO, TunnelScalar.ZERO, TunnelScalar.ZERO), end.puck.velocity)
    }

    @Test
    fun `extreme speed stays bounded by the iteration cap`() {
        val start = served(vx = 1_000_000_000L, vy = 250_000_000L, vz = 0L)
        val end = reduceTunnel(start, emptyList(), rules)

        assertInside(end, rules, "extreme")
    }

    private fun served(
        vx: Long,
        vy: Long,
        vz: Long,
    ): TunnelState =
        reduceTunnel(
            TunnelState(rules = rules),
            listOf(
                TunnelInput.Serve(
                    TunnelVector3(TunnelMath.fromInt(vx), TunnelMath.fromInt(vy), TunnelMath.fromInt(vz)),
                ),
            ),
            rules,
        )

    private fun withSpin(
        state: TunnelState,
        spin: Double,
    ): TunnelState {
        val half = TunnelMath.fromRatio(1L, 2L)
        val value = if (spin < 0) TunnelMath.negate(half) else half
        return state.copy(puck = state.puck.copy(spin = TunnelVector2(value, TunnelScalar.ZERO)))
    }

    private fun coveringPaddle(state: TunnelState): TunnelState {
        val centre = TunnelVector2(TunnelMath.fromInt(400L), TunnelMath.fromInt(300L))
        return state.copy(
            paddleA = TunnelPaddle(position = centre, target = centre),
            paddleB = TunnelPaddle(position = centre, target = centre),
        )
    }

    private fun repeatSteps(
        state: TunnelState,
        rules: TunnelRules,
        steps: Int,
    ): TunnelState {
        var current = state
        repeat(steps) { current = reduceTunnel(current, emptyList(), rules) }
        return current
    }

    private fun assertInside(
        state: TunnelState,
        rules: TunnelRules,
        label: String,
    ) {
        val radius = state.puck.radius
        assertTrue(
            state.puck.position.x >= radius && state.puck.position.x <= TunnelMath.subtract(rules.width, radius),
            "$label: x=${state.puck.position.x.raw}",
        )
        assertTrue(
            state.puck.position.y >= radius && state.puck.position.y <= TunnelMath.subtract(rules.height, radius),
            "$label: y=${state.puck.position.y.raw}",
        )
    }
}

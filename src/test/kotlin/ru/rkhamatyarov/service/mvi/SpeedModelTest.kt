package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.SpeedConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure speed rules: rally growth, smash, ceilings and the effective-speed cap. */
class SpeedModelTest {
    private val config = SpeedConfig()

    @Test
    fun `passive return grows the raw speed by the per-hit factor`() {
        assertEquals(360.0 * 1.06, SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 0.0, config), TOLERANCE)
    }

    @Test
    fun `full swing adds the whole smash boost`() {
        assertEquals(360.0 * 1.06 * 1.45, SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 80.0, config), TOLERANCE)
    }

    @Test
    fun `smash scales with the swing regardless of its direction`() {
        val halfSwingUp = SpeedModel.rallyReturnSpeed(360.0, paddleTravel = -40.0, config)
        val halfSwingDown = SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 40.0, config)

        assertEquals(360.0 * 1.06 * 1.225, halfSwingUp, TOLERANCE)
        assertEquals(halfSwingUp, halfSwingDown, TOLERANCE)
    }

    @Test
    fun `swing beyond a full swing gives no extra boost`() {
        assertEquals(
            SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 80.0, config),
            SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 500.0, config),
            TOLERANCE,
        )
    }

    @Test
    fun `rally growth stops at the maximum rally speed`() {
        assertEquals(3_200.0, SpeedModel.rallyReturnSpeed(3_100.0, paddleTravel = 80.0, config), TOLERANCE)
    }

    @Test
    fun `return never slows a puck already faster than the rally ceiling`() {
        assertEquals(3_500.0, SpeedModel.rallyReturnSpeed(3_500.0, paddleTravel = 80.0, config), TOLERANCE)
    }

    @Test
    fun `a long passive rally compounds up to the top speed and holds there`() {
        var speed = 360.0
        var hits = 0
        while (speed < config.maxRallySpeed) {
            val next = SpeedModel.rallyReturnSpeed(speed, paddleTravel = 0.0, config)
            assertTrue(next > speed, "every return must speed the puck up until the ceiling")
            speed = next
            hits++
        }

        assertEquals(38, hits, "ln(3200 / 360) / ln(1.06) rounds up to 38 returns")
        assertEquals(3_200.0, SpeedModel.rallyReturnSpeed(speed, paddleTravel = 0.0, config), TOLERANCE)
    }

    @Test
    fun `zero smash boost disables the smash`() {
        val noSmash = config.copy(smashBoost = 0.0)

        assertEquals(360.0 * 1.06, SpeedModel.rallyReturnSpeed(360.0, paddleTravel = 80.0, noSmash), TOLERANCE)
    }

    @Test
    fun `invalid growth never slows or breaks the puck`() {
        assertEquals(
            360.0,
            SpeedModel.rallyReturnSpeed(360.0, 0.0, config.copy(rallyGrowthPerHit = Double.NaN)),
            TOLERANCE,
        )
        assertEquals(360.0, SpeedModel.rallyReturnSpeed(360.0, 0.0, config.copy(rallyGrowthPerHit = 0.5)), TOLERANCE)
        assertEquals(
            360.0 * 1.06,
            SpeedModel.rallyReturnSpeed(360.0, Double.NaN, config),
            TOLERANCE,
            "NaN travel is no swing",
        )
    }

    @Test
    fun `stacked boosts are clamped to the maximum effective speed`() {
        val state = MviGameState(puck = MviPuck(vx = 3_200.0, vy = 0.0), speedMultiplier = 1.5)

        val multiplier = SpeedModel.effectiveMultiplier(state, turboSpeedMultiplier = 2.0)

        assertEquals(1.5, multiplier, TOLERANCE, "3200 raw * 1.5 = 4800, the effective ceiling")
        assertEquals(config.maxEffectiveSpeed, 3_200.0 * multiplier, TOLERANCE)
    }

    @Test
    fun `boosts below the ceiling are untouched`() {
        val state = MviGameState(puck = MviPuck(vx = 360.0, vy = 0.0), speedMultiplier = 1.5)

        assertEquals(3.0, SpeedModel.effectiveMultiplier(state, turboSpeedMultiplier = 2.0), TOLERANCE)
    }

    @Test
    fun `invalid turbo falls back to no turbo`() {
        val state = MviGameState(puck = MviPuck(vx = 360.0, vy = 0.0))

        assertEquals(1.0, SpeedModel.effectiveMultiplier(state, turboSpeedMultiplier = Double.NaN), TOLERANCE)
        assertEquals(1.0, SpeedModel.effectiveMultiplier(state, turboSpeedMultiplier = -2.0), TOLERANCE)
    }

    @Test
    fun `state multiplier combines the power-up boost with progressive speed`() {
        val twoMinutesIn = MviGameState(speedMultiplier = 1.5, elapsedSeconds = 120.0)

        assertEquals(1.5 * (1.0 + 2 * 0.05), SpeedModel.stateMultiplier(twoMinutesIn), TOLERANCE)
    }

    @Test
    fun `progressive speed respects its configured maximum`() {
        val crowded = MviGameState(lines = List(200) { MviLine("l$it", emptyList()) })

        assertEquals(config.maxMultiplier, SpeedModel.stateMultiplier(crowded), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}

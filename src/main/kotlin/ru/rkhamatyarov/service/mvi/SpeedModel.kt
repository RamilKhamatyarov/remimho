package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.SpeedConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Single source of truth for puck speed.
 *
 * Raw velocity lives on [MviPuck]; the on-screen speed is raw speed times a multiplier. Rallies
 * change the raw speed at each paddle return, and multipliers scale the whole match. Keeping both
 * here stops the reducer, the paddle physics and the bot from computing different speeds.
 */
internal object SpeedModel {
    /** Multiplier carried by the match state itself: power-up boost times progressive speed. */
    fun stateMultiplier(state: MviGameState): Double = state.speedMultiplier * progressiveMultiplier(state)

    /**
     * Final multiplier for one tick, clamped so the puck never travels faster on screen than
     * [SpeedConfig.maxEffectiveSpeed] however boosts stack.
     */
    fun effectiveMultiplier(
        state: MviGameState,
        turboSpeedMultiplier: Double,
    ): Double {
        val turbo = turboSpeedMultiplier.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val uncapped = stateMultiplier(state) * turbo
        val rawSpeed = hypot(state.puck.vx, state.puck.vy)
        val ceiling = state.speedConfig.maxEffectiveSpeed.positiveOr(SpeedConfig.DEFAULT_MAX_EFFECTIVE_SPEED)
        return if (rawSpeed * uncapped > ceiling) ceiling / rawSpeed else uncapped
    }

    /**
     * Raw speed after a paddle face return.
     *
     * Every return grows the speed by [SpeedConfig.rallyGrowthPerHit]; a paddle moving at contact adds
     * up to [SpeedConfig.smashBoost] more in proportion to the swing. Growth stops at
     * [SpeedConfig.maxRallySpeed], and a return never slows a puck that is already faster than that.
     */
    fun rallyReturnSpeed(
        incomingRawSpeed: Double,
        paddleTravel: Double,
        config: SpeedConfig,
    ): Double {
        if (!incomingRawSpeed.isFinite() || incomingRawSpeed <= 0.0) return incomingRawSpeed
        val ceiling = config.maxRallySpeed.positiveOr(SpeedConfig.DEFAULT_MAX_RALLY_SPEED)
        if (incomingRawSpeed >= ceiling) return incomingRawSpeed

        val growth = config.rallyGrowthPerHit.takeIf { it.isFinite() && it >= 1.0 } ?: 1.0
        val smash = config.smashBoost.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        return min(incomingRawSpeed * growth * (1.0 + smash * swing(paddleTravel)), ceiling)
    }

    /** Fraction of a full swing, from the paddle travel accumulated since the previous tick. */
    fun swing(paddleTravel: Double): Double =
        if (paddleTravel.isFinite()) (abs(paddleTravel) / FULL_SWING_TRAVEL).coerceIn(0.0, 1.0) else 0.0

    private fun progressiveMultiplier(state: MviGameState): Double {
        val config = state.speedConfig
        val timeFactor = (state.elapsedSeconds / SECONDS_PER_MINUTE) * config.timeAccelerationRate
        val levelFactor = state.lines.size * config.levelAccelerationPerLine
        return (config.baseMultiplier + timeFactor + levelFactor).coerceAtMost(config.maxMultiplier)
    }

    private fun Double.positiveOr(fallback: Double): Double = takeIf { it.isFinite() && it > 0.0 } ?: fallback

    /** Paddle travel within one tick that counts as a full smash swing. */
    private const val FULL_SWING_TRAVEL = 80.0
    private const val SECONDS_PER_MINUTE = 60.0
}

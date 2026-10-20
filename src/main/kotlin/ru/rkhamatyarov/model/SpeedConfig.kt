package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

/**
 * Puck speed rules.
 *
 * The first four values scale the whole match (progressive speed). The rally values model
 * professional table tennis: each face return multiplies the puck's raw speed by
 * [rallyGrowthPerHit], a fast paddle swing adds up to [smashBoost] on top, [maxRallySpeed] caps
 * what rallies can build, and [maxEffectiveSpeed] caps the final on-screen speed after every
 * multiplier (progressive, power-up, turbo) has been applied.
 */
@RegisterForReflection
data class SpeedConfig(
    val baseMultiplier: Double = 1.0,
    val timeAccelerationRate: Double = 0.05,
    val levelAccelerationPerLine: Double = 0.02,
    val maxMultiplier: Double = 3.0,
    val rallyGrowthPerHit: Double = DEFAULT_RALLY_GROWTH_PER_HIT,
    val smashBoost: Double = DEFAULT_SMASH_BOOST,
    val maxRallySpeed: Double = DEFAULT_MAX_RALLY_SPEED,
    val maxEffectiveSpeed: Double = DEFAULT_MAX_EFFECTIVE_SPEED,
) {
    companion object {
        const val DEFAULT_RALLY_GROWTH_PER_HIT = 1.06
        const val DEFAULT_SMASH_BOOST = 0.45
        const val DEFAULT_MAX_RALLY_SPEED = 3_200.0
        const val DEFAULT_MAX_EFFECTIVE_SPEED = 4_800.0

        /** Largest values the workshop accepts; collision must hold across this whole envelope. */
        const val MAX_ALLOWED_RALLY_SPEED = 6_000.0
        const val MAX_ALLOWED_EFFECTIVE_SPEED = 12_000.0
    }
}

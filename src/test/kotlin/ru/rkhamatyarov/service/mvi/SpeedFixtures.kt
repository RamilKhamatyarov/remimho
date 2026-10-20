package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.SpeedConfig

/** Neutral rally rules, so a test of another mechanic is not coupled to rally speed tuning. */
internal val NO_RALLY_SPEED = SpeedConfig(rallyGrowthPerHit = 1.0, smashBoost = 0.0)

/** The widest speed envelope the workshop accepts; collision stress tests must hold across all of it. */
internal val MAX_LEGAL_SPEED =
    SpeedConfig(
        timeAccelerationRate = 0.0,
        levelAccelerationPerLine = 0.0,
        maxRallySpeed = SpeedConfig.MAX_ALLOWED_RALLY_SPEED,
        maxEffectiveSpeed = SpeedConfig.MAX_ALLOWED_EFFECTIVE_SPEED,
    )

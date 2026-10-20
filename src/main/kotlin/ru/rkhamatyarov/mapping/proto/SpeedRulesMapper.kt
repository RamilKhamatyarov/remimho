package ru.rkhamatyarov.mapping.proto

import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.proto.SpeedRules

/** Speed rules travel with each history delta so a rewind restores the rules of that moment. */
fun SpeedConfig.toProto(): SpeedRules =
    SpeedRules
        .newBuilder()
        .setBaseMultiplier(baseMultiplier)
        .setTimeAccelerationRate(timeAccelerationRate)
        .setLevelAccelerationPerLine(levelAccelerationPerLine)
        .setMaxMultiplier(maxMultiplier)
        .setRallyGrowthPerHit(rallyGrowthPerHit)
        .setSmashBoost(smashBoost)
        .setMaxRallySpeed(maxRallySpeed)
        .setMaxEffectiveSpeed(maxEffectiveSpeed)
        .build()

/** Rally fields absent from the wire were written before they existed and take the defaults. */
fun SpeedRules.toDomain(): SpeedConfig =
    SpeedConfig(
        baseMultiplier = baseMultiplier,
        timeAccelerationRate = timeAccelerationRate,
        levelAccelerationPerLine = levelAccelerationPerLine,
        maxMultiplier = maxMultiplier,
        rallyGrowthPerHit = if (hasRallyGrowthPerHit()) rallyGrowthPerHit else SpeedConfig.DEFAULT_RALLY_GROWTH_PER_HIT,
        smashBoost = if (hasSmashBoost()) smashBoost else SpeedConfig.DEFAULT_SMASH_BOOST,
        maxRallySpeed = if (hasMaxRallySpeed()) maxRallySpeed else SpeedConfig.DEFAULT_MAX_RALLY_SPEED,
        maxEffectiveSpeed =
            if (hasMaxEffectiveSpeed()) maxEffectiveSpeed else SpeedConfig.DEFAULT_MAX_EFFECTIVE_SPEED,
    )

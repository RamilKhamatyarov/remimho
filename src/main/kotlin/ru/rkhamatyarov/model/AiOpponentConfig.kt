package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class AiOpponentConfig(
    val enabled: Boolean = true,
    val reactionDelayMs: Long = 180,
    val maxSpeed: Double = 180.0,
    val trackingError: Double = 10.0,
    val reactZoneRatio: Double = 0.7,
)

package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class SpeedConfig(
    val baseMultiplier: Double = 1.0,
    val timeAccelerationRate: Double = 0.05,
    val levelAccelerationPerLine: Double = 0.02,
    val maxMultiplier: Double = 3.0,
)

package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class AiOpponentConfig(
    val enabled: Boolean = true,
    val reactionDelayMs: Long = 180,
    val aimError: Double = 10.0,
    val predictionDepth: Int = 1,
    val aggression: Double = 0.35,
)

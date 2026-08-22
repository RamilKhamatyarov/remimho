package ru.rkhamatyarov.config

import kotlinx.serialization.Serializable

@Serializable
data class RuleConfig(
    val name: String = "Untitled",
    val version: Int = 1,
    val speed: RuleSpeedConfig = RuleSpeedConfig(),
    val ai: RuleAiConfig = RuleAiConfig(),
    val lines: List<RuleLineConfig> = emptyList(),
    val teleports: List<RuleTeleportConfig> = emptyList(),
)

@Serializable
data class RuleSpeedConfig(
    val baseMultiplier: Double = 1.0,
    val timeAccelerationRate: Double = 0.05,
    val levelAccelerationPerLine: Double = 0.02,
    val maxMultiplier: Double = 3.0,
)

@Serializable
data class RuleAiConfig(
    val enabled: Boolean = true,
    val reactionDelayMs: Long = 180,
    val aimError: Double = 10.0,
    val predictionDepth: Int = 1,
    val aggression: Double = 0.35,
)

@Serializable
data class RuleLineConfig(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
)

@Serializable
data class RuleTeleportConfig(
    val id: String,
    val pair: String,
)

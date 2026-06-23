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
    val maxSpeed: Double = 180.0,
    val trackingError: Double = 10.0,
    val reactZoneRatio: Double = 0.7,
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

data class CompileRequest(
    val source: String,
    val format: String = "yaml",
)

data class CompileResponse(
    val ok: Boolean,
    val config: RuleConfig? = null,
    val version: Int = 1,
    val checksum: String? = null,
    val errors: List<String> = emptyList(),
)

data class PreviewResponse(
    val ok: Boolean,
    val checksum: String? = null,
    val collisionCount: Int = 0,
    val frameTimeMs: Double = 0.0,
    val memoryBytes: Long = 0,
    val errors: List<String> = emptyList(),
)

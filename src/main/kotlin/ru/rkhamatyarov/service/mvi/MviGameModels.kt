package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig

data class MviPuck(
    val x: Double = 400.0,
    val y: Double = 300.0,
    val vx: Double = 300.0,
    val vy: Double = 200.0,
    val radius: Double = 10.0,
    val spin: Double = 0.0,
    val spinRemainingNs: Long = 0L,
    val teleportCooldownUntilNs: Long = 0L,
    val lastTeleportPairId: String? = null,
)

data class MviScore(
    val playerA: Int = 0,
    val playerB: Int = 0,
)

data class MviPoint(
    val x: Double,
    val y: Double,
)

data class MviLine(
    val id: String,
    val points: List<MviPoint>,
    val width: Double = 5.0,
    val ownerSide: PaddleSide? = null,
)

data class MviPowerUp(
    val id: String,
    val x: Double,
    val y: Double,
    val type: PowerUpType,
    val createdNs: Long,
    val lifetimeNs: Long = 15_000_000_000L,
    val radius: Double = 15.0,
)

data class MviActivePowerUp(
    val type: PowerUpType,
    val activatedNs: Long,
    val durationNs: Long,
) {
    fun isExpired(nowNs: Long): Boolean = durationNs > 0L && nowNs - activatedNs > durationNs

    fun remainingSeconds(nowNs: Long): Long = (durationNs - (nowNs - activatedNs)).coerceAtLeast(0L) / NANOS_PER_SECOND
}

/** Immutable authoritative state used by gameplay, history, and replay. */
data class MviGameState(
    val puck: MviPuck = MviPuck(),
    val score: MviScore = MviScore(),
    val paddle1Y: Double = 250.0,
    val paddle2Y: Double = 250.0,
    val paddle1Velocity: Double = 0.0,
    val paddle2Velocity: Double = 0.0,
    val paused: Boolean = false,
    val canvasWidth: Double = 800.0,
    val canvasHeight: Double = 600.0,
    val paddleHeight: Double = 100.0,
    val lines: List<MviLine> = emptyList(),
    val teleports: Map<String, String> = emptyMap(),
    val speedConfig: SpeedConfig = SpeedConfig(),
    val elapsedSeconds: Double = 0.0,
    val aiConfig: AiOpponentConfig = AiOpponentConfig(),
    val powerUps: List<MviPowerUp> = emptyList(),
    val activePowerUps: List<MviActivePowerUp> = emptyList(),
    val speedMultiplier: Double = 1.0,
    val ghostMode: Boolean = false,
    val paddleShield: Boolean = false,
    val touchLedger: TouchLedger = TouchLedger(),
    val oneTimerConfig: OneTimerConfig = OneTimerConfig(),
)

private const val NANOS_PER_SECOND = 1_000_000_000L

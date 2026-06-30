package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.SpeedConfig

sealed interface GameAction {
    data class Tick(
        val deltaSeconds: Double,
        val elapsedNs: Long = 0L,
    ) : GameAction

    data class MovePaddle(
        val y: Double,
    ) : GameAction

    data object TogglePause : GameAction

    data object Reset : GameAction

    data class CommitLine(
        val line: MviLine,
    ) : GameAction

    data class EraseLine(
        val lineId: String,
    ) : GameAction

    data object ClearLines : GameAction

    data class RestoreSnapshot(
        val state: MviGameState,
    ) : GameAction

    data class ApplyTeleports(
        val portals: Map<String, String>,
    ) : GameAction

    data class SpawnPowerUp(
        val powerUp: MviPowerUp,
    ) : GameAction

    data class ApplySpeedConfig(
        val config: SpeedConfig,
    ) : GameAction

    data class ApplyAiConfig(
        val config: AiOpponentConfig,
    ) : GameAction
}

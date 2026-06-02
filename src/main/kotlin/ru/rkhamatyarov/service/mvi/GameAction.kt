package ru.rkhamatyarov.service.mvi

sealed interface GameAction {
    data class Tick(
        val deltaSeconds: Double,
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
}

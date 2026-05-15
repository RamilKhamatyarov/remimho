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
}

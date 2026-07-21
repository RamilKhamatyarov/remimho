package ru.rkhamatyarov.service.mvi

sealed interface GameIntent {
    data class Reliable(
        val action: GameAction,
    ) : GameIntent

    data class Ephemeral(
        val event: EphemeralEvent,
    ) : GameIntent
}

sealed interface EphemeralEvent {
    data class LineDraft(
        val lineId: String,
        val x: Double,
        val y: Double,
    ) : EphemeralEvent

    data class LineFinished(
        val lineId: String,
    ) : EphemeralEvent

    data class EraseLineDraft(
        val lineId: String,
    ) : EphemeralEvent

    data class CursorMove(
        val playerId: String,
        val x: Double,
        val y: Double,
    ) : EphemeralEvent
}

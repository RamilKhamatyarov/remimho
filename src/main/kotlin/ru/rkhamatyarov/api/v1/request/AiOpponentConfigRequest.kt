package ru.rkhamatyarov.api.v1.request

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.service.RoomRegistry

data class AiOpponentConfigRequest(
    val roomId: String = RoomRegistry.DEFAULT_ROOM_ID,
    val enabled: Boolean = true,
    val reactionDelayMs: Long = 180,
    val aimError: Double = 10.0,
    val predictionDepth: Int = 1,
    val aggression: Double = 0.35,
) {
    fun toDomain(): AiOpponentConfig =
        AiOpponentConfig(
            enabled = enabled,
            reactionDelayMs = reactionDelayMs,
            aimError = aimError,
            predictionDepth = predictionDepth,
            aggression = aggression,
        )
}

package ru.rkhamatyarov.api.v1.response

import ru.rkhamatyarov.model.AiOpponentConfig

data class AiOpponentConfigResponse(
    val applied: Boolean,
    val roomId: String,
    val enabled: Boolean,
    val reactionDelayMs: Long,
    val aimError: Double,
    val predictionDepth: Int,
    val aggression: Double,
) {
    companion object {
        fun applied(
            roomId: String,
            config: AiOpponentConfig,
        ): AiOpponentConfigResponse =
            AiOpponentConfigResponse(
                applied = true,
                roomId = roomId,
                enabled = config.enabled,
                reactionDelayMs = config.reactionDelayMs,
                aimError = config.aimError,
                predictionDepth = config.predictionDepth,
                aggression = config.aggression,
            )
    }
}

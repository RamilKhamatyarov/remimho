package ru.rkhamatyarov.api.v1.request

import com.fasterxml.jackson.databind.JsonNode

data class WorkshopContentRequest(
    val type: ContentType,
    val data: JsonNode,
    val metadata: Map<String, String>,
    val version: Int = 1,
    val checksum: String? = null,
)

enum class ContentType {
    LEVEL,
    SKIN,
    THEME,
    POWERUP_SET,
    GAME_MODE,
    SPEED_CONFIG,
    AI_OPPONENT_CONFIG,
}

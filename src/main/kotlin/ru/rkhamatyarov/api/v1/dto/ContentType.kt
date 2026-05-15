package ru.rkhamatyarov.api.v1.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    LEVEL,
    SKIN,
    THEME,
    POWERUP_SET,
    GAME_MODE,
    SPEED_CONFIG,
    AI_OPPONENT_CONFIG,
}

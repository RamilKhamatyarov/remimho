package ru.rkhamatyarov.websocket.dto

data class ActivePowerUpDTO(
    val type: String,
    val emoji: String,
    val remainingSeconds: Long,
)

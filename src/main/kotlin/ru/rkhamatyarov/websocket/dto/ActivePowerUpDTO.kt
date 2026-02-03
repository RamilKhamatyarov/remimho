package ru.rkhamatyarov.websocket.dto

data class ActivePowerUpDTO(
    val type: String,
    val emoji: String,
    val description: String,
    val remainingSeconds: Int,
    val color: String,
)

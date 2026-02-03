package ru.rkhamatyarov.websocket.dto

data class PowerUpDTO(
    val x: Double,
    val y: Double,
    val type: String,
    val emoji: String,
    val color: String,
    val radius: Double,
)

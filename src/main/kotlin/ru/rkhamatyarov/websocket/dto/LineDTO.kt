package ru.rkhamatyarov.websocket.dto

data class LineDTO(
    val points: List<PointDTO>,
    val width: Double,
    val animationProgress: Double,
    val isAnimating: Boolean,
)

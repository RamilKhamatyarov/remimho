package ru.rkhamatyarov.model

data class GameState(
    val puck: Puck,
    val score: Score,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val paddleHeight: Double,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paused: Boolean,
    val lines: List<Line> = emptyList(),
)

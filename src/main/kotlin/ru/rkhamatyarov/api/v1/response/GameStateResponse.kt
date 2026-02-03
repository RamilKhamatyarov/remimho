package ru.rkhamatyarov.api.v1.response

data class GameStateResponse(
    val puckX: Double,
    val puckY: Double,
    val puckVX: Double,
    val puckVY: Double,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paddleHeight: Double,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val paused: Boolean,
    val speedMultiplier: Double,
    val linesCount: Int,
    val powerUpsCount: Int,
    val activePowerUpsCount: Int,
)

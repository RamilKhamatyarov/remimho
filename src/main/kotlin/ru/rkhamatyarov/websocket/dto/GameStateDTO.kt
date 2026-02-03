package ru.rkhamatyarov.websocket.dto

data class GameStateDTO(
    val puckX: Double,
    val puckY: Double,
    val puckVX: Double,
    val puckVY: Double,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paddleHeight: Double,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val lines: List<LineDTO>,
    val powerUps: List<PowerUpDTO>,
    val activePowerUps: List<ActivePowerUpDTO>,
    val additionalPucks: List<PuckDTO>,
    val lifeGridCells: List<CellDTO>,
    val paused: Boolean,
    val speedMultiplier: Double,
)

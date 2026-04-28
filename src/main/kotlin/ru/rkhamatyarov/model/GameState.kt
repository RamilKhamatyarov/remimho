package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
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
    val powerUps: List<PowerUp> = emptyList(),
    val activePowerUpEffects: List<ActivePowerUpEffect> = emptyList(),
)

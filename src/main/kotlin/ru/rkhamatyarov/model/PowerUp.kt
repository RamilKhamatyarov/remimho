package ru.rkhamatyarov.model

import javafx.scene.paint.Color

data class PowerUp(
    var x: Double,
    var y: Double,
    val type: PowerUpType,
    var isActive: Boolean = true,
    var creationTime: Long = System.nanoTime(),
    val lifetime: Long = 15_000_000_000L,
) {
    val radius = 15.0

    fun isExpired(): Boolean = System.nanoTime() - creationTime > lifetime

    fun getColor(): Color =
        when (type) {
            PowerUpType.SPEED_BOOST -> Color.YELLOW
            PowerUpType.MAGNET_BALL -> Color.MAGENTA
            PowerUpType.GHOST_MODE -> Color.LIGHTBLUE
            PowerUpType.MULTI_BALL -> Color.ORANGE
            PowerUpType.PADDLE_SHIELD -> Color.GREEN
        }
}

enum class PowerUpType {
    SPEED_BOOST,
    MAGNET_BALL,
    GHOST_MODE,
    MULTI_BALL,
    PADDLE_SHIELD,
}

data class ActivePowerUpEffect(
    val type: PowerUpType,
    val startTime: Long = System.nanoTime(),
    val duration: Long,
    var isActive: Boolean = true,
) {
    fun isExpired(): Boolean = System.nanoTime() - startTime > duration
}

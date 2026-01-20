package ru.rkhamatyarov.model

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

    fun getColorCode(): String = PowerUpType.getColorCode(type)
}

enum class PowerUpType(
    val description: String,
    val emoji: String,
) {
    SPEED_BOOST("⚡ Speed (+50%)", "⚡"),
    MAGNET_BALL("🧲 Magnet Ball", "🧲"),
    GHOST_MODE("👻 Ghost Mode", "👻"),
    MULTI_BALL("⚪ Multi Ball", "⚪"),
    PADDLE_SHIELD("🛡️ Paddle Shield", "🛡️"),
    ;

    companion object {
        fun getColorCode(type: PowerUpType): String =
            when (type) {
                SPEED_BOOST -> "#FFEB3B"
                MAGNET_BALL -> "#E040FB"
                GHOST_MODE -> "#40C4FF"
                MULTI_BALL -> "#FF6E40"
                PADDLE_SHIELD -> "#69F0AE"
            }

        fun getDuration(type: PowerUpType): Long =
            when (type) {
                SPEED_BOOST -> 5_000_000_000
                MAGNET_BALL -> 8_000_000_000
                GHOST_MODE -> 6_000_000_000
                MULTI_BALL -> 0L
                PADDLE_SHIELD -> 10_000_000_000
            }
    }
}

data class ActivePowerUpEffect(
    val type: PowerUpType,
    val duration: Long,
    val startTime: Long = System.nanoTime(),
    val activationTime: Long = System.nanoTime(),
) {
    val isActive: Boolean
        get() = (System.nanoTime() - startTime) < duration

    fun isExpired(): Boolean {
        if (duration == 0L) return false
        return System.nanoTime() - activationTime > duration
    }

    fun remainingTime(): Long {
        if (duration == 0L) return 0L
        val remaining = duration - (System.nanoTime() - activationTime)
        return if (remaining > 0) remaining else 0L
    }

    fun remainingPercentage(): Double {
        if (duration == 0L) return 0.0
        return (remainingTime().toDouble() / duration.toDouble()) * 100
    }
}

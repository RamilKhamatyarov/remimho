package ru.rkhamatyarov.model

data class AdditionalPuck(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val creationTime: Long = System.nanoTime(),
    val lifetime: Long = 15_000_000_000L,
) {
    fun isExpired(currentTime: Long = System.nanoTime()): Boolean = currentTime - creationTime > lifetime

    fun update(speedMultiplier: Double) {
        x += vx * speedMultiplier
        y += vy * speedMultiplier
    }
}

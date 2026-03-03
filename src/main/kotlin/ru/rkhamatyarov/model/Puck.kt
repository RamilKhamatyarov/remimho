package ru.rkhamatyarov.model

data class Puck(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val radius: Double = 10.0,
)

package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Line(
    val controlPoints: MutableList<Point> = mutableListOf(),
    var width: Double = 5.0,
    var flattenedPoints: MutableList<Point>? = null,
    var animationProgress: Double = 0.0,
    var isAnimating: Boolean = false,
)

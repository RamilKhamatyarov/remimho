package ru.rkhamatyarov.websocket.dto

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class LineDTO(
    val points: List<PointDTO>,
    val width: Double,
    val animationProgress: Double,
    val isAnimating: Boolean,
)

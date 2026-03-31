package ru.rkhamatyarov.websocket.dto

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class PointDTO(
    val x: Double,
    val y: Double,
)

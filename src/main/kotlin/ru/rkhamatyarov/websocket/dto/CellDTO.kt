package ru.rkhamatyarov.websocket.dto

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class CellDTO(
    val x: Double,
    val y: Double,
    val size: Double,
)

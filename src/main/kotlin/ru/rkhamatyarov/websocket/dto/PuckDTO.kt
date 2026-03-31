package ru.rkhamatyarov.websocket.dto

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class PuckDTO(
    val x: Double,
    val y: Double,
)

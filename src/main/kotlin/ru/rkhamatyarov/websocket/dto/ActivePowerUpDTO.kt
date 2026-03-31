package ru.rkhamatyarov.websocket.dto

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class ActivePowerUpDTO(
    val type: String,
    val emoji: String,
    val remainingSeconds: Long,
)

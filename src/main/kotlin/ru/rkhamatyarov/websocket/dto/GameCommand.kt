package ru.rkhamatyarov.websocket.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.quarkus.runtime.annotations.RegisterForReflection
import java.util.Collections.emptyMap

@RegisterForReflection
data class GameCommand(
    @field:JsonProperty("type")
    val type: String,
    @field:JsonProperty("data")
    val data: Map<String, Any?> = emptyMap(),
)

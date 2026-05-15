package ru.rkhamatyarov.api.v1.dto

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class WorkshopContentDTO(
    val type: ContentType,
    @Contextual
    val data: JsonNode,
    val metadata: Map<String, String>,
    val version: Int = 1,
    val checksum: String? = null,
)

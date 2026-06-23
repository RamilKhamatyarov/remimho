package ru.rkhamatyarov.api.v1.dto

import com.fasterxml.jackson.databind.JsonNode

data class WorkshopContentDTO(
    val type: ContentType,
    val data: JsonNode,
    val metadata: Map<String, String>,
    val version: Int = 1,
    val checksum: String? = null,
)

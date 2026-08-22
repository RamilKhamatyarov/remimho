package ru.rkhamatyarov.api.v1.response

data class PreviewResponse(
    val ok: Boolean,
    val checksum: String? = null,
    val collisionCount: Int = 0,
    val frameTimeMs: Double = 0.0,
    val memoryBytes: Long = 0,
    val errors: List<String> = emptyList(),
)

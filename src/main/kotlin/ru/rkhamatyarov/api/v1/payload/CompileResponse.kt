package ru.rkhamatyarov.api.v1.payload

import ru.rkhamatyarov.config.RuleConfig

data class CompileResponse(
    val ok: Boolean,
    val config: RuleConfig? = null,
    val version: Int = 1,
    val checksum: String? = null,
    val errors: List<String> = emptyList(),
)

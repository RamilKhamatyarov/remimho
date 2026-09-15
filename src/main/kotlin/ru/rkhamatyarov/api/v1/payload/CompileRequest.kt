package ru.rkhamatyarov.api.v1.payload

data class CompileRequest(
    val source: String,
    val format: String = "yaml",
)

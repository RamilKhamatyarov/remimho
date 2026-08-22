package ru.rkhamatyarov.api.v1.request

data class CompileRequest(
    val source: String,
    val format: String = "yaml",
)

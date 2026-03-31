package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Cell(
    val x: Double,
    val y: Double,
)

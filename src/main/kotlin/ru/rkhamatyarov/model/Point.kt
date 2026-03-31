package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Point(
    val x: Double,
    val y: Double,
)

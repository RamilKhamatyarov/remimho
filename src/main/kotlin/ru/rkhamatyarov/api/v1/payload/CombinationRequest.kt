package ru.rkhamatyarov.api.v1.payload

import io.quarkus.runtime.annotations.RegisterForReflection

/** Canonical left-to-right geometry, independent of the submitting player's side. */
@RegisterForReflection
data class CombinationRequest(
    val id: String = "",
    val lines: List<CombinationLineRequest> = emptyList(),
)

@RegisterForReflection
data class CombinationLineRequest(
    val points: List<CombinationPointRequest>,
    val width: Double = 5.0,
)

@RegisterForReflection
data class CombinationPointRequest(
    val x: Double,
    val y: Double,
)

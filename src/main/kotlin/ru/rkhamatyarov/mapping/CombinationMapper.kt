package ru.rkhamatyarov.mapping

import ru.rkhamatyarov.api.v1.payload.CombinationRequest
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.PaddleSide

/** Validates browser geometry before assigning authoritative ownership and identities. */
fun CombinationRequest.toAction(
    side: PaddleSide,
    state: MviGameState,
    batchId: String,
): GameAction.ApplyCombination {
    require(lines.size <= 32) { "A combination supports at most 32 lines" }
    require(lines.isEmpty() || id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid combination ID" }
    require(lines.sumOf { it.points.size } <= 4096) { "Too many combination points" }
    val resolved =
        lines.mapIndexed { index, line ->
            require(line.points.size in 2..256) { "Each line needs 2 to 256 points" }
            require(line.width.isFinite() && line.width in 1.0..12.0) { "Line width must be between 1 and 12" }
            val points =
                line.points.map { point ->
                    require(point.x.isFinite() && point.y.isFinite() && point.x in 0.0..1.0 && point.y in 0.0..1.0) {
                        "Combination coordinates must be between 0 and 1"
                    }
                    MviPoint(
                        x = (if (side == PaddleSide.A) point.x else 1.0 - point.x) * state.canvasWidth,
                        y = point.y * state.canvasHeight,
                    )
                }
            require(points.zipWithNext().any { (a, b) -> a != b }) { "A line must have nonzero length" }
            MviLine(
                id = "$batchId:$index",
                points = points,
                width = line.width,
                ownerSide = side,
                combinationId = id,
            )
        }
    return GameAction.ApplyCombination(side, resolved)
}

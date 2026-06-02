package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.proto.GameStateDelta
import kotlin.math.abs

data class MviPuck(
    val x: Double = 400.0,
    val y: Double = 300.0,
    val vx: Double = 300.0,
    val vy: Double = 200.0,
    val radius: Double = 10.0,
)

data class MviScore(
    val playerA: Int = 0,
    val playerB: Int = 0,
)

data class MviPoint(
    val x: Double,
    val y: Double,
)

data class MviLine(
    val id: String,
    val points: List<MviPoint>,
    val width: Double = 5.0,
)

data class MviGameState(
    val puck: MviPuck = MviPuck(),
    val score: MviScore = MviScore(),
    val paddle1Y: Double = 250.0,
    val paddle2Y: Double = 250.0,
    val paused: Boolean = false,
    val canvasWidth: Double = 800.0,
    val canvasHeight: Double = 600.0,
    val paddleHeight: Double = 100.0,
    val lines: List<MviLine> = emptyList(),
) {
    fun toDelta(): GameStateDelta =
        GameStateDelta
            .newBuilder()
            .setPuckX(puck.x)
            .setPuckY(puck.y)
            .setPuckVx(puck.vx)
            .setPuckVy(puck.vy)
            .setPaddle1Y(paddle1Y)
            .setPaddle2Y(paddle2Y)
            .setScoreA(score.playerA)
            .setScoreB(score.playerB)
            .setPaused(paused)
            .setFullState(true)
            .addAllLines(lines.map { it.toProto() })
            .build()
}

fun reduce(
    state: MviGameState,
    action: GameAction,
): MviGameState =
    when (action) {
        is GameAction.Tick -> {
            reduceTick(state, action.deltaSeconds)
        }

        is GameAction.MovePaddle -> {
            state.copy(paddle2Y = action.y.coerceIn(0.0, state.canvasHeight - state.paddleHeight))
        }

        GameAction.TogglePause -> {
            state.copy(paused = !state.paused)
        }

        GameAction.Reset -> {
            state.copy(
                puck =
                    state.puck.copy(
                        x = state.canvasWidth / 2,
                        y = state.canvasHeight / 2,
                        vx = if (state.puck.vx > 0) 300.0 else -300.0,
                        vy = 200.0,
                    ),
                paused = false,
                lines = emptyList(),
            )
        }

        is GameAction.CommitLine -> {
            if (action.line.id.isBlank()) {
                state
            } else {
                state.copy(lines = state.lines.filterNot { it.id == action.line.id } + action.line)
            }
        }

        is GameAction.EraseLine -> {
            state.copy(lines = state.lines.filterNot { it.id == action.lineId })
        }
    }

private fun reduceTick(
    state: MviGameState,
    deltaSeconds: Double,
): MviGameState {
    if (state.paused || deltaSeconds <= 0.0) return state

    var puck =
        state.puck.copy(
            x = state.puck.x + state.puck.vx * deltaSeconds,
            y = state.puck.y + state.puck.vy * deltaSeconds,
        )

    if (puck.y - puck.radius <= 0.0) {
        puck = puck.copy(y = puck.radius, vy = abs(puck.vy))
    } else if (puck.y + puck.radius >= state.canvasHeight) {
        puck = puck.copy(y = state.canvasHeight - puck.radius, vy = -abs(puck.vy))
    }

    val score =
        when {
            puck.x - puck.radius <= 0.0 -> state.score.copy(playerB = state.score.playerB + 1)
            puck.x + puck.radius >= state.canvasWidth -> state.score.copy(playerA = state.score.playerA + 1)
            else -> state.score
        }

    if (score != state.score) {
        puck =
            puck.copy(
                x = state.canvasWidth / 2,
                y = state.canvasHeight / 2,
                vx = if (puck.vx > 0) 300.0 else -300.0,
                vy = 200.0,
            )
    }

    return state.copy(puck = puck, score = score)
}

private fun MviLine.toProto(): ru.rkhamatyarov.proto.Line =
    ru.rkhamatyarov.proto.Line
        .newBuilder()
        .setId(id)
        .setWidth(width)
        .addAllPoints(points.map { it.toProto() })
        .build()

private fun MviPoint.toProto(): ru.rkhamatyarov.proto.Point =
    ru.rkhamatyarov.proto.Point
        .newBuilder()
        .setX(x)
        .setY(y)
        .build()

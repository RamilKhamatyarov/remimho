package ru.rkhamatyarov.service

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.PaddleSide
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

class BotController(
    roomId: String,
) {
    private val aimPhase = ((roomId.hashCode().toLong() and 0xffffL).toDouble() / 0xffffL) * 2.0 * PI
    private var observedConfig: AiOpponentConfig? = null
    private var nextReactionNs: Long? = null
    private var lastTickNs: Long? = null
    private var targetY: Double? = null

    fun nextMove(
        state: MviGameState,
        elapsedNs: Long,
    ): GameAction.MovePaddle? {
        val config = state.aiConfig
        if (!config.enabled || state.paused || elapsedNs < 0L) {
            reset()
            return null
        }
        if (observedConfig != config || lastTickNs?.let { elapsedNs < it } == true) {
            reset()
            observedConfig = config
        }

        val effectiveConfig = config.scaledForElapsed(elapsedNs)
        val previousTickNs = lastTickNs
        lastTickNs = elapsedNs

        val scheduledReactionNs = nextReactionNs
        if (scheduledReactionNs == null) {
            nextReactionNs = elapsedNs + effectiveConfig.reactionDelayMs.toNanoseconds()
            if (effectiveConfig.reactionDelayMs > 0L) return null
        }
        if (elapsedNs >= (nextReactionNs ?: elapsedNs)) {
            targetY = targetPaddleY(state, effectiveConfig, elapsedNs)
            nextReactionNs = elapsedNs + effectiveConfig.reactionDelayMs.toNanoseconds()
        }

        val desiredY = targetY ?: return null
        val deltaNs = (elapsedNs - (previousTickNs ?: elapsedNs)).coerceAtLeast(0L)
        if (deltaNs == 0L) return null
        val maxMovement = FIXED_PADDLE_SPEED * deltaNs / NANOS_PER_SECOND
        val difference = desiredY - state.paddle1Y
        if (abs(difference) < MIN_MOVEMENT) return null

        val nextY =
            (state.paddle1Y + sign(difference) * minOf(abs(difference), maxMovement))
                .coerceIn(0.0, state.canvasHeight - state.paddleHeight)
        if (abs(nextY - state.paddle1Y) < MIN_MOVEMENT) return null
        return GameAction.MovePaddle(nextY, PaddleSide.A)
    }

    fun reset() {
        observedConfig = null
        nextReactionNs = null
        lastTickNs = null
        targetY = null
    }

    private fun targetPaddleY(
        state: MviGameState,
        config: AiOpponentConfig,
        elapsedNs: Long,
    ): Double {
        val centerY = (state.canvasHeight - state.paddleHeight) / 2.0
        val puckTargetY =
            if (state.puck.vx < 0.0) {
                predictPuckY(state.puck, state.canvasHeight, config.predictionDepth)
            } else {
                centerY + state.paddleHeight / 2.0 + (state.puck.y - state.canvasHeight / 2.0) * config.aggression
            }
        val attackDirection = directionOrFallback(state.puck.vy, elapsedNs)
        val contactOffset = attackDirection * config.aggression * MAX_ATTACK_CONTACT_OFFSET
        val aimError = sin(elapsedNs / NANOS_PER_SECOND * AIM_ERROR_FREQUENCY + aimPhase) * config.aimError

        return (puckTargetY - state.paddleHeight / 2.0 - contactOffset * state.paddleHeight / 2.0 + aimError)
            .coerceIn(0.0, state.canvasHeight - state.paddleHeight)
    }

    private fun predictPuckY(
        puck: MviPuck,
        canvasHeight: Double,
        predictionDepth: Int,
    ): Double {
        if (predictionDepth <= 0 || puck.vx >= -MIN_HORIZONTAL_SPEED) return puck.y
        val targetX = PADDLE_WIDTH + puck.radius
        val travelSeconds = ((puck.x - targetX) / -puck.vx).coerceAtLeast(0.0)
        var predictedY = puck.y + puck.vy * travelSeconds
        val minimumY = puck.radius
        val maximumY = canvasHeight - puck.radius
        var bounces = 0
        while ((predictedY < minimumY || predictedY > maximumY) && bounces < predictionDepth) {
            predictedY =
                if (predictedY < minimumY) {
                    minimumY + (minimumY - predictedY)
                } else {
                    maximumY - (predictedY - maximumY)
                }
            bounces++
        }
        return predictedY.coerceIn(minimumY, maximumY)
    }

    private fun directionOrFallback(
        velocity: Double,
        elapsedNs: Long,
    ): Double =
        when {
            velocity > 0.0 -> 1.0
            velocity < 0.0 -> -1.0
            (elapsedNs / TIME_SCALING_STEP_NS) % 2L == 0L -> 1.0
            else -> -1.0
        }

    private fun Long.toNanoseconds(): Long = this * NANOS_PER_MILLISECOND

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val TIME_SCALING_STEP_NS = 60_000_000_000L
        private const val FIXED_PADDLE_SPEED = 260.0
        private const val PADDLE_WIDTH = 20.0
        private const val MIN_HORIZONTAL_SPEED = 0.000_001
        private const val MIN_MOVEMENT = 0.25
        private const val MAX_ATTACK_CONTACT_OFFSET = 0.55
        private const val AIM_ERROR_FREQUENCY = 2.5
    }
}

internal fun AiOpponentConfig.scaledForElapsed(elapsedNs: Long): AiOpponentConfig {
    val stages = (elapsedNs.coerceAtLeast(0L) / 60_000_000_000L).coerceAtMost(3L).toInt()
    val accuracyFactor = 1.0 - stages * 0.1
    return copy(
        reactionDelayMs = (reactionDelayMs * accuracyFactor).toLong().coerceAtLeast(0L),
        aimError = (aimError * accuracyFactor).coerceAtLeast(0.0),
        predictionDepth = (predictionDepth + if (stages >= 2) 1 else 0).coerceAtMost(4),
        aggression = (aggression + stages * 0.05).coerceIn(0.0, 1.0),
    )
}

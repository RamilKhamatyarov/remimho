package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@ApplicationScoped
class CircleFormula : Formula {
    @Inject
    lateinit var gameState: GameState

    override val name = "Circle"

    override fun createLine(): Line {
        val line = Line(width = 3.0)

        val centerX = generateRandomPosition(gameState.canvasWidth, gameState.canvasWidth / 2)
        val centerY = generateRandomPosition(gameState.canvasHeight, gameState.canvasHeight / 2)

        val minRadius = min(gameState.canvasWidth, gameState.canvasHeight) * 0.1
        val maxRadius = min(gameState.canvasWidth, gameState.canvasHeight) * 0.4

        val radius = Random.nextDouble(minRadius, maxRadius)

        val safeRadius = ensureCircleWithinBounds(centerX, centerY, radius)

        for (angle in 0..360 step 10) {
            val rad = angle * PI / 180
            val x = centerX + safeRadius * cos(rad)
            val y = centerY + safeRadius * sin(rad)
            line.controlPoints.add(Point(x, y))
        }
        return line
    }

    private fun generateRandomPosition(
        maxValue: Double,
        forbiddenValue: Double,
        tolerance: Double = 50.0,
    ): Double {
        var position: Double
        do {
            val margin = maxValue * 0.1
            position = Random.nextDouble(margin, maxValue - margin)
        } while (abs(position - forbiddenValue) < tolerance)

        return position
    }

    private fun ensureCircleWithinBounds(
        centerX: Double,
        centerY: Double,
        radius: Double,
    ): Double {
        val margin = 10.0

        val maxRadiusX =
            min(
                centerX - margin,
                gameState.canvasWidth - centerX - margin,
            )

        val maxRadiusY =
            min(
                centerY - margin,
                gameState.canvasHeight - centerY - margin,
            )

        return min(radius, min(maxRadiusX, maxRadiusY))
    }
}

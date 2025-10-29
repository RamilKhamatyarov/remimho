package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.pow

/**
 * Generates a parabolic obstacle line using the formula: y = vertexY + a * (x - vertexX)²
 *
 * Creates a downward-opening parabola with vertex at (canvas center, 70% height).
 * Points are generated with adaptive step size and filtered to stay within canvas bounds.
 *
 * @see Formula
 */
@ApplicationScoped
class ParabolaFormula : Formula {
    @Inject
    lateinit var gameState: GameState

    override val name = "Parabola"

    /**
     * Creates a parabolic line with points calculated using parabola equation.
     *
     * Step size is max(canvasWidth / 50, 10). Only points within vertical bounds are included.
     *
     * @return Line with control points forming parabola and fixed width of 3.0
     */
    override fun createLine(): Line {
        val vertexX = gameState.canvasWidth / 2
        val vertexY = gameState.canvasHeight * 0.7
        val a = 0.0001 * gameState.canvasHeight
        val step = (gameState.canvasWidth / 50).toInt().coerceAtLeast(10)

        val controlPoints =
            (0..gameState.canvasWidth.toInt() step step)
                .asSequence()
                .map { x ->
                    Point(
                        x = x.toDouble(),
                        y = vertexY + a * (x - vertexX).pow(2),
                    )
                }.filter { it.y in 0.0..gameState.canvasHeight }
                .toMutableList()

        return Line(width = 3.0, controlPoints = controlPoints)
    }
}

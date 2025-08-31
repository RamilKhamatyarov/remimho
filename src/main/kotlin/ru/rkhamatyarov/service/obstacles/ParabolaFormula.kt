package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.pow

@ApplicationScoped
class ParabolaFormula : Formula {
    @Inject
    lateinit var gameState: GameState

    override val name = "Parabola"

    override fun createLine(): Line {
        val line = Line(width = 3.0)

        val vertexX = gameState.canvasWidth / 2
        val vertexY = gameState.canvasHeight * 0.7
        val a = 0.0001 * gameState.canvasHeight

        val step = (gameState.canvasWidth / 50).toInt().coerceAtLeast(10)

        for (x in 0..gameState.canvasWidth.toInt() step step) {
            val y = vertexY + a * (x - vertexX).pow(2)

            if (y in 0.0..gameState.canvasHeight) {
                line.controlPoints.add(Point(x.toDouble(), y))
            }
        }
        return line
    }
}

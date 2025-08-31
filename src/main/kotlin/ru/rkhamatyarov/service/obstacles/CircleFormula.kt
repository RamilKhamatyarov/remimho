package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@ApplicationScoped
class CircleFormula : Formula {
    @Inject
    lateinit var gameState: GameState

    override val name = "Circle"

    override fun createLine(): Line {
        val line = Line(width = 3.0)
        val centerX = gameState.canvasWidth / 2
        val centerY = gameState.canvasHeight / 2
        val radius = min(gameState.canvasWidth, gameState.canvasHeight) * 0.3

        for (angle in 0..360 step 10) {
            val rad = angle * PI / 180
            val x = centerX + radius * cos(rad)
            val y = centerY + radius * sin(rad)
            line.controlPoints.add(Point(x, y))
        }
        return line
    }
}

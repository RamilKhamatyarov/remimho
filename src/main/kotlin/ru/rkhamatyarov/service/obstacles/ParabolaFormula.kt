package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import ru.rkhamatyarov.service.GameEngine
import kotlin.math.pow

@ApplicationScoped
class ParabolaFormula : Formula {
    @Inject
    lateinit var engine: GameEngine

    override val name = "Parabola"

    override fun createLine(): Line {
        val vertexX = engine.canvasWidth / 2
        val vertexY = engine.canvasHeight * 0.7
        val a = 0.0001 * engine.canvasHeight
        val step = (engine.canvasWidth / 50).toInt().coerceAtLeast(10)

        val controlPoints =
            (0..engine.canvasWidth.toInt() step step)
                .asSequence()
                .map { x -> Point(x = x.toDouble(), y = vertexY + a * (x - vertexX).pow(2)) }
                .filter { it.y in 0.0..engine.canvasHeight }
                .toMutableList()

        return Line(width = 3.0, controlPoints = controlPoints)
    }
}

package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.pow

@ApplicationScoped
class ParabolaFormula : Formula {
    override val name = "Parabola"

    override fun createLine(): Line {
        val line = Line(width = 3.0)
        val vertexX = 200.0
        val vertexY = 400.0
        val a = 0.01

        for (x in 100..500 step 10) {
            val y = vertexY + a * (x - vertexX).pow(2)
            line.controlPoints.add(Point(x.toDouble(), y))
        }
        return line
    }
}

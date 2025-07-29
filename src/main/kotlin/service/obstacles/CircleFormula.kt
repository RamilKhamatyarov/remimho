package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@ApplicationScoped
class CircleFormula : Formula {
    override val name = "Circle"

    override fun createLine(): Line {
        val line = Line(width = 3.0)
        val centerX = 600.0
        val centerY = 150.0
        val radius = 40.0

        for (angle in 0..360 step 10) {
            val rad = angle * PI / 180
            val x = centerX + radius * cos(rad)
            val y = centerY + radius * sin(rad)
            line.controlPoints.add(Point(x, y))
        }
        return line
    }
}

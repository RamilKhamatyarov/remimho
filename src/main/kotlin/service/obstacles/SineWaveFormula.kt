package ru.rkhamatyarov.service.obstacles

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import kotlin.math.sin

@ApplicationScoped
class SineWaveFormula : Formula {
    override val name = "Sine Wave"

    override fun createLine(): Line {
        val line = Line(width = 3.0)
        val amplitude = 30.0
        val frequency = 0.05
        val yOffset = 300.0

        for (x in 100..700 step 10) {
            val y = yOffset + amplitude * sin(frequency * (x - 100))
            line.controlPoints.add(Point(x.toDouble(), y))
        }
        return line
    }
}

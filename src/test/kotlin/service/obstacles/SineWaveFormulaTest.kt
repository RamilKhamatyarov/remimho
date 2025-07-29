package service.obstacles

import ru.rkhamatyarov.service.obstacles.SineWaveFormula
import service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class SineWaveFormulaTest : FormulaTestBase<SineWaveFormula>(SineWaveFormula()) {
    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Sine wave should have correct point count and shape",
                expectedPoints = 61, // (700-100)/10 + 1
                validation = { line ->
                    val amplitude = 30.0
                    val frequency = 0.05
                    val yOffset = 300.0

                    line.controlPoints.all { point ->
                        val expectedY = yOffset + amplitude * sin(frequency * (point.x - 100))
                        abs(point.y - expectedY) < 0.001
                    }
                },
            ),
        )

    @Test
    fun `sine wave should have correct amplitude`() {
        // g // w
        val line = formula.createLine()
        val yValues = line.controlPoints.map { it.y }
        val maxY = yValues.maxOrNull() ?: 0.0
        val minY = yValues.minOrNull() ?: 0.0
        // t
        assertEquals(30.0, (maxY - minY) / 2, 0.1)
    }
}

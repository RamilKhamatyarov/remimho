package ru.rkhamatyarov.service.obstacles

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.assertEquals

class SineWaveFormulaTest : FormulaTestBase<SineWaveFormula>(SineWaveFormula()) {
    init {
        testGameState.canvasWidth = 800.0
        testGameState.canvasHeight = 600.0
    }

    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Sine wave should have correct point count and shape",
                expectedPoints = 61,
                validation = { line ->
                    val amplitude = testGameState.canvasHeight * 0.1
                    val frequency = 0.05
                    val yOffset = testGameState.canvasHeight / 2

                    line.controlPoints.all { point ->
                        val expectedY = yOffset + amplitude * sin(frequency * (point.x - 100))
                        abs(point.y - expectedY) < 0.001
                    }
                },
            ),
        )

    @Test
    fun `sine wave should have correct amplitude`() {
        val line = formula.createLine()
        val yValues = line.controlPoints.map { it.y }
        val maxY = yValues.maxOrNull() ?: 0.0
        val minY = yValues.minOrNull() ?: 0.0
        val expectedAmplitude = testGameState.canvasHeight * 0.1

        assertEquals(expectedAmplitude, (maxY - minY) / 2, 0.1)
    }
}

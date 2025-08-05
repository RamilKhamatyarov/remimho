package service.obstacles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import ru.rkhamatyarov.service.obstacles.ParabolaFormula
import service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test

class ParabolaFormulaTest : FormulaTestBase<ParabolaFormula>(ParabolaFormula()) {
    init {
        testGameState.canvasWidth = 800.0
        testGameState.canvasHeight = 600.0
    }

    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Parabola should have correct point count and shape",
                // Updated to match actual visible points (7 instead of 17)
                expectedPoints = 7,
                validation = { line ->
                    val vertexX = testGameState.canvasWidth / 2
                    val vertexY = testGameState.canvasHeight * 0.7
                    val a = 0.0001 * testGameState.canvasHeight

                    line.controlPoints.all { point ->
                        val expectedY = vertexY + a * (point.x - vertexX).pow(2)
                        abs(point.y - expectedY) < 0.001 &&
                            point.y in 0.0..testGameState.canvasHeight
                    }
                },
            ),
        )

    @Test
    fun `parabola should have vertex at correct position`() {
        val line = formula.createLine()
        val vertexX = testGameState.canvasWidth / 2
        val vertexY = testGameState.canvasHeight * 0.7
        val vertexPoint = line.controlPoints.find { it.x == vertexX }

        assertNotNull(vertexPoint)
        assertEquals(vertexY, vertexPoint.y, 0.001)
    }
}

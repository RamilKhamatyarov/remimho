package service.obstacles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import ru.rkhamatyarov.service.obstacles.ParabolaFormula
import service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test

class ParabolaFormulaTest : FormulaTestBase<ParabolaFormula>(ParabolaFormula()) {
    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Parabola should have correct point count and shape",
                expectedPoints = 41, // (500-100)/10 + 1
                validation = { line ->
                    val vertexX = 200.0
                    val vertexY = 400.0
                    val a = 0.01

                    line.controlPoints.all { point ->
                        val expectedY = vertexY + a * (point.x - vertexX).pow(2)
                        abs(point.y - expectedY) < 0.001
                    }
                },
            ),
        )

    @Test
    fun `parabola should have vertex at correct position`() {
        // g // w
        val line = formula.createLine()
        val vertexPoint = line.controlPoints.find { it.x == 200.0 }
        // t
        assertNotNull(vertexPoint)
        assertEquals(400.0, vertexPoint.y, 0.001)
    }
}

package service.obstacles

import io.smallrye.common.constraint.Assert.assertTrue
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.obstacles.CircleFormula
import service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test

class CircleFormulaTest : FormulaTestBase<CircleFormula>(CircleFormula()) {
    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Circle should have correct point count and shape",
                expectedPoints = 37,
                validation = { line ->
                    val centerX = 600.0
                    val centerY = 150.0
                    val radius = 40.0

                    line.controlPoints.all { point ->
                        val dx = point.x - centerX
                        val dy = point.y - centerY
                        abs(sqrt(dx * dx + dy * dy) - radius) < 0.001
                    }
                },
            ),
        )

    @Test
    fun `circle should have points at cardinal directions`() {
        // g // w
        val line = formula.createLine()
        val centerX = 600.0
        val centerY = 150.0
        val radius = 40.0

        val pointsAtAngles =
            mapOf(
                0.0 to Point(centerX + radius, centerY),
                90.0 to Point(centerX, centerY + radius),
                180.0 to Point(centerX - radius, centerY),
                270.0 to Point(centerX, centerY - radius),
            )

        // t
        pointsAtAngles.values.forEach { expectedPoint ->
            assertTrue(
                line.controlPoints.any { point ->
                    abs(point.x - expectedPoint.x) < 0.001 &&
                        abs(point.y - expectedPoint.y) < 0.001
                },
            )
        }
    }
}

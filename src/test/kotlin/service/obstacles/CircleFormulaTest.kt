package service.obstacles

import org.junit.jupiter.api.Assertions.assertTrue
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.obstacles.CircleFormula
import service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test

class CircleFormulaTest : FormulaTestBase<CircleFormula>(CircleFormula()) {
    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Circle should have correct point count and shape",
                expectedPoints = 37,
                validation = { line ->
                    val centerX = testGameState.canvasWidth / 2
                    val centerY = testGameState.canvasHeight / 2
                    val radius = min(testGameState.canvasWidth, testGameState.canvasHeight) * 0.3

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
        val line = formula.createLine()
        val centerX = testGameState.canvasWidth / 2
        val centerY = testGameState.canvasHeight / 2
        val radius = min(testGameState.canvasWidth, testGameState.canvasHeight) * 0.3

        val pointsAtAngles =
            mapOf(
                0.0 to Point(centerX + radius, centerY),
                90.0 to Point(centerX, centerY + radius),
                180.0 to Point(centerX - radius, centerY),
                270.0 to Point(centerX, centerY - radius),
            )

        pointsAtAngles.values.forEach { expectedPoint ->
            assertTrue(
                line.controlPoints.any { point ->
                    abs(point.x - expectedPoint.x) < 0.001 &&
                        abs(point.y - expectedPoint.y) < 0.001
                },
                "Missing point at angle for $expectedPoint",
            )
        }
    }
}

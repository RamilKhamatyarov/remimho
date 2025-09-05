package ru.rkhamatyarov.service.obstacles

import org.junit.jupiter.api.Assertions.assertTrue
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test

class CircleFormulaTest : FormulaTestBase<CircleFormula>(CircleFormula()) {
    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Circle should have correct point count and circular shape",
                expectedPoints = 37,
                validation = { line ->
                    val centerX = line.controlPoints.map { it.x }.average()
                    val centerY = line.controlPoints.map { it.y }.average()

                    val distances =
                        line.controlPoints.map { point ->
                            val dx = point.x - centerX
                            val dy = point.y - centerY
                            sqrt(dx * dx + dy * dy)
                        }
                    val avgRadius = distances.average()

                    distances.all { distance ->
                        abs(distance - avgRadius) < 5.0
                    }
                },
            ),
        )

    @Test
    fun `circle should have points forming a circular shape`() {
        val line = formula.createLine()

        val centerX = line.controlPoints.map { it.x }.average()
        val centerY = line.controlPoints.map { it.y }.average()

        val distances =
            line.controlPoints.map { point ->
                val dx = point.x - centerX
                val dy = point.y - centerY
                sqrt(dx * dx + dy * dy)
            }
        val avgRadius = distances.average()

        assertTrue(
            distances.all { distance ->
                abs(distance - avgRadius) < 5.0
            },
            "All points should be approximately the same distance from the center",
        )

        val margin = 10.0
        line.controlPoints.forEach { point ->
            assertTrue(point.x >= margin, "Point should not be too close to left edge")
            assertTrue(point.x <= testGameState.canvasWidth - margin, "Point should not be too close to right edge")
            assertTrue(point.y >= margin, "Point should not be too close to top edge")
            assertTrue(point.y <= testGameState.canvasHeight - margin, "Point should not be too close to bottom edge")
        }

        val canvasCenterX = testGameState.canvasWidth / 2
        val canvasCenterY = testGameState.canvasHeight / 2
        val tolerance = 50.0

        assertTrue(
            abs(centerX - canvasCenterX) >= tolerance || abs(centerY - canvasCenterY) >= tolerance,
            "Circle should not be centered exactly in the middle of the canvas",
        )
    }

    @Test
    fun `circle should have points at regular angular intervals`() {
        val line = formula.createLine()

        val centerX = line.controlPoints.map { it.x }.average()
        val centerY = line.controlPoints.map { it.y }.average()

        val pointsByAngle = mutableMapOf<Int, MutableList<Point>>()

        line.controlPoints.forEach { point ->
            val dx = point.x - centerX
            val dy = point.y - centerY
            var angle = Math.toDegrees(kotlin.math.atan2(dy, dx))

            angle = (angle + 360) % 360

            val roundedAngle = ((angle + 5) / 10).toInt() * 10 % 360

            pointsByAngle.getOrPut(roundedAngle) { mutableListOf() }.add(point)
        }

        val majorAngles = listOf(0, 90, 180, 270)
        val foundAngles = mutableListOf<Int>()

        majorAngles.forEach { targetAngle ->
            val hasPointsNearAngle =
                pointsByAngle.any { (angle, points) ->
                    val angleDiff =
                        min(
                            abs(angle - targetAngle),
                            min(abs(angle - targetAngle - 360), abs(angle - targetAngle + 360)),
                        )
                    angleDiff <= 15 && points.isNotEmpty()
                }

            assertTrue(
                hasPointsNearAngle,
                "Should have points near angle $targetAngle degrees. Found angles: ${pointsByAngle.keys.sorted()}",
            )

            if (hasPointsNearAngle) {
                foundAngles.add(targetAngle)
            }
        }

        assertTrue(
            foundAngles.size >= 3,
            "Should have points in at least 3 of the 4 cardinal directions. Found: $foundAngles",
        )
    }
}

package ru.rkhamatyarov.service.obstacles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.rkhamatyarov.service.FormulaTestBase
import kotlin.math.abs
import kotlin.math.pow

class ParabolaFormulaTest : FormulaTestBase<ParabolaFormula>(ParabolaFormula()) {
    companion object {
        @JvmStatic
        fun canvasSizeProvider() =
            listOf(
                Arguments.of(800.0, 600.0),
                Arguments.of(1920.0, 1080.0),
                Arguments.of(400.0, 300.0),
                Arguments.of(1000.0, 1000.0),
                Arguments.of(100.0, 100.0),
            )

        @JvmStatic
        fun edgeCaseProvider() =
            listOf(
                Arguments.of(0.0, 0.0),
                Arguments.of(1.0, 1.0),
                Arguments.of(10000.0, 10000.0),
            )
    }

    @BeforeEach
    fun setUp() {
        testGameState.canvasWidth = 800.0
        testGameState.canvasHeight = 600.0
    }

    override fun testCases() =
        listOf(
            FormulaTestCase(
                description = "Parabola should have correct point count and shape for default size",
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
            FormulaTestCase(
                description = "Line should have valid width",
                expectedPoints = 7,
                validation = { line ->
                    line.width == 3.0
                },
            ),
        )

    @Test
    fun `parabola should have vertex at correct position`() {
        val line = formula.createLine()
        val vertexX = testGameState.canvasWidth / 2
        val vertexY = testGameState.canvasHeight * 0.7

        val vertexPoint =
            requireNotNull(
                line.controlPoints.find { it.x == vertexX },
            ) { "Vertex point should exist at center X" }

        assertEquals(vertexY, vertexPoint.y, 0.001, "Vertex Y should be at 70% of canvas height")
    }

    @Test
    fun `parabola should have correct name`() {
        assertEquals("Parabola", formula.name)
    }

    @Test
    fun `parabola should create line with valid structure`() {
        val line = formula.createLine()

        assertNotNull(line)
        assertTrue(line.controlPoints.isNotEmpty(), "Line should have control points")
        assertEquals(3.0, line.width, "Line width should be 3.0")
    }

    @Test
    fun `parabola points should be in ascending x order`() {
        val line = formula.createLine()
        val xValues = line.controlPoints.map { it.x }

        assertTrue(xValues.isNotEmpty(), "Should have x values")
        assertEquals(xValues.sorted(), xValues, "X values should be in ascending order")
    }

    @Test
    fun `all parabola points should be within canvas bounds`() {
        val line = formula.createLine()

        line.controlPoints.forEach { point ->
            assertTrue(
                point.x in 0.0..testGameState.canvasWidth,
                "Point X ${point.x} should be within canvas width [0, ${testGameState.canvasWidth}]",
            )
            assertTrue(
                point.y in 0.0..testGameState.canvasHeight,
                "Point Y ${point.y} should be within canvas height [0, ${testGameState.canvasHeight}]",
            )
        }
    }

    @Test
    fun `parabola should be symmetric around vertex`() {
        val line = formula.createLine()
        val vertexX = testGameState.canvasWidth / 2

        val pointsByX = line.controlPoints.associateBy { it.x }
        val tolerance = 0.001

        line.controlPoints.forEach { point ->
            val distanceFromVertex = point.x - vertexX
            if (distanceFromVertex != 0.0) {
                val symmetricX = vertexX - distanceFromVertex
                val symmetricPoint = pointsByX[symmetricX]

                symmetricPoint?.let {
                    assertEquals(
                        point.y,
                        it.y,
                        tolerance,
                        "Points at symmetric positions $distanceFromVertex from vertex should have same Y",
                    )
                }
            }
        }
    }

    @Test
    fun `parabola should handle minimum step size correctly`() {
        testGameState.canvasWidth = 50.0
        testGameState.canvasHeight = 50.0

        val line = formula.createLine()

        val expectedPointCount = 6
        assertEquals(
            expectedPointCount,
            line.controlPoints.size,
            "Should use minimum step of 10 for small canvas",
        )
    }

    @ParameterizedTest
    @MethodSource("canvasSizeProvider")
    fun `parabola should create valid shape for different canvas sizes`(
        width: Double,
        height: Double,
    ) {
        testGameState.canvasWidth = width
        testGameState.canvasHeight = height

        val line = formula.createLine()
        val vertexX = width / 2
        val vertexY = height * 0.7
        val a = 0.0001 * height

        assertTrue(line.controlPoints.isNotEmpty(), "Should generate points for canvas $width x $height")

        line.controlPoints.forEach { point ->
            val expectedY = vertexY + a * (point.x - vertexX).pow(2)
            assertEquals(
                expectedY,
                point.y,
                0.001,
                "Point at X ${point.x} should follow parabola equation for canvas $width x $height",
            )
        }
    }

    @ParameterizedTest
    @MethodSource("edgeCaseProvider")
    fun `parabola should handle edge case canvas sizes`(
        width: Double,
        height: Double,
    ) {
        testGameState.canvasWidth = width
        testGameState.canvasHeight = height

        val line = formula.createLine()

        assertNotNull(line)
        assertNotNull(line.controlPoints)

        if (width > 0 && height > 0) {
            assertTrue(
                line.controlPoints.isNotEmpty() || width < 10,
                "Should generate points for reasonable canvas sizes",
            )
        }
    }

    @Test
    fun `parabola should filter points outside canvas vertically`() {
        testGameState.canvasWidth = 800.0
        testGameState.canvasHeight = 100.0

        val line = formula.createLine()

        line.controlPoints.forEach { point ->
            assertTrue(
                point.y in 0.0..testGameState.canvasHeight,
                "All points should be within vertical bounds [0, ${testGameState.canvasHeight}]",
            )
        }
    }

    @Test
    fun `parabola step calculation should handle integer division correctly`() {
        testGameState.canvasWidth = 755.0
        testGameState.canvasHeight = 600.0

        val line = formula.createLine()

        assertTrue(line.controlPoints.isNotEmpty())
        assertTrue(line.controlPoints.all { it.x in 0.0..testGameState.canvasWidth })
        assertTrue(line.controlPoints.all { it.y in 0.0..testGameState.canvasHeight })
    }

    @Test
    fun `parabola should have correct a coefficient based on canvas height`() {
        testGameState.canvasHeight = 600.0
        val expectedA = 0.0001 * testGameState.canvasHeight

        val line = formula.createLine()
        val vertexX = testGameState.canvasWidth / 2
        val vertexY = testGameState.canvasHeight * 0.7

        val testPoint = line.controlPoints.find { it.x != vertexX }
        testPoint?.let { point ->
            val expectedY = vertexY + expectedA * (point.x - vertexX).pow(2)
            assertEquals(expectedY, point.y, 0.001, "Should use correct 'a' coefficient")
        }
    }

    @Test
    fun `parabola vertex should be at 70 percent of canvas height`() {
        val line = formula.createLine()
        val vertexX = testGameState.canvasWidth / 2
        val expectedVertexY = testGameState.canvasHeight * 0.7

        val vertexPoint =
            requireNotNull(
                line.controlPoints.find { it.x == vertexX },
            ) { "Vertex point should exist at center X" }

        assertEquals(
            expectedVertexY,
            vertexPoint.y,
            0.001,
            "Vertex should be at 70% of canvas height",
        )
    }
}

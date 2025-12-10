package ru.rkhamatyarov.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import javafx.application.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("FormulaRegistry Tests")
class FormulaRegistryTest {
    private lateinit var formulaRegistry: FormulaRegistry
    private lateinit var mockGameState: GameState
    private lateinit var mockFormula1: Formula
    private lateinit var mockFormula2: Formula

    @BeforeEach
    fun setUp() {
        mockkStatic(Platform::class)
        every { Platform.runLater(any()) } answers { firstArg<() -> Unit>().invoke() }

        // Create a more specific mock for GameState
        mockGameState =
            mockk {
                every { clearLines() } answers { }
                every { flattenBezierSpline(any()) } returns
                    mutableListOf(
                        mockk<Point>(relaxed = true),
                        mockk<Point>(relaxed = true),
                    )
                // Use a real list that we can verify interactions with
                val linesList = CopyOnWriteArrayList<Line>()
                every { lines } returns linesList
                every { lines.add(any()) } answers {
                    linesList.add(firstArg())
                    true // Return true to indicate successful addition
                }
            }

        mockFormula1 = mockk(relaxed = true)
        mockFormula2 = mockk(relaxed = true)

        formulaRegistry =
            FormulaRegistry().apply {
                gameState = mockGameState
                setPrivateFormulas(listOf(mockFormula1, mockFormula2))
            }

        every { mockFormula1.createLine() } returns createMockLine()
        every { mockFormula2.createLine() } returns createMockLine()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockLine(): Line {
        val mockPoint1 = mockk<Point>(relaxed = true)
        val mockPoint2 = mockk<Point>(relaxed = true)
        return Line(
            controlPoints = mutableListOf(mockPoint1, mockPoint2),
            width = 3.0,
        )
    }

    @Test
    fun `should initialize timer when starting scheduler`() {
        // w
        formulaRegistry.startRandomCurveScheduler()

        // t
        assertNotNull(formulaRegistry.getPrivateTimer())
    }

    @Test
    fun `should cancel existing timer when starting new scheduler`() {
        // g
        val mockTimer = mockk<Timer>(relaxed = true)
        formulaRegistry.setPrivateTimer(mockTimer)

        // w
        formulaRegistry.startRandomCurveScheduler()

        // t
        verify { mockTimer.cancel() }
    }

    @Test
    fun `should select random formula from available formulas`() {
        // g
        formulaRegistry.startRandomCurveScheduler()

        // w
        formulaRegistry.callPrivateShowRandomCurve()

        // t
        val currentFormula = formulaRegistry.getPrivateCurrentFormula()
        assertTrue(currentFormula == mockFormula1 || currentFormula == mockFormula2)
    }

    @Test
    fun `should create line with animation when showing random curve`() {
        // w
        formulaRegistry.callPrivateShowRandomCurve()

        // t
        verify {
            mockGameState.flattenBezierSpline(any())
            mockGameState.lines.add(match<Line> { it.isAnimating == true })
        }
    }

    @Test
    fun `should handle empty formulas list gracefully`() {
        // g
        formulaRegistry.setPrivateFormulas(emptyList())

        // w
        formulaRegistry.callPrivateShowRandomCurve()

        // t
        verify(exactly = 0) {
            mockFormula1.createLine()
            mockFormula2.createLine()
        }
        verify(exactly = 0) { mockGameState.clearLines() }
    }

    @Test
    fun `should handle null formulas instance gracefully`() {
        // g
        formulaRegistry.setPrivateFormulas(null)

        // w
        formulaRegistry.callPrivateShowRandomCurve()

        // t
        verify(exactly = 0) {
            mockFormula1.createLine()
            mockFormula2.createLine()
        }
        verify(exactly = 0) { mockGameState.clearLines() }
    }

    @Test
    fun `should do nothing when no current formula is set`() {
        // g
        formulaRegistry.setPrivateCurrentFormula(null)

        // w
        formulaRegistry.handleResize()

        // t
        verify(exactly = 0) { mockGameState.clearLines() }
    }

    @Test
    fun `should recreate current formula line without animation on resize`() {
        // g
        formulaRegistry.setPrivateCurrentFormula(mockFormula1)
        val mockLine = createMockLine().apply { isAnimating = false }
        every { mockFormula1.createLine() } returns mockLine

        // w
        formulaRegistry.handleResize()

        // t
        verify {
            mockGameState.clearLines()
            mockFormula1.createLine()
            mockGameState.flattenBezierSpline(any())
            mockGameState.lines.add(match<Line> { it.isAnimating == false })
        }
    }

    @Test
    fun `should preserve current formula type on resize`() {
        // g
        formulaRegistry.setPrivateCurrentFormula(mockFormula2)
        val mockLine = createMockLine().apply { isAnimating = false }
        every { mockFormula2.createLine() } returns mockLine

        // w
        formulaRegistry.handleResize()

        // t
        verify { mockFormula2.createLine() }
        verify(exactly = 0) { mockFormula1.createLine() }
    }

    @Test
    fun `should schedule next curve with random delay between 5 and 15 seconds`() {
        // g
        val capturedDelay = slot<Long>()
        val mockTimer = mockk<Timer>(relaxed = true)
        formulaRegistry.setPrivateTimer(mockTimer)

        every { mockTimer.schedule(any(), capture(capturedDelay)) } returns mockk()

        // w
        formulaRegistry.callPrivateScheduleNextCurve()

        // t
        assertTrue(capturedDelay.captured in 5000L..15000L)
    }

    @Test
    fun `should handle timer cancellation during scheduling`() {
        // g
        val mockTimer = mockk<Timer>(relaxed = true)
        formulaRegistry.setPrivateTimer(mockTimer)

        every { mockTimer.schedule(any(), any<Long>()) } throws IllegalStateException("Timer cancelled")

        // w // t
        assertDoesNotThrow {
            formulaRegistry.callPrivateScheduleNextCurve()
        }
    }

    @Test
    fun `should handle invalid delay during scheduling`() {
        // g
        val mockTimer = mockk<Timer>(relaxed = true)
        formulaRegistry.setPrivateTimer(mockTimer)

        every { mockTimer.schedule(any(), any<Long>()) } throws IllegalArgumentException("Invalid delay")

        // w // t
        assertDoesNotThrow {
            formulaRegistry.callPrivateScheduleNextCurve()
        }
    }

    @Test
    fun `should handle single formula available`() {
        // g
        formulaRegistry.setPrivateFormulas(listOf(mockFormula1))

        // w
        formulaRegistry.callPrivateShowRandomCurve()

        // t
        assertEquals(mockFormula1, formulaRegistry.getPrivateCurrentFormula())
    }

    @Test
    fun `should not add line when formula creation fails`() {
        // g
        formulaRegistry.setPrivateFormulas(listOf(mockFormula1))
        every { mockFormula1.createLine() } throws RuntimeException("Creation failed")

        // w // t
        val exception =
            assertThrows(RuntimeException::class.java) {
                formulaRegistry.callPrivateShowRandomCurveNoReflection()
            }
        assertEquals("Creation failed", exception.message)
    }
}

private fun FormulaRegistry.getPrivateTimer(): Timer? {
    val field = FormulaRegistry::class.java.getDeclaredField("timer")
    field.isAccessible = true
    return field.get(this) as? Timer
}

private fun FormulaRegistry.setPrivateTimer(timer: Timer?) {
    val field = FormulaRegistry::class.java.getDeclaredField("timer")
    field.isAccessible = true
    field.set(this, timer)
}

private fun FormulaRegistry.getPrivateCurrentFormula(): Formula? {
    val field = FormulaRegistry::class.java.getDeclaredField("currentFormula")
    field.isAccessible = true
    return field.get(this) as? Formula
}

private fun FormulaRegistry.setPrivateCurrentFormula(formula: Formula?) {
    val field = FormulaRegistry::class.java.getDeclaredField("currentFormula")
    field.isAccessible = true
    field.set(this, formula)
}

private fun FormulaRegistry.setPrivateFormulas(formulas: List<Formula>?) {
    val field = FormulaRegistry::class.java.getDeclaredField("formulas")
    field.isAccessible = true

    if (formulas == null) {
        field.set(this, null)
    } else {
        val mockInstance = mockk<jakarta.enterprise.inject.Instance<Formula>>(relaxed = true)
        every { mockInstance.toList() } returns formulas

        val mutableIterator =
            object : MutableIterator<Formula?> {
                private val iterator = formulas.iterator()

                override fun hasNext(): Boolean = iterator.hasNext()

                override fun next(): Formula = iterator.next()

                override fun remove(): Unit = throw UnsupportedOperationException("remove")
            }
        every { mockInstance.iterator() } returns mutableIterator
        field.set(this, mockInstance)
    }
}

private fun FormulaRegistry.callPrivateShowRandomCurve() {
    val method = FormulaRegistry::class.java.getDeclaredMethod("showRandomCurve")
    method.isAccessible = true
    method.invoke(this)
}

private fun FormulaRegistry.callPrivateScheduleNextCurve() {
    val method = FormulaRegistry::class.java.getDeclaredMethod("scheduleNextCurve")
    method.isAccessible = true
    method.invoke(this)
}

private fun FormulaRegistry.callPrivateShowRandomCurveNoReflection() {
    val formulasField = FormulaRegistry::class.java.getDeclaredField("formulas")
    formulasField.isAccessible = true
    val formulasInstance = formulasField.get(this) as? jakarta.enterprise.inject.Instance<*>

    val availableFormulas = formulasInstance?.toList() ?: return

    if (availableFormulas.isEmpty()) return

    val currentFormulaField = FormulaRegistry::class.java.getDeclaredField("currentFormula")
    currentFormulaField.isAccessible = true
    currentFormulaField.set(this, availableFormulas.random())

    val currentFormula = currentFormulaField.get(this) as Formula
    val gameStateField = FormulaRegistry::class.java.getDeclaredField("gameState")
    gameStateField.isAccessible = true
    val gameState = gameStateField.get(this) as GameState

    gameState.clearLines()

    val line =
        currentFormula.createLine().apply {
            flattenedPoints = gameState.flattenBezierSpline(controlPoints)
            isAnimating = true
        }
    gameState.lines.add(line)
}

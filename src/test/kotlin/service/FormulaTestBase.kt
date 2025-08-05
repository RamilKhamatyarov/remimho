package service

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.service.Formula
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FormulaTestBase<T : Formula>(
    val formula: T,
) {
    protected val testGameState = GameState()

    init {
        if (formula::class.java.declaredFields.any { it.name == "gameState" }) {
            formula::class.java.getDeclaredField("gameState").apply {
                isAccessible = true
                set(formula, testGameState)
            }
        }
    }

    protected abstract fun testCases(): List<FormulaTestCase>

    data class FormulaTestCase(
        val description: String,
        val expectedPoints: Int,
        val validation: (Line) -> Boolean,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    fun `should generate correct formula`(testCase: FormulaTestCase) {
        val line = formula.createLine()

        assertAll(
            { assertEquals(testCase.expectedPoints, line.controlPoints.size, "Point count mismatch") },
            { assertTrue(testCase.validation(line), "Formula validation failed") },
        )
    }
}

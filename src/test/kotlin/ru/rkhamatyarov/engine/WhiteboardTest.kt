package ru.rkhamatyarov.engine

import jakarta.enterprise.inject.Instance
import jakarta.enterprise.util.TypeLiteral
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxRobot
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start
import org.testfx.util.WaitForAsyncUtils
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameOfLifeGrid
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.service.Formula
import ru.rkhamatyarov.service.FormulaRegistry
import ru.rkhamatyarov.service.WhiteboardService
import kotlin.test.assertEquals

@ExtendWith(ApplicationExtension::class)
class WhiteboardTest {
    private lateinit var stage: Stage
    private val robot = FxRobot()

    @Suppress("UNUSED")
    @Start
    fun start(stage: Stage) {
        this.stage = stage
    }

    @Test
    fun `startGame should initialize whiteboard with correct properties`() {
        // g
        val (service, _) = init()

        // w
        robot.interact {
            service.startGame(stage)
        }
        WaitForAsyncUtils.waitForFxEvents()

        // t
        robot.interact {
            assertWhiteboardInitializedCorrectly()
        }
    }

    private fun init(): Pair<WhiteboardService, GameState> {
        val lifeGrid = GameOfLifeGrid()
        val gameState =
            GameState().apply {
                this.lifeGrid = lifeGrid
            }

        val inputHandler =
            InputHandler().apply {
                this.gameState = gameState
            }

        val gameLoop =
            GameLoop().apply {
                this.gameState = gameState
                this.inputHandler = inputHandler
                this.lifeGrid = lifeGrid
            }

        val formulaRegistry =
            FormulaRegistry().apply {
                this.gameState = gameState
                this.formulas = mockFormulas()
            }

        val service =
            WhiteboardService().apply {
                this.inputHandler = inputHandler
                this.gameLoop = gameLoop
                this.gameState = gameState
                this.formulaRegistry = formulaRegistry
            }

        return Pair(service, gameState)
    }

    private fun mockFormulas(): Instance<Formula> {
        val mockFormulas =
            listOf(
                object : Formula {
                    override val name = "test"

                    override fun createLine() =
                        Line().apply {
                            controlPoints.addAll(listOf(Point(0.0, 0.0), Point(100.0, 100.0)))
                        }
                },
            )

        return object : Instance<Formula> {
            override fun iterator(): MutableIterator<Formula?> = mockFormulas.iterator() as MutableIterator<Formula?>

            override fun get() = mockFormulas.first()

            override fun isUnsatisfied() = false

            override fun isAmbiguous() = false

            override fun destroy(instance: Formula) {}

            override fun getHandle() = throw UnsupportedOperationException()

            override fun handles() = throw UnsupportedOperationException()

            override fun select(vararg qualifiers: Annotation) = this

            @Suppress("UNCHECKED_CAST")
            override fun <U : Formula> select(
                type: Class<U>,
                vararg qualifiers: Annotation,
            ) = this as Instance<U>

            @Suppress("UNCHECKED_CAST")
            override fun <U : Formula> select(
                type: TypeLiteral<U>,
                vararg qualifiers: Annotation,
            ) = this as Instance<U>
        }
    }

    private fun assertWhiteboardInitializedCorrectly() {
        assertEquals("Whiteboard", stage.title)
        assertNotNull(stage.scene)
        assertEquals(800.0, stage.scene.width, 0.01)
        assertEquals(650.0, stage.scene.height, 0.01)
        assertEquals(Color.WHITE, stage.scene.fill)
    }
}

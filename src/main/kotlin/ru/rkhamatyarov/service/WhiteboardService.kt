package ru.rkhamatyarov.service

import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.jboss.logging.Logger
import ru.rkhamatyarov.engine.GameLoop
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameState

@Unremovable
@ApplicationScoped
class WhiteboardService {
    private val log = Logger.getLogger(javaClass.name)

    @Inject
    lateinit var inputHandler: InputHandler

    @Inject
    lateinit var gameLoop: GameLoop

    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var formulaRegistry: FormulaRegistry

    lateinit var root: VBox

    fun startGame(stage: Stage) {
        try {
            val iconStream = javaClass.getResourceAsStream("/images/logo.png")
            if (iconStream != null) {
                val icon = Image(iconStream)
                stage.icons.add(icon)
            }
        } catch (e: Exception) {
            log.error("Logo not found, using default icon;", e)
        }

        val canvas = createCanvas()
        val scene = createScene(canvas)

        gameLoop.gc = canvas.graphicsContext2D
        gameLoop.start()

        formulaRegistry.startRandomCurveScheduler()

        stage.scene = scene
        stage.title = "Whiteboard"
        stage.show()

        gameState.lifeGrid.repositionGrid(gameState.canvasWidth, gameState.canvasHeight)

        root.requestFocus()
    }

    private fun createCanvas(): Canvas =
        Canvas(800.0, 600.0).apply {
            widthProperty().addListener { _, _, newVal ->
                gameState.canvasWidth = newVal.toDouble()
                formulaRegistry.handleResize()
                gameState.lifeGrid.repositionGrid(gameState.canvasWidth, gameState.canvasHeight)
            }

            heightProperty().addListener { _, _, newVal ->
                gameState.canvasHeight = newVal.toDouble()
                formulaRegistry.handleResize()
                gameState.lifeGrid.repositionGrid(gameState.canvasWidth, gameState.canvasHeight)
            }

            setOnMouseMoved { event ->
                inputHandler.handleMouseMove(event)
            }

            setOnMousePressed { event ->
                if (event.button == MouseButton.SECONDARY) {
                    gameState.startNewLine(event.x, event.y)

                    gameLoop.gc?.let { gc ->
                        gc.stroke = Color.DARKGRAY
                        gc.lineWidth = gameState.currentLine?.width ?: 5.0
                        gc.beginPath()
                        gc.moveTo(event.x, event.y)
                        gc.lineTo(event.x, event.y)
                        gc.stroke()
                    }
                }
            }

            setOnMouseDragged { event ->
                if (event.button == MouseButton.SECONDARY && gameState.isDrawing) {
                    gameState.updateCurrentLine(event.x, event.y)
                }
            }

            setOnMouseReleased { event ->
                if (event.button == MouseButton.SECONDARY && gameState.isDrawing) {
                    gameState.finishCurrentLine()
                }
            }
        }

    private fun createControlBox(): HBox {
        val resetButton = createResetButton()
        val clearBlocksButton = createClearBlocksButton()
        val pauseButton = createPauseButton()
        val controlModeLabel = Label("Controls: Keyboard (Press M to toggle)")
        val (speedLabel, speedSlider) = createSpeedControls()
        val (thicknessLabel, thicknessSlider) = createThicknessControls()

        val buttonBox =
            HBox(
                resetButton,
                clearBlocksButton,
                pauseButton,
                controlModeLabel,
            ).apply {
                spacing = 10.0
            }

        val speedBox = VBox(speedLabel, speedSlider).apply { spacing = 5.0 }
        val thicknessBox = VBox(thicknessLabel, thicknessSlider).apply { spacing = 5.0 }

        return HBox(
            buttonBox,
            speedBox,
            thicknessBox,
        ).apply {
            spacing = 20.0
        }
    }

    private fun createResetButton(): Button =
        Button("Reset Game").apply {
            setOnAction {
                gameState.reset()
                root.requestFocus()
            }
        }

    private fun createClearBlocksButton(): Button =
        Button("Clear Drawings").apply {
            setOnAction {
                gameState.clearLines()
                gameLoop.gc?.clearRect(0.0, 0.0, 800.0, 600.0)
                gameLoop.gc?.let { gc ->
                    gameLoop.renderScore(gc, width)
                    gameLoop.renderObjects(gc, width)
                }
                root.requestFocus()
            }
        }

    private fun createPauseButton(): Button =
        Button("Pause").apply {
            setOnAction {
                gameLoop.togglePause()
                text = if (gameState.paused) "Resume" else "Pause"
                root.requestFocus()
            }
        }

    private fun createSpeedControls(): Pair<Label, Slider> {
        val speedLabel = Label("Speed: 1.0x")
        val speedSlider =
            Slider(0.1, 25.0, 1.0).apply {
                isShowTickLabels = true
                isShowTickMarks = true
                majorTickUnit = 5.0
                minorTickCount = 4
                blockIncrement = 0.5
            }

        bindSpeedSliderToLabel(speedSlider, speedLabel)

        return speedLabel to speedSlider
    }

    private fun createThicknessControls(): Pair<Label, Slider> {
        val thicknessLabel = Label("Line Thickness: 5")
        val thicknessSlider =
            Slider(1.0, 20.0, 5.0).apply {
                isShowTickLabels = true
                isShowTickMarks = true
                majorTickUnit = 5.0
                blockIncrement = 1.0
            }

        thicknessSlider.valueProperty().addListener { _, _, newValue ->
            val thickness = newValue.toDouble()
            thicknessLabel.text = "Line Thickness: ${thickness.toInt()}"
            gameState.currentLine?.width = thickness
        }

        return thicknessLabel to thicknessSlider
    }

    private fun bindSpeedSliderToLabel(
        slider: Slider,
        label: Label,
    ) {
        slider.valueProperty().addListener { _, _, newValue ->
            val speedMultiplier = newValue.toDouble()
            label.text = String.format("Speed: %.1fx", speedMultiplier)
            gameState.baseSpeedMultiplier = speedMultiplier
        }
    }

    private fun createScene(canvas: Canvas): Scene {
        val controlBox = createControlBox()
        root =
            VBox(controlBox, canvas).apply {
                spacing = 10.0
                isFocusTraversable = true
            }

        canvas.widthProperty().bind(root.widthProperty())
        canvas.heightProperty().bind(root.heightProperty().subtract(controlBox.heightProperty()))

        val scene = Scene(root, 800.0, 650.0, Color.WHITE)

        scene.setOnKeyPressed { event -> inputHandler.handleKeyPress(event) }
        scene.setOnKeyReleased { event -> inputHandler.handleKeyRelease(event) }

        return scene
    }
}

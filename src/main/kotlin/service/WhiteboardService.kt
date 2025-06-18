package ru.rkhamatyarov.service

import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import ru.rkhamatyarov.engine.GameLoop
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameState

@Unremovable
@ApplicationScoped
class WhiteboardService {
    @Inject
    lateinit var inputHandler: InputHandler

    @Inject
    lateinit var gameLoop: GameLoop

    @Inject
    lateinit var gameState: GameState

    lateinit var root: VBox

    fun startGame(stage: Stage) {
        val canvas = createCanvas()
        val scene = createScene(canvas)

        gameLoop.gc = canvas.graphicsContext2D
        gameLoop.start()

        stage.scene = scene
        stage.title = "Whiteboard"
        stage.show()

        root.requestFocus()
    }

    private fun createCanvas(): Canvas =
        Canvas(800.0, 600.0).apply {
            widthProperty().addListener { _, _, newVal ->
                gameState.canvasWidth = newVal.toDouble()
            }

            heightProperty().addListener { _, _, newVal ->
                gameState.canvasHeight = newVal.toDouble()
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

                    gameLoop.gc?.let { gc ->
                        gc.stroke = Color.DARKGRAY
                        gc.lineWidth = gameState.currentLine?.width ?: 5.0
                        val points = gameState.currentLine?.points
                        if (points != null && points.size > 1) {
                            gc.beginPath()
                            gc.moveTo(points[points.size - 2].x, points[points.size - 2].y)
                            gc.lineTo(points.last().x, points.last().y)
                            gc.stroke()
                        }
                    }
                }
            }

            setOnMouseReleased { event ->
                if (event.button == MouseButton.SECONDARY && gameState.isDrawing) {
                    gameState.finishCurrentLine()
                }
            }
        }

    private fun createControlBox(): VBox {
        val resetButton = createResetButton()
        val clearBlocksButton = createClearBlocksButton()
        val pauseButton = createPauseButton()
        val (speedLabel, speedSlider) = createSpeedControls()
        val (thicknessLabel, thicknessSlider) = createThicknessControls()

        return VBox(
            resetButton,
            clearBlocksButton,
            pauseButton,
            speedLabel,
            speedSlider,
            thicknessLabel,
            thicknessSlider,
        ).apply {
            spacing = 5.0
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
            Slider(0.5, 3.0, 1.0).apply {
                isShowTickLabels = true
                isShowTickMarks = true
                majorTickUnit = 0.5
                minorTickCount = 4
                blockIncrement = 0.1
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
            gameState.speedMultiplier = speedMultiplier
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

package ru.rkhamatyarov.service

import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.springframework.stereotype.Service
import ru.rkhamatyarov.engine.GameLoop
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameState

@Service
class WhiteboardService(
    private val inputHandler: InputHandler,
    private val gameLoop: GameLoop,
    private val gameState: GameState
) {
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

    private fun createCanvas(): Canvas = Canvas(800.0, 600.0).apply {
        setOnMousePressed { event ->
            if (event.button == MouseButton.SECONDARY) {
                gameState.startNewBlock(event.x, event.y)
            }
        }

        setOnMouseDragged { event ->
            if (event.button == MouseButton.SECONDARY) {
                gameState.updateCurrentBlock(event.x, event.y)
            }
        }

        setOnMouseReleased { event ->
            if (event.button == MouseButton.SECONDARY) {
                gameState.finishCurrentBlock()
            }
        }
    }

    private fun createControlBox(): VBox {
        val resetButton = createResetButton()
        val clearBlocksButton = createClearBlocksButton()
        val pauseButton = createPauseButton()
        val (speedLabel, speedSlider) = createSpeedControls()

        return VBox(resetButton, clearBlocksButton, pauseButton, speedLabel, speedSlider).apply {
            spacing = 5.0
        }
    }

    private fun createResetButton(): Button {
        return Button("Reset Game").apply {
            setOnAction {
                gameState.reset()
                root.requestFocus()
            }
        }
    }

    private fun createClearBlocksButton(): Button {
        return Button("Clear Blocks").apply {
            setOnAction {
                gameState.clearBlocks()
                root.requestFocus()
            }
        }
    }

    private fun createPauseButton(): Button {
        return Button("Pause").apply {
            setOnAction {
                gameLoop.togglePause()
                text = if (gameState.paused) "Resume" else "Pause"
                root.requestFocus()
            }
        }
    }

    private fun createSpeedControls(): Pair<Label, Slider> {
        val speedLabel = Label("Speed: 1.0x")
        val speedSlider = Slider(0.5, 3.0, 1.0).apply {
            isShowTickLabels = true
            isShowTickMarks = true
            majorTickUnit = 0.5
            minorTickCount = 4
            blockIncrement = 0.1
        }

        bindSpeedSliderToLabel(speedSlider, speedLabel)

        return speedLabel to speedSlider
    }

    private fun bindSpeedSliderToLabel(slider: Slider, label: Label) {
        slider.valueProperty().addListener { _, _, newValue ->
            val speedMultiplier = newValue.toDouble()
            label.text = String.format("Speed: %.1fx", speedMultiplier)
            gameState.speedMultiplier = speedMultiplier
        }
    }

    private fun createScene(canvas: Canvas): Scene {
        val controlBox = createControlBox()
        root = VBox(controlBox, canvas).apply {
            spacing = 10.0
            isFocusTraversable = true
        }

        val scene = Scene(root, 800.0, 650.0, Color.WHITE)

        scene.setOnKeyPressed { event -> inputHandler.handleKeyPress(event) }
        scene.setOnKeyReleased { event -> inputHandler.handleKeyRelease(event) }

        return scene
    }
}

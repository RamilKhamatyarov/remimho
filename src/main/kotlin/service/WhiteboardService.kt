package ru.rkhamatyarov.service

import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Slider
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

    private fun createCanvas(): Canvas = Canvas(800.0, 600.0)

    private fun createControlBox(): VBox {
        val resetButton = createResetButton()
        val (speedLabel, speedSlider) = createSpeedControls()

        return VBox(resetButton, speedLabel, speedSlider).apply {
            spacing = 5.0
        }
    }

    private fun createResetButton(): Button {
        return Button("Reset").apply {
            setOnAction {
                gameState.reset()
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

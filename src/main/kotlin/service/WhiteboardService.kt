package ru.rkhamatyarov.service

import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.layout.HBox
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

    fun startGame(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        val gc = canvas.graphicsContext2D

        val resetButton = Button("Reset").apply {
            setOnAction {
                gameState.reset()
            }
        }

        val buttonBox = HBox(resetButton).apply {
            spacing = 10.0
        }

        val root = VBox(buttonBox, canvas).apply {
            spacing = 10.0
            isFocusTraversable = true
        }

        val scene = Scene(root, 800.0, 650.0, Color.WHITE)

        scene.setOnKeyPressed { event ->
            inputHandler.handleKeyPress(event)
        }

        scene.setOnKeyReleased { event ->
            inputHandler.handleKeyRelease(event)
        }

        gameLoop.gc = gc
        gameLoop.start()

        stage.scene = scene
        stage.title = "Whiteboard Hockey"
        stage.show()

        root.requestFocus()
    }
}

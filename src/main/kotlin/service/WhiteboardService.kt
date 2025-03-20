package ru.rkhamatyarov.service

import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.springframework.stereotype.Service
import ru.rkhamatyarov.engine.GameLoop
import ru.rkhamatyarov.handler.InputHandler

@Service
class WhiteboardService(
    private val inputHandler: InputHandler,
    private val gameLoop: GameLoop
) {

    fun startGame(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        val gc = canvas.graphicsContext2D
        val scene = Scene(StackPane(canvas), 800.0, 600.0, Color.WHITE)

        scene.setOnKeyPressed { event ->
            inputHandler.handleKeyPress(event)
        }

        gameLoop.gc = gc
        gameLoop.start()

        stage.scene = scene
        stage.title = "Whiteboard Hockey"
        stage.show()
    }
}

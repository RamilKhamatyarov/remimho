package ru.rkhamatyarov.engine

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.input.KeyCode
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.springframework.stereotype.Component

@Component
class Whiteboard : Application() {
    override fun start(stage: Stage) {
        val canvas = Canvas(800.0, 600.0)
        val gc = canvas.graphicsContext2D
        val scene = Scene(StackPane(canvas), 800.0, 600.0, Color.WHITE)

        var puckX = 390.0
        var puckY = 290.0
        var puckVX = 3.0
        var puckVY = 3.0
        var paddle1Y = 250.0
        var paddle2Y = 250.0

        scene.setOnKeyPressed {
            when (it.code) {
                KeyCode.W -> paddle1Y -= 20
                KeyCode.S -> paddle1Y += 20
                KeyCode.UP -> paddle2Y -= 20
                KeyCode.DOWN -> paddle2Y += 20
                else -> {}
            }
        }

        val gameLoop = object : AnimationTimer() {
            override fun handle(now: Long) {
                gc.clearRect(0.0, 0.0, 800.0, 600.0)
                gc.fill = Color.BLACK
                gc.fillRect(20.0, paddle1Y, 10.0, 100.0)
                gc.fillRect(770.0, paddle2Y, 10.0, 100.0)
                gc.fillOval(puckX, puckY, 20.0, 20.0)

                puckX += puckVX
                puckY += puckVY

                if (puckY <= 0 || puckY >= 580) puckVY *= -1
                if ((puckX <= 30 && puckY in paddle1Y..(paddle1Y + 100)) ||
                    (puckX >= 750 && puckY in paddle2Y..(paddle2Y + 100))) puckVX *= -1
            }
        }
        gameLoop.start()
        stage.scene = scene
        stage.title = "Whiteboard Hockey"
        stage.show()
    }
}


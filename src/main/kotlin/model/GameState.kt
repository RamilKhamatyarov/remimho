package ru.rkhamatyarov.model

import org.springframework.stereotype.Component

@Component
class GameState {
    var puckX = 390.0
    var puckY = 290.0

    var puckVX = 3.0
    var puckVY = (Math.random() - 0.5) * 5

    var paddle1Y = 250.0
    var paddle2Y = 250.0

    var speedMultiplier = 1.0

    var paused = false

    val lines = mutableListOf<Line>()
    var currentLine: Line? = null
    var isDrawing = false

    data class Line(
        val points: MutableList<Point> = mutableListOf(),
        var width: Double = 5.0
    ) {
        data class Point(val x: Double, val y: Double)
    }

    fun startNewLine(x: Double, y: Double) {
        currentLine = Line().apply {
            points.add(Line.Point(x, y))
            width = 5.0
        }
        isDrawing = true
    }

    fun updateCurrentLine(x: Double, y: Double) {
        currentLine?.let {
            it.points.add(Line.Point(x, y))

            if (it.points.size > 1000) {
                it.points.removeAt(0)
            }
        }
    }

    fun finishCurrentLine() {
        currentLine?.let {
            if (it.points.size > 1) {
                lines.add(it)
            }
        }
        isDrawing = false
    }

    fun clearLines() {
        lines.clear()
        currentLine = null
        isDrawing = false
    }

    fun togglePause() {
        paused = !paused
    }

    fun reset() {
        puckX = 400.0
        puckY = 300.0

        puckVX = -3.0
        puckVY = (Math.random() - 0.5) * 5

        paddle1Y = 250.0
        paddle2Y = 250.0
    }
}

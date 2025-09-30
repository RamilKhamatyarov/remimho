package ru.rkhamatyarov.model

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.math.absoluteValue

@ApplicationScoped
class GameState {
    @Inject
    lateinit var lifeGrid: GameOfLifeGrid

    var canvasWidth = 800.0
    var canvasHeight = 600.0

    var puckX = 390.0
    var puckY = 290.0

    var puckVX = 3.0
    var puckVY = (Math.random() - 0.5) * 5

    var paddle1Y = 250.0
    var paddle2Y = 250.0

    val paddleHeight: Double
        get() = canvasHeight / 6

    var speedMultiplier = 1.0
    var baseSpeedMultiplier = 1.0
    var timeSpeedBoost = 1.0
    var puckMovingTime = 0L

    var paused = false

    val lines = mutableListOf<Line>()
    var currentLine: Line? = null
    var isDrawing = false

    fun updatePuckMovingTime(deltaTime: Long) {
        if (!paused && (puckVX.absoluteValue > 0.1 || puckVY.absoluteValue > 0.1)) {
            puckMovingTime += deltaTime

            if (puckMovingTime > 2_000_000_000L) {
                timeSpeedBoost = 2.5
            } else if (puckMovingTime > 1_000_000_000L) {
                timeSpeedBoost = 1.5
            }
        } else {
            puckMovingTime = 0L
            timeSpeedBoost = 1.0
        }

        speedMultiplier = baseSpeedMultiplier * timeSpeedBoost
    }

    fun startNewLine(
        x: Double,
        y: Double,
    ) {
        currentLine =
            Line().apply {
                controlPoints.add(Point(x, y))
                width = 5.0
                isAnimating = false
            }
        isDrawing = true
    }

    fun updateCurrentLine(
        x: Double,
        y: Double,
    ) {
        currentLine?.let {
            it.controlPoints.add(Point(x, y))
            if (it.controlPoints.size > 1000) {
                it.controlPoints.removeAt(0)
            }
        }
    }

    fun updateAnimations() {
        lines.forEach { line ->
            if (line.isAnimating) {
                line.animationProgress += 0.02
                if (line.animationProgress >= 1.0) {
                    line.animationProgress = 1.0
                    line.isAnimating = false
                }
            }
        }
    }

    fun finishCurrentLine() {
        currentLine?.let {
            if (it.controlPoints.size > 1) {
                it.flattenedPoints = flattenBezierSpline(it.controlPoints)
                it.isAnimating = true
                lines.add(it)
            }
        }
        isDrawing = false
    }

    fun flattenBezierSpline(
        controlPoints: List<Point>,
        stepsPerSegment: Int = 20,
    ): MutableList<Point> {
        val flattened = mutableListOf<Point>()
        if (controlPoints.size < 4) {
            flattened.addAll(controlPoints)
            return flattened
        }

        val tension = 0.5
        val divisor = 6 * tension

        for (i in 0 until controlPoints.size - 1) {
            val p0 = if (i == 0) controlPoints[0] else controlPoints[i - 1]
            val p1 = controlPoints[i]
            val p2 = controlPoints[i + 1]
            val p3 = if (i == controlPoints.size - 2) controlPoints.last() else controlPoints[i + 2]

            val dx1 = if (i == 0) 0.0 else (p2.x - p0.x) / divisor
            val dy1 = if (i == 0) 0.0 else (p2.y - p0.y) / divisor
            val dx2 = if (i == controlPoints.size - 2) 0.0 else (p3.x - p1.x) / divisor
            val dy2 = if (i == controlPoints.size - 2) 0.0 else (p3.y - p1.y) / divisor

            val b0 = Point(p1.x, p1.y)
            val b1 = Point(p1.x + dx1, p1.y + dy1)
            val b2 = Point(p2.x - dx2, p2.y - dy2)
            val b3 = Point(p2.x, p2.y)

            for (step in 0..stepsPerSegment) {
                val t = step.toDouble() / stepsPerSegment
                val u = 1 - t
                val x =
                    u * u * u * b0.x +
                        3 * u * u * t * b1.x +
                        3 * u * t * t * b2.x +
                        t * t * t * b3.x
                val y =
                    u * u * u * b0.y +
                        3 * u * u * t * b1.y +
                        3 * u * t * t * b2.y +
                        t * t * t * b3.y
                flattened.add(Point(x, y))
            }
        }
        return flattened
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
        puckX = canvasWidth / 2
        puckY = canvasHeight / 2

        puckVX = -3.0
        puckVY = (Math.random() - 0.5) * 5

        paddle1Y = (canvasHeight - paddleHeight) / 2
        paddle2Y = (canvasHeight - paddleHeight) / 2

        puckMovingTime = 0L
        timeSpeedBoost = 1.0
        baseSpeedMultiplier = 1.0
        speedMultiplier = 1.0

        lifeGrid.reset()
        lifeGrid.repositionGrid(canvasWidth, canvasHeight)
    }
}

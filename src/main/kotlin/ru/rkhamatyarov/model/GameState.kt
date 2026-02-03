package ru.rkhamatyarov.model

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.math.hypot

@ApplicationScoped
class GameState {
    @Inject
    lateinit var lifeGrid: GameOfLifeGrid

    var canvasWidth = 800.0
    var canvasHeight = 600.0
    var puckX = 400.0
    var puckY = 300.0
    var puckVX = 80.0
    var puckVY = 80.0
    var paddle1Y = 250.0
    var paddle2Y = 250.0

    val paddleHeight: Double
        get() = canvasHeight / 6

    var speedMultiplier = 1.0
    var baseSpeedMultiplier = 1.0
    var timeSpeedBoost = 1.0
    var powerUpSpeedMultiplier = 1.0
    var puckMovingTime = 0L
    var paused = false

    val lines = mutableListOf<Line>()
    var currentLine: Line? = null
    var isDrawing = false

    val powerUps = mutableListOf<PowerUp>()
    val activePowerUpEffects = mutableListOf<ActivePowerUpEffect>()
    val additionalPucks = mutableListOf<AdditionalPuck>()

    var isGhostMode = false
    var hasPaddleShield = false

    val puckLineCollisionCooldown = mutableMapOf<String, Long>()
    val collisionCooldownDuration = 100_000_000L
    val maxVelocityMagnitude = 150.0
    val additionalPuckMaxVelocity = 60.0

    var getTimeNs: () -> Long = { System.nanoTime() }

    private fun getLineSegmentId(
        point1: Point,
        point2: Point,
    ): String = "${point1.x.toInt()}_${point1.y.toInt()}_${point2.x.toInt()}_${point2.y.toInt()}"

    fun isLineSegmentInCooldown(
        point1: Point,
        point2: Point,
    ): Boolean {
        val segmentId = getLineSegmentId(point1, point2)
        val lastCollisionTime = puckLineCollisionCooldown[segmentId] ?: return false
        return (getTimeNs() - lastCollisionTime) < collisionCooldownDuration
    }

    fun recordLineSegmentCollision(
        point1: Point,
        point2: Point,
    ) {
        val segmentId = getLineSegmentId(point1, point2)
        puckLineCollisionCooldown[segmentId] = getTimeNs()
    }

    fun capPuckVelocity() {
        val magnitude = hypot(puckVX, puckVY)
        if (magnitude > maxVelocityMagnitude) {
            puckVX = (puckVX / magnitude) * maxVelocityMagnitude
            puckVY = (puckVY / magnitude) * maxVelocityMagnitude
        }
    }

    fun capAdditionalPuckVelocity(puck: AdditionalPuck) {
        val magnitude = hypot(puck.vx, puck.vy)
        if (magnitude > additionalPuckMaxVelocity) {
            puck.vx = (puck.vx / magnitude) * additionalPuckMaxVelocity
            puck.vy = (puck.vy / magnitude) * additionalPuckMaxVelocity
        }
    }

    fun cleanupCollisionCooldowns() {
        val now = getTimeNs()
        puckLineCollisionCooldown.entries.removeAll { (_, time) ->
            (now - time) > collisionCooldownDuration * 2
        }
    }

    fun updatePuckMovingTime() {
        speedMultiplier = 1.0
    }

    fun updateAdditionalPucks() {
        additionalPucks.forEach { puck ->
            puck.update(speedMultiplier * 1.5)
            capAdditionalPuckVelocity(puck)
            if (puck.x <= 10 || puck.x >= canvasWidth - 10) {
                puck.vx = -puck.vx
            }
            if (puck.y <= 10 || puck.y >= canvasHeight - 10) {
                puck.vy = -puck.vy
            }
        }
        additionalPucks.removeAll { it.isExpired() }
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
                line.animationProgress += 0.05
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
        stepsPerSegment: Int = 15,
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
                val x = u * u * u * b0.x + 3 * u * u * t * b1.x + 3 * u * t * t * b2.x + t * t * t * b3.x
                val y = u * u * u * b0.y + 3 * u * u * t * b1.y + 3 * u * t * t * b2.y + t * t * t * b3.y
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
        puckVX = 80.0
        puckVY = 80.0
        paddle1Y = (canvasHeight - paddleHeight) / 2
        paddle2Y = (canvasHeight - paddleHeight) / 2
        puckMovingTime = 0L
        timeSpeedBoost = 1.0
        baseSpeedMultiplier = 1.0
        powerUpSpeedMultiplier = 1.0
        speedMultiplier = 1.0
        powerUps.clear()
        activePowerUpEffects.clear()
        additionalPucks.clear()
        isGhostMode = false
        hasPaddleShield = false
        puckLineCollisionCooldown.clear()
        lifeGrid.reset()
        lifeGrid.repositionGrid(canvasWidth, canvasHeight)
    }
}

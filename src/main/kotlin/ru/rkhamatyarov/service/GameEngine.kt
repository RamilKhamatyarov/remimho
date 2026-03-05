package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

@ApplicationScoped
class GameEngine {
    val canvasWidth = 800.0
    val canvasHeight = 600.0
    val paddleWidth = 20.0
    val paddleHeight get() = canvasHeight / 6

    val puck = Puck(x = canvasWidth / 2, y = canvasHeight / 2, vx = 250.0, vy = 180.0)
    val score = Score()

    var paddle1Y = (canvasHeight - paddleHeight) / 2
    var paddle2Y = (canvasHeight - paddleHeight) / 2
    var paused = false

    val lines = CopyOnWriteArrayList<Line>()
    var currentLine: Line? = null
    var isDrawing = false

    private val segmentCooldown = mutableMapOf<String, Long>()
    private val cooldownDurationNs = 100_000_000L
    var getTimeNs: () -> Long = { System.nanoTime() }

    fun tick(deltaSeconds: Double) {
        if (paused) return
        movePuck(deltaSeconds)
        handleWallCollisions()
        handlePaddleCollisions()
        handleLineCollisions()
        handleGoals()
        updateAI(deltaSeconds)
        cleanupCooldowns()
    }

    private fun movePuck(dt: Double) {
        puck.x += puck.vx * dt
        puck.y += puck.vy * dt
    }

    private fun handleWallCollisions() {
        val r = puck.radius
        if (puck.y - r <= 0.0 && puck.vy < 0.0) {
            puck.y = r
            puck.vy = abs(puck.vy)
        }
        if (puck.y + r >= canvasHeight && puck.vy > 0.0) {
            puck.y = canvasHeight - r
            puck.vy = -abs(puck.vy)
        }
    }

    private fun handlePaddleCollisions() {
        val r = puck.radius
        val clearX = paddleWidth + r + 2.0

        if (puck.vx < 0.0 && puck.x <= paddleWidth + r &&
            puck.y >= paddle1Y && puck.y <= paddle1Y + paddleHeight
        ) {
            puck.x = clearX
            val speed = hypot(puck.vx, puck.vy)
            val rel = (puck.y - paddle1Y) / paddleHeight
            puck.vx = abs(puck.vx) * 1.05
            puck.vy = (rel - 0.5) * 1.5 * speed
            capVelocity()
        }

        if (puck.vx > 0.0 && puck.x >= canvasWidth - paddleWidth - r &&
            puck.y >= paddle2Y && puck.y <= paddle2Y + paddleHeight
        ) {
            puck.x = canvasWidth - clearX
            val speed = hypot(puck.vx, puck.vy)
            val rel = (puck.y - paddle2Y) / paddleHeight
            puck.vx = -abs(puck.vx) * 1.05
            puck.vy = (rel - 0.5) * 1.5 * speed
            capVelocity()
        }
    }

    private fun handleLineCollisions() {
        val r = puck.radius
        for (line in lines) {
            val pts =
                line.flattenedPoints?.takeIf { it.size >= 2 }
                    ?: line.controlPoints.takeIf { it.size >= 2 }
                    ?: continue

            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                if (isSegmentCooling(a, b)) continue

                val info = segmentInfo(puck.x, puck.y, a, b)
                val threshold = r + line.width / 2.0
                if (info.dist < threshold) {
                    val overlap = threshold - info.dist
                    puck.x += info.nx * overlap
                    puck.y += info.ny * overlap

                    val dot = puck.vx * info.nx + puck.vy * info.ny

                    puck.vx -= 2.0 * dot * info.nx
                    puck.vy -= 2.0 * dot * info.ny
                    capVelocity()
                    recordCooldown(a, b)
                    break
                }
            }
        }
    }

    private fun handleGoals() {
        val r = puck.radius
        if (puck.x - r <= 0.0) {
            score.playerB++
            resetPuck()
        }
        if (puck.x + r >= canvasWidth) {
            score.playerA++
            resetPuck()
        }
    }

    fun resetPuck() {
        puck.x = canvasWidth / 2
        puck.y = canvasHeight / 2
        puck.vx = if ((score.playerA + score.playerB) % 2 == 0) 250.0 else -250.0
        puck.vy = 180.0
    }

    private fun updateAI(dt: Double) {
        val target = puck.y - paddleHeight / 2
        val diff = target - paddle1Y
        if (abs(diff) > 2.0) paddle1Y += Math.signum(diff) * 300.0 * dt
        paddle1Y = paddle1Y.coerceIn(0.0, canvasHeight - paddleHeight)
    }

    fun startNewLine(
        x: Double,
        y: Double,
    ) {
        currentLine =
            Line().apply {
                controlPoints.add(Point(x, y))
                width = 5.0
            }
        isDrawing = true
    }

    fun updateCurrentLine(
        x: Double,
        y: Double,
    ) {
        currentLine?.let {
            it.controlPoints.add(Point(x, y))
            if (it.controlPoints.size > 1000) it.controlPoints.removeAt(0)
        }
    }

    fun finishCurrentLine() {
        currentLine?.let {
            if (it.controlPoints.size > 1) {
                it.flattenedPoints = flattenBezierSpline(it.controlPoints)
                lines.add(it)
            }
        }
        currentLine = null
        isDrawing = false
    }

    fun clearLines() {
        lines.clear()
        currentLine = null
        isDrawing = false
    }

    fun movePaddle2(y: Double) {
        paddle2Y = y.coerceIn(0.0, canvasHeight - paddleHeight)
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
        val divisor = 6 * 0.5
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
                flattened.add(
                    Point(
                        u * u * u * b0.x + 3 * u * u * t * b1.x + 3 * u * t * t * b2.x + t * t * t * b3.x,
                        u * u * u * b0.y + 3 * u * u * t * b1.y + 3 * u * t * t * b2.y + t * t * t * b3.y,
                    ),
                )
            }
        }
        return flattened
    }

    private fun capVelocity(max: Double = 500.0) {
        val mag = hypot(puck.vx, puck.vy)
        if (mag > max) {
            puck.vx = puck.vx / mag * max
            puck.vy = puck.vy / mag * max
        }
    }

    private fun segmentKey(
        a: Point,
        b: Point,
    ) = "${a.x.toInt()}_${a.y.toInt()}_${b.x.toInt()}_${b.y.toInt()}"

    private fun isSegmentCooling(
        a: Point,
        b: Point,
    ) = segmentCooldown[segmentKey(a, b)]?.let { (getTimeNs() - it) < cooldownDurationNs } ?: false

    private fun recordCooldown(
        a: Point,
        b: Point,
    ) {
        segmentCooldown[segmentKey(a, b)] = getTimeNs()
    }

    private fun cleanupCooldowns() {
        val now = getTimeNs()
        segmentCooldown.entries.removeAll { (_, t) -> (now - t) > cooldownDurationNs * 2 }
    }

    private data class SegInfo(
        val dist: Double,
        val nx: Double,
        val ny: Double,
        val cx: Double,
        val cy: Double,
    )

    private fun segmentInfo(
        px: Double,
        py: Double,
        a: Point,
        b: Point,
    ): SegInfo {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        val tc = if (len2 == 0.0) 0.0 else ((px - a.x) * abx + (py - a.y) * aby / len2).coerceIn(0.0, 1.0)
        val cx = a.x + tc * abx
        val cy = a.y + tc * aby
        val dx = px - cx
        val dy = py - cy
        val dist = sqrt(dx * dx + dy * dy)
        val (nx, ny) = if (dist < 1e-9) Pair(0.0, -1.0) else Pair(dx / dist, dy / dist)
        return SegInfo(dist, nx, ny, cx, cy)
    }
}

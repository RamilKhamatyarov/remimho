package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import kotlin.math.abs
import kotlin.math.hypot

@ApplicationScoped
class GameEngine {
    val canvasWidth: Double = 800.0
    val canvasHeight: Double = 600.0

    val paddleHeight: Double = 100.0
    private val paddleWidth: Double = 20.0
    private val paddleSpeed: Double = 400.0

    var paddle1Y: Double = (canvasHeight - paddleHeight) / 2
    var paddle2Y: Double = (canvasHeight - paddleHeight) / 2

    val puck: Puck =
        Puck(
            x = canvasWidth / 2,
            y = canvasHeight / 2,
            vx = 300.0,
            vy = 200.0,
            radius = 10.0,
        )

    val score: Score = Score(playerA = 0, playerB = 0)

    var paused: Boolean = false

    val lines: MutableList<Line> = mutableListOf()
    private var currentLine: Line? = null

    val powerUps: MutableList<PowerUp> = mutableListOf()
    val activePowerUpEffects: MutableList<ActivePowerUpEffect> = mutableListOf()
    val additionalPucks: MutableList<AdditionalPuck> = mutableListOf()
    var powerUpSpeedMultiplier: Double = 1.0
    var isGhostMode: Boolean = false
    var hasPaddleShield: Boolean = false

    fun tick(deltaSeconds: Double) {
        if (paused) return

        val speed = powerUpSpeedMultiplier

        puck.x += puck.vx * speed * deltaSeconds
        puck.y += puck.vy * speed * deltaSeconds

        if (puck.y - puck.radius <= 0) {
            puck.y = puck.radius
            puck.vy = abs(puck.vy)
        } else if (puck.y + puck.radius >= canvasHeight) {
            puck.y = canvasHeight - puck.radius
            puck.vy = -abs(puck.vy)
        }

        if (puck.x - puck.radius <= paddleWidth &&
            puck.y >= paddle1Y &&
            puck.y <= paddle1Y + paddleHeight
        ) {
            puck.x = paddleWidth + puck.radius
            puck.vx = abs(puck.vx)
        }

        if (puck.x + puck.radius >= canvasWidth - paddleWidth &&
            puck.y >= paddle2Y &&
            puck.y <= paddle2Y + paddleHeight
        ) {
            puck.x = canvasWidth - paddleWidth - puck.radius
            puck.vx = -abs(puck.vx)
        }

        if (puck.x - puck.radius <= 0) {
            score.playerB++
            resetPuck()
        } else if (puck.x + puck.radius >= canvasWidth) {
            score.playerA++
            resetPuck()
        }

        val aiCenter = paddle1Y + paddleHeight / 2
        if (puck.y > aiCenter + 5) {
            paddle1Y = minOf(paddle1Y + paddleSpeed * deltaSeconds, canvasHeight - paddleHeight)
        } else if (puck.y < aiCenter - 5) {
            paddle1Y = maxOf(paddle1Y - paddleSpeed * deltaSeconds, 0.0)
        }

        deflectOffLines()
    }

    private fun deflectOffLines() {
        for (line in lines) {
            val pts = line.flattenedPoints ?: line.controlPoints
            if (pts.size < 2) continue
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                if (segmentCircleCollision(a, b, puck)) {
                    val nx = -(b.y - a.y)
                    val ny = (b.x - a.x)
                    val len = hypot(nx, ny)
                    if (len < 1e-9) continue
                    val nnx = nx / len
                    val nny = ny / len
                    val dot = puck.vx * nnx + puck.vy * nny
                    puck.vx -= 2 * dot * nnx
                    puck.vy -= 2 * dot * nny
                    return
                }
            }
        }
    }

    private fun segmentCircleCollision(
        a: Point,
        b: Point,
        p: Puck,
    ): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val fx = a.x - p.x
        val fy = a.y - p.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) return false
        val t = ((-fx * dx - fy * dy) / lenSq).coerceIn(0.0, 1.0)
        val closestX = a.x + t * dx - p.x
        val closestY = a.y + t * dy - p.y
        return closestX * closestX + closestY * closestY <= p.radius * p.radius
    }

    fun movePaddle2(y: Double) {
        paddle2Y = y.coerceIn(0.0, canvasHeight - paddleHeight)
    }

    fun resetPuck() {
        puck.x = canvasWidth / 2
        puck.y = canvasHeight / 2
        puck.vx = if (puck.vx > 0) 300.0 else -300.0
        puck.vy = 200.0
    }

    fun startNewLine(
        x: Double,
        y: Double,
    ) {
        val line = Line()
        line.controlPoints.add(Point(x, y))
        currentLine = line
        lines.add(line)
    }

    fun updateCurrentLine(
        x: Double,
        y: Double,
    ) {
        currentLine?.controlPoints?.add(Point(x, y))
    }

    fun finishCurrentLine() {
        currentLine?.let { line ->
            line.flattenedPoints = flattenPolyline(line.controlPoints)
        }
        currentLine = null
    }

    fun clearLines() {
        lines.clear()
        currentLine = null
    }

    fun flattenBezierSpline(controlPoints: MutableList<Point>): MutableList<Point> = flattenPolyline(controlPoints)

    private fun flattenPolyline(pts: List<Point>): MutableList<Point> {
        if (pts.size < 2) return pts.toMutableList()
        val result = mutableListOf<Point>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val dist = hypot(b.x - a.x, b.y - a.y)
            val steps = maxOf(1, (dist / 5).toInt())
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                result.add(Point(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)))
            }
        }
        result.add(pts.last())
        return result
    }
}

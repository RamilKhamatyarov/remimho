package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import ru.rkhamatyarov.proto.GameStateDelta
import kotlin.math.abs
import kotlin.math.hypot

@ApplicationScoped
class GameEngine {
    val canvasWidth: Double = 800.0
    val canvasHeight: Double = 600.0

    val paddleHeight: Double = 100.0
    private val paddleWidth: Double = 20.0

    private val aiMaxSpeed: Double = 160.0

    private val aiReactZone: Double = canvasWidth * 0.65

    private val aiInaccuracy: Double = 12.0

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

    @Inject
    lateinit var powerUpManager: PowerUpManager

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
            puck.y >= paddle1Y && puck.y <= paddle1Y + paddleHeight
        ) {
            puck.x = paddleWidth + puck.radius
            puck.vx = abs(puck.vx)
        }
        if (puck.x + puck.radius >= canvasWidth - paddleWidth &&
            puck.y >= paddle2Y && puck.y <= paddle2Y + paddleHeight
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

        updateAI(deltaSeconds)

        deflectOffLines()

        powerUpManager.update(deltaSeconds)
    }

    private fun updateAI(dt: Double) {
        val shouldReact = puck.vx < 0 && puck.x < aiReactZone

        val targetY =
            if (shouldReact) {
                puck.y - paddleHeight / 2 + aiInaccuracy
            } else {
                (canvasHeight - paddleHeight) / 2
            }

        val diff = targetY - paddle1Y
        if (abs(diff) > 4.0) {
            val move = Math.signum(diff) * aiMaxSpeed * dt
            paddle1Y = (paddle1Y + move).coerceIn(0.0, canvasHeight - paddleHeight)
        }
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
        currentLine?.let { it.flattenedPoints = flattenPolyline(it.controlPoints) }
        currentLine = null
    }

    fun clearLines() {
        lines.clear()
        currentLine = null
    }

    fun toGameStateDelta(nowNs: Long = System.nanoTime()): GameStateDelta {
        val builder =
            GameStateDelta
                .newBuilder()
                .setPuckX(puck.x)
                .setPuckY(puck.y)
                .setPuckVx(puck.vx)
                .setPuckVy(puck.vy)
                .setPaddle1Y(paddle1Y)
                .setPaddle2Y(paddle2Y)
                .setScoreA(score.playerA)
                .setScoreB(score.playerB)
                .setPaused(paused)
                .setFullState(true)

        lines.forEach { line ->
            val lb =
                ru.rkhamatyarov.proto.Line
                    .newBuilder()
                    .setWidth(line.width)
                    .setAnimationProgress(line.animationProgress)
                    .setIsAnimating(line.isAnimating)
            (line.flattenedPoints ?: line.controlPoints).forEach { pt ->
                lb.addPoints(
                    ru.rkhamatyarov.proto.Point
                        .newBuilder()
                        .setX(pt.x)
                        .setY(pt.y),
                )
            }
            builder.addLines(lb)
        }

        powerUps.filter { it.isActive }.forEach { pu ->
            builder.addPowerUps(
                ru.rkhamatyarov.proto.PowerUp
                    .newBuilder()
                    .setX(pu.x)
                    .setY(pu.y)
                    .setRadius(pu.radius)
                    .setType(pu.type.name)
                    .setEmoji(pu.type.emoji)
                    .setColor(PowerUpType.getColorCode(pu.type)),
            )
        }

        activePowerUpEffects.filter { !it.isExpired() }.forEach { eff ->
            val remaining = ((eff.duration - (nowNs - eff.activationTime)) / 1_000_000_000).coerceAtLeast(0)
            builder.addActivePowerUps(
                ru.rkhamatyarov.proto.ActivePowerUp
                    .newBuilder()
                    .setType(eff.type.name)
                    .setEmoji(eff.type.emoji)
                    .setRemainingSeconds(remaining),
            )
        }

        return builder.build()
    }

    fun restoreFromDelta(
        snapshot: GameStateDelta,
        nowNs: Long = System.nanoTime(),
    ) {
        if (snapshot.hasPuckX()) puck.x = snapshot.puckX
        if (snapshot.hasPuckY()) puck.y = snapshot.puckY
        if (snapshot.hasPuckVx()) puck.vx = snapshot.puckVx
        if (snapshot.hasPuckVy()) puck.vy = snapshot.puckVy
        if (snapshot.hasPaddle1Y()) paddle1Y = snapshot.paddle1Y.coerceIn(0.0, canvasHeight - paddleHeight)
        if (snapshot.hasPaddle2Y()) paddle2Y = snapshot.paddle2Y.coerceIn(0.0, canvasHeight - paddleHeight)
        if (snapshot.hasScoreA()) score.playerA = snapshot.scoreA
        if (snapshot.hasScoreB()) score.playerB = snapshot.scoreB
        if (snapshot.hasPaused()) paused = snapshot.paused

        lines.clear()
        snapshot.linesList.forEach { protoLine ->
            val points = protoLine.pointsList.map { Point(it.x, it.y) }.toMutableList()
            lines.add(
                Line(
                    controlPoints = points.toMutableList(),
                    width = protoLine.width,
                    flattenedPoints = points.toMutableList(),
                    animationProgress = protoLine.animationProgress,
                    isAnimating = protoLine.isAnimating,
                ),
            )
        }
        currentLine = null

        powerUps.clear()
        snapshot.powerUpsList.forEach { protoPowerUp ->
            val type = runCatching { PowerUpType.valueOf(protoPowerUp.type) }.getOrNull()
            if (type != null) {
                powerUps.add(PowerUp(x = protoPowerUp.x, y = protoPowerUp.y, type = type))
            }
        }

        activePowerUpEffects.clear()
        snapshot.activePowerUpsList.forEach { protoEffect ->
            val type = runCatching { PowerUpType.valueOf(protoEffect.type) }.getOrNull() ?: return@forEach
            val duration = PowerUpType.getDuration(type)
            val remainingNs = protoEffect.remainingSeconds.coerceAtLeast(0L) * 1_000_000_000L
            val activationTime =
                if (duration == 0L) {
                    nowNs
                } else {
                    nowNs - (duration - remainingNs).coerceIn(0L, duration)
                }
            activePowerUpEffects.add(
                ActivePowerUpEffect(
                    type = type,
                    duration = duration,
                    activationTime = activationTime,
                ),
            )
        }

        additionalPucks.clear()
        powerUpSpeedMultiplier = if (activePowerUpEffects.any { it.type == PowerUpType.SPEED_BOOST }) 1.5 else 1.0
        isGhostMode = activePowerUpEffects.any { it.type == PowerUpType.GHOST_MODE }
        hasPaddleShield = activePowerUpEffects.any { it.type == PowerUpType.PADDLE_SHIELD }
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

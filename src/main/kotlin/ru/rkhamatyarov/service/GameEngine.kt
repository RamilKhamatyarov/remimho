package ru.example.game.service

import jakarta.enterprise.context.ApplicationScoped
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import kotlin.math.abs

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

    fun tick(deltaSeconds: Double) {
        if (paused) return

        movePuck(deltaSeconds)
        handleWallCollisions()
        handlePaddleCollisions()
        handleGoals()
        updateAI(deltaSeconds)
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

        if (puck.vx < 0.0 &&
            puck.x <= paddleWidth + r &&
            puck.y >= paddle1Y &&
            puck.y <= paddle1Y + paddleHeight
        ) {
            puck.x = clearX
            val speed = Math.hypot(puck.vx, puck.vy)
            val rel = (puck.y - paddle1Y) / paddleHeight
            puck.vx = abs(puck.vx) * 1.05
            puck.vy = (rel - 0.5) * 1.5 * speed
            capVelocity()
        }

        if (puck.vx > 0.0 &&
            puck.x >= canvasWidth - paddleWidth - r &&
            puck.y >= paddle2Y &&
            puck.y <= paddle2Y + paddleHeight
        ) {
            puck.x = canvasWidth - clearX
            val speed = Math.hypot(puck.vx, puck.vy)
            val rel = (puck.y - paddle2Y) / paddleHeight
            puck.vx = -abs(puck.vx) * 1.05
            puck.vy = (rel - 0.5) * 1.5 * speed
            capVelocity()
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
        puck.vx = if (score.playerA + score.playerB % 2 == 0) 250.0 else -250.0
        puck.vy = 180.0
    }

    private fun updateAI(dt: Double) {
        val aiSpeed = 300.0
        val target = puck.y - paddleHeight / 2
        val diff = target - paddle1Y
        if (Math.abs(diff) > 2.0) {
            paddle1Y += Math.signum(diff) * aiSpeed * dt
        }
        paddle1Y = paddle1Y.coerceIn(0.0, canvasHeight - paddleHeight)
    }

    private fun capVelocity(max: Double = 500.0) {
        val mag = Math.hypot(puck.vx, puck.vy)
        if (mag > max) {
            puck.vx = puck.vx / mag * max
            puck.vy = puck.vy / mag * max
        }
    }

    fun movePaddle2(y: Double) {
        paddle2Y = y.coerceIn(0.0, canvasHeight - paddleHeight)
    }
}

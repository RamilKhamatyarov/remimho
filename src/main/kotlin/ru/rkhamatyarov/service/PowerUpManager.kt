package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

@ApplicationScoped
class PowerUpManager {
    @Inject
    lateinit var engine: GameEngine

    private var lastSpawnTime = 0L

    fun update(deltaTime: Double) {
        val nowNs = System.nanoTime()
        if (nowNs - lastSpawnTime > SPAWN_INTERVAL_NS) {
            spawnRandomPowerUp()
            lastSpawnTime = nowNs
        }

        engine.powerUps.removeAll { it.isExpired() }

        engine.activePowerUpEffects.removeAll { effect ->
            if (effect.isExpired()) {
                deactivatePowerUpEffect(effect.type)
                true
            } else {
                false
            }
        }

        applyActivePowerUpEffects()
        checkPowerUpCollisions()
    }

    private fun spawnRandomPowerUp() {
        val type = PowerUpType.entries.toTypedArray().random()
        val (x, y) = generateSafeSpawnPosition()
        engine.powerUps.add(PowerUp(x, y, type))
    }

    private fun generateSafeSpawnPosition(): Pair<Double, Double> {
        var x: Double
        var y: Double
        var attempts = 0
        do {
            x = Random.nextDouble(SPAWN_MARGIN, engine.canvasWidth - SPAWN_MARGIN)
            y = Random.nextDouble(SPAWN_MARGIN, engine.canvasHeight - SPAWN_MARGIN)
            attempts++
        } while (attempts < MAX_SPAWN_ATTEMPTS && (isNearPaddle(x, y) || isNearObstacle(x, y)))
        return Pair(x, y)
    }

    private fun isNearPaddle(
        x: Double,
        y: Double,
    ): Boolean {
        if (abs(x - (engine.canvasWidth - PADDLE_WIDTH)) < MIN_SPAWN_DISTANCE &&
            y >= engine.paddle2Y && y <= engine.paddle2Y + engine.paddleHeight
        ) {
            return true
        }
        if (abs(x - PADDLE_WIDTH) < MIN_SPAWN_DISTANCE &&
            y >= engine.paddle1Y && y <= engine.paddle1Y + engine.paddleHeight
        ) {
            return true
        }
        return false
    }

    private fun isNearObstacle(
        x: Double,
        y: Double,
    ): Boolean =
        engine.lines.any { line ->
            line.flattenedPoints?.any { pt -> hypot(x - pt.x, y - pt.y) < OBSTACLE_SPAWN_DISTANCE } ?: false
        }

    private fun checkPowerUpCollisions() {
        engine.powerUps.removeAll { pu ->
            if (pu.isActive && hypot(engine.puck.x - pu.x, engine.puck.y - pu.y) < pu.radius + engine.puck.radius) {
                activatePowerUp(pu.type)
                true
            } else {
                false
            }
        }
    }

    private fun activatePowerUp(type: PowerUpType) {
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = type,
                duration = PowerUpType.getDuration(type),
                activationTime = System.nanoTime(),
            ),
        )
    }

    private fun applyActivePowerUpEffects() {
        var hasSpeedBoost = false
        engine.activePowerUpEffects.forEach { effect ->
            when (effect.type) {
                PowerUpType.SPEED_BOOST -> {
                    hasSpeedBoost = true
                }

                PowerUpType.MAGNET_BALL -> {
                    applyMagnetEffect()
                }

                else -> {}
            }
        }
        engine.powerUpSpeedMultiplier = if (hasSpeedBoost) 1.5 else 1.0
    }

    private fun applyMagnetEffect() {
        val cx = engine.canvasWidth - engine.puck.radius
        val cy = engine.paddle2Y + engine.paddleHeight / 2
        val d = hypot(engine.puck.x - cx, engine.puck.y - cy)
        if (d in 1e-9..MAGNET_RANGE) {
            engine.puck.vx += (cx - engine.puck.x) / d * MAGNET_STRENGTH
            engine.puck.vy += (cy - engine.puck.y) / d * MAGNET_STRENGTH
        }
    }

    private fun deactivatePowerUpEffect(type: PowerUpType) {
        when (type) {
            PowerUpType.GHOST_MODE -> {
                engine.isGhostMode = false
            }

            PowerUpType.PADDLE_SHIELD -> {
                engine.hasPaddleShield = false
            }

            else -> {}
        }
    }

    companion object {
        private const val SPAWN_INTERVAL_NS = 10_000_000_000L
        private const val SPAWN_MARGIN = 50.0
        private const val MAX_SPAWN_ATTEMPTS = 20
        private const val MIN_SPAWN_DISTANCE = 100.0
        private const val OBSTACLE_SPAWN_DISTANCE = 30.0
        private const val PADDLE_WIDTH = 20.0
        private const val MAGNET_RANGE = 150.0
        private const val MAGNET_STRENGTH = 0.3
    }
}

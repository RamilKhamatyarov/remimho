package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

@ApplicationScoped
class PowerUpManager {
    @Inject
    lateinit var engine: GameEngine

    private var lastSpawnTime = 0L
    private val spawnInterval = 10_000_000_000L
    private val minSpawnDistance = 100.0

    fun update(deltaTime: Double) {
        if (System.nanoTime() - lastSpawnTime > spawnInterval) {
            spawnRandomPowerUp()
            lastSpawnTime = System.nanoTime()
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
            x = Random.nextDouble(50.0, engine.canvasWidth - 50.0)
            y = Random.nextDouble(50.0, engine.canvasHeight - 50.0)
            attempts++
        } while (attempts < 20 && (isNearPaddle(x, y) || isNearObstacle(x, y)))
        return Pair(x, y)
    }

    private fun isNearPaddle(
        x: Double,
        y: Double,
    ): Boolean {
        val pw = 20.0
        if (abs(x - (engine.canvasWidth - pw)) < minSpawnDistance &&
            y >= engine.paddle2Y && y <= engine.paddle2Y + engine.paddleHeight
        ) {
            return true
        }
        if (abs(x - pw) < minSpawnDistance &&
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
            line.flattenedPoints?.any { pt -> hypot(x - pt.x, y - pt.y) < 30.0 } ?: false
        }

    private fun checkPowerUpCollisions() {
        val pucks = mutableListOf(Pair(engine.puck.x, engine.puck.y))
        pucks.addAll(engine.additionalPucks.map { Pair(it.x, it.y) })

        pucks.forEach { (px, py) ->
            engine.powerUps.removeAll { pu ->
                if (pu.isActive && hypot(px - pu.x, py - pu.y) < pu.radius + 10) {
                    activatePowerUp(pu.type)
                    true
                } else {
                    false
                }
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

                PowerUpType.GHOST_MODE -> {}

                PowerUpType.MULTI_BALL -> {}

                PowerUpType.PADDLE_SHIELD -> {}
            }
        }
        engine.powerUpSpeedMultiplier = if (hasSpeedBoost) 1.5 else 1.0
    }

    private fun applyMagnetEffect() {
        applyMagnetToPuck(
            engine.puck.x,
            engine.puck.y,
            { engine.puck.vx = it },
            { engine.puck.vy = it },
        )

        engine.additionalPucks.forEach { puck ->
            applyMagnetToPuck(puck.x, puck.y, { puck.vx = it }, { puck.vy = it })
        }
    }

    private fun applyMagnetToPuck(
        puckX: Double,
        puckY: Double,
        setVX: (Double) -> Unit,
        setVY: (Double) -> Unit,
    ) {
        val pw = 20.0
        val magnetRange = 150.0
        val magnetStrength = 0.3
        val cx = engine.canvasWidth - pw / 2
        val cy = engine.paddle2Y + engine.paddleHeight / 2
        val d = hypot(puckX - cx, puckY - cy)
        if (d < magnetRange) {
            setVX(engine.puck.vx + (cx - puckX) / d * magnetStrength)
            setVY(engine.puck.vy + (cy - puckY) / d * magnetStrength)
        }
    }

    private fun spawnAdditionalPucks() {
        repeat(2) {
            val angle = Random.nextDouble(0.0, 2 * PI)
            val speed = 3.0 + Random.nextDouble(-1.0, 1.0)
            engine.additionalPucks.add(
                AdditionalPuck(
                    x = engine.puck.x + Random.nextDouble(-20.0, 20.0),
                    y = engine.puck.y + Random.nextDouble(-20.0, 20.0),
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    lifetime = 15_000_000_000L,
                ),
            )
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
}

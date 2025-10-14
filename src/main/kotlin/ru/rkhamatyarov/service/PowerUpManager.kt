package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.GameState
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
    lateinit var gameState: GameState

    private var lastSpawnTime = 0L
    private val spawnInterval = 10_000_000_000L
    private val minSpawnDistance = 100.0

    fun update(deltaTime: Long) {
        if (System.nanoTime() - lastSpawnTime > spawnInterval) {
            spawnRandomPowerUp()
            lastSpawnTime = System.nanoTime()
        }

        gameState.powerUps.removeAll { it.isExpired() }

        gameState.activePowerUpEffects.removeAll { effect ->
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
        val powerUpType = PowerUpType.values().random()
        val (x, y) = generateSafeSpawnPosition()

        val powerUp = PowerUp(x, y, powerUpType)
        gameState.powerUps.add(powerUp)
    }

    private fun generateSafeSpawnPosition(): Pair<Double, Double> {
        var x: Double
        var y: Double
        var attempts = 0

        do {
            x = Random.nextDouble(50.0, gameState.canvasWidth - 50.0)
            y = Random.nextDouble(50.0, gameState.canvasHeight - 50.0)
            attempts++
        } while (attempts < 20 && (isNearPaddle(x, y) || isNearObstacle(x, y)))

        return Pair(x, y)
    }

    private fun isNearPaddle(
        x: Double,
        y: Double,
    ): Boolean {
        val paddleWidth = 20.0

        val distanceToPlayer = abs(x - (gameState.canvasWidth - paddleWidth))
        if (
            distanceToPlayer < minSpawnDistance &&
            y >= gameState.paddle2Y &&
            y <= gameState.paddle2Y + gameState.paddleHeight
        ) {
            return true
        }

        val distanceToAI = abs(x - paddleWidth)
        if (
            distanceToAI < minSpawnDistance &&
            y >= gameState.paddle1Y &&
            y <= gameState.paddle1Y + gameState.paddleHeight
        ) {
            return true
        }

        return false
    }

    private fun isNearObstacle(
        x: Double,
        y: Double,
    ): Boolean =
        gameState.lines.any { line ->
            line.flattenedPoints?.any { point ->
                hypot(x - point.x, y - point.y) < 30.0
            } ?: false
        }

    private fun checkPowerUpCollisions() {
        val pucksToCheck = mutableListOf<Pair<Double, Double>>()

        pucksToCheck.add(Pair(gameState.puckX, gameState.puckY))

        pucksToCheck.addAll(gameState.additionalPucks.map { Pair(it.x, it.y) })

        pucksToCheck.forEach { (puckX, puckY) ->
            gameState.powerUps.removeAll { powerUp ->
                if (powerUp.isActive) {
                    val distance = hypot(puckX - powerUp.x, puckY - powerUp.y)
                    if (distance < powerUp.radius + 10) {
                        activatePowerUp(powerUp.type)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
    }

    private fun activatePowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.SPEED_BOOST -> {
                val effect = ActivePowerUpEffect(type, duration = 5_000_000_000L)
                gameState.activePowerUpEffects.add(effect)
            }

            PowerUpType.MAGNET_BALL -> {
                val effect = ActivePowerUpEffect(type, duration = 8_000_000_000L)
                gameState.activePowerUpEffects.add(effect)
            }

            PowerUpType.GHOST_MODE -> {
                val effect = ActivePowerUpEffect(type, duration = 6_000_000_000L)
                gameState.activePowerUpEffects.add(effect)
                gameState.isGhostMode = true
            }

            PowerUpType.MULTI_BALL -> {
                spawnAdditionalPucks()
            }

            PowerUpType.PADDLE_SHIELD -> {
                val effect = ActivePowerUpEffect(type, duration = 10_000_000_000L)
                gameState.activePowerUpEffects.add(effect)
                gameState.hasPaddleShield = true
            }
        }
    }

    private fun applyActivePowerUpEffects() {
        var hasSpeedBoost = false
        var hasMagnetEffect = false

        gameState.activePowerUpEffects.forEach { effect ->
            when (effect.type) {
                PowerUpType.SPEED_BOOST -> {
                    hasSpeedBoost = true
                }

                PowerUpType.MAGNET_BALL -> {
                    hasMagnetEffect = true
                    applyMagnetEffect()
                }

                PowerUpType.GHOST_MODE -> {
                }

                PowerUpType.MULTI_BALL -> {
                }

                PowerUpType.PADDLE_SHIELD -> {
                }
            }
        }

        gameState.powerUpSpeedMultiplier = if (hasSpeedBoost) 1.5 else 1.0
    }

    private fun applyMagnetEffect() {
        val magnetStrength = 0.3
        val magnetRange = 150.0
        val paddleWidth = 20.0

        applyMagnetToPuck(
            gameState.puckX,
            gameState.puckY,
            { newVX -> gameState.puckVX = newVX },
            { newVY -> gameState.puckVY = newVY },
        )

        gameState.additionalPucks.forEach { puck ->
            applyMagnetToPuck(
                puck.x,
                puck.y,
                { newVX -> puck.vx = newVX },
                { newVY -> puck.vy = newVY },
            )
        }
    }

    private fun applyMagnetToPuck(
        puckX: Double,
        puckY: Double,
        setVX: (Double) -> Unit,
        setVY: (Double) -> Unit,
    ) {
        val paddleWidth = 20.0
        val magnetRange = 150.0
        val magnetStrength = 0.3

        val playerPaddleCenterX = gameState.canvasWidth - paddleWidth / 2
        val playerPaddleCenterY = gameState.paddle2Y + gameState.paddleHeight / 2
        val distanceToPlayer = hypot(puckX - playerPaddleCenterX, puckY - playerPaddleCenterY)

        if (distanceToPlayer < magnetRange) {
            val magnetForceX = (playerPaddleCenterX - puckX) / distanceToPlayer * magnetStrength
            val magnetForceY = (playerPaddleCenterY - puckY) / distanceToPlayer * magnetStrength

            setVX(gameState.puckVX + magnetForceX)
            setVY(gameState.puckVY + magnetForceY)
        }
    }

    private fun spawnAdditionalPucks() {
        val numAdditionalPucks = 2

        for (i in 0 until numAdditionalPucks) {
            val angle = Random.nextDouble(0.0, 2 * PI)
            val speed = 3.0 + Random.nextDouble(-1.0, 1.0)

            val additionalPuck =
                AdditionalPuck(
                    x = gameState.puckX + Random.nextDouble(-20.0, 20.0),
                    y = gameState.puckY + Random.nextDouble(-20.0, 20.0),
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    lifetime = 15_000_000_000L,
                )

            gameState.additionalPucks.add(additionalPuck)
        }
    }

    private fun deactivatePowerUpEffect(type: PowerUpType) {
        when (type) {
            PowerUpType.GHOST_MODE -> {
                gameState.isGhostMode = false
            }

            PowerUpType.PADDLE_SHIELD -> {
                gameState.hasPaddleShield = false
            }

            else -> {
            }
        }
    }
}

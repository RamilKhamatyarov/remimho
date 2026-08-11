package ru.rkhamatyarov.service

import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPowerUp
import kotlin.math.hypot

class PowerUpEscalationStrategy(
    roomId: String,
    private val curveVersion: String = DEFAULT_CURVE_VERSION,
    private val tiers: List<PowerUpEscalationTier> = DEFAULT_TIERS,
) {
    private val seed = stableSeed("$curveVersion:$roomId")
    private var spawnIndex = 0L
    private var nextSpawnElapsedNs = tiers.first().spawnIntervalNs

    fun nextSpawn(
        state: MviGameState,
        elapsedNs: Long,
    ): MviPowerUp? {
        if (state.paused || elapsedNs < nextSpawnElapsedNs) return null

        val tier = tierAt(elapsedNs)
        val currentSpawnIndex = spawnIndex++
        nextSpawnElapsedNs = elapsedNs + tier.spawnIntervalNs

        if (state.powerUps.size >= tier.fieldPowerUpLimit) return null
        return createDeterministicPowerUp(
            type = chooseType(tier, currentSpawnIndex),
            state = state,
            elapsedNs = elapsedNs,
            seed = seed,
            spawnIndex = currentSpawnIndex,
        )
    }

    private fun tierAt(elapsedNs: Long): PowerUpEscalationTier = tiers.last { elapsedNs >= it.startsAtNs }

    private fun chooseType(
        tier: PowerUpEscalationTier,
        index: Long,
    ): PowerUpType {
        val selectedIndex = positiveModulo(mix64(seed + index * TYPE_STEP), tier.eligibleTypes.size)
        return tier.eligibleTypes[selectedIndex]
    }

    companion object {
        const val DEFAULT_CURVE_VERSION = "powerup-escalation-v1"
        private const val SECOND_NS = 1_000_000_000L
        private val TYPE_STEP = 0x9E3779B97F4A7C15uL.toLong()

        private val DEFAULT_TIERS =
            listOf(
                PowerUpEscalationTier(
                    startsAtNs = 0L,
                    spawnIntervalNs = 10L * SECOND_NS,
                    fieldPowerUpLimit = 2,
                    eligibleTypes = listOf(PowerUpType.SPEED_BOOST, PowerUpType.MAGNET_BALL),
                ),
                PowerUpEscalationTier(
                    startsAtNs = 60L * SECOND_NS,
                    spawnIntervalNs = 8L * SECOND_NS,
                    fieldPowerUpLimit = 3,
                    eligibleTypes = listOf(PowerUpType.SPEED_BOOST, PowerUpType.MAGNET_BALL, PowerUpType.GHOST_MODE),
                ),
                PowerUpEscalationTier(
                    startsAtNs = 120L * SECOND_NS,
                    spawnIntervalNs = 6L * SECOND_NS,
                    fieldPowerUpLimit = 4,
                    eligibleTypes =
                        listOf(
                            PowerUpType.SPEED_BOOST,
                            PowerUpType.MAGNET_BALL,
                            PowerUpType.GHOST_MODE,
                            PowerUpType.PADDLE_SHIELD,
                        ),
                ),
                PowerUpEscalationTier(
                    startsAtNs = 240L * SECOND_NS,
                    spawnIntervalNs = 5L * SECOND_NS,
                    fieldPowerUpLimit = 4,
                    eligibleTypes =
                        listOf(
                            PowerUpType.SPEED_BOOST,
                            PowerUpType.MAGNET_BALL,
                            PowerUpType.GHOST_MODE,
                            PowerUpType.PADDLE_SHIELD,
                        ),
                ),
            )
    }
}

data class PowerUpEscalationTier(
    val startsAtNs: Long,
    val spawnIntervalNs: Long,
    val fieldPowerUpLimit: Int,
    val eligibleTypes: List<PowerUpType>,
) {
    init {
        require(spawnIntervalNs > 0L) { "spawnIntervalNs must be positive" }
        require(fieldPowerUpLimit > 0) { "fieldPowerUpLimit must be positive" }
        require(eligibleTypes.isNotEmpty()) { "eligibleTypes must not be empty" }
    }
}

fun createDeterministicPowerUp(
    type: PowerUpType,
    state: MviGameState,
    elapsedNs: Long = (state.elapsedSeconds * 1_000_000_000L).toLong(),
    seed: Long = stableSeed("manual:${type.name}:$elapsedNs:${state.powerUps.size}"),
    spawnIndex: Long = state.powerUps.size.toLong(),
): MviPowerUp? {
    repeat(SPAWN_ATTEMPTS) { attempt ->
        val candidateSeed = seed + spawnIndex * SPAWN_INDEX_STEP + attempt * ATTEMPT_STEP
        val x = deterministicCoordinate(candidateSeed, state.canvasWidth, state.canvasWidth * HORIZONTAL_MARGIN_RATIO)
        val y = deterministicCoordinate(candidateSeed xor Y_SEED_MASK, state.canvasHeight, POWER_UP_RADIUS)
        if (isSafePowerUpPosition(x, y, state)) {
            return MviPowerUp(
                id = "${type.name.lowercase()}-$elapsedNs-$spawnIndex",
                x = x,
                y = y,
                type = type,
                createdNs = elapsedNs,
                radius = POWER_UP_RADIUS,
            )
        }
    }
    return null
}

private fun deterministicCoordinate(
    seed: Long,
    size: Double,
    requestedMargin: Double,
): Double {
    val margin = requestedMargin.coerceAtLeast(POWER_UP_RADIUS)
    val min = margin
    val max = size - margin
    if (max <= min) return size / 2.0
    return min + deterministicUnit(seed) * (max - min)
}

private fun isSafePowerUpPosition(
    x: Double,
    y: Double,
    state: MviGameState,
): Boolean {
    val awayFromPuck = hypot(x - state.puck.x, y - state.puck.y) >= POWER_UP_RADIUS * PUCK_CLEARANCE_RADIUS_MULTIPLIER
    val awayFromPowerUps =
        state.powerUps.all { powerUp ->
            hypot(x - powerUp.x, y - powerUp.y) >= POWER_UP_RADIUS * POWER_UP_CLEARANCE_RADIUS_MULTIPLIER
        }
    return awayFromPuck && awayFromPowerUps
}

private fun deterministicUnit(seed: Long): Double {
    val bits = mix64(seed).ushr(11)
    return bits.toDouble() / (1L shl 53).toDouble()
}

internal fun stableSeed(value: String): Long = value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }

private fun positiveModulo(
    value: Long,
    divisor: Int,
): Int = Math.floorMod(value, divisor)

private fun mix64(value: Long): Long {
    var z = value + 0x9E3779B97F4A7C15uL.toLong()
    z = (z xor z.ushr(30)) * 0xBF58476D1CE4E5B9uL.toLong()
    z = (z xor z.ushr(27)) * 0x94D049BB133111EBuL.toLong()
    return z xor z.ushr(31)
}

private const val POWER_UP_RADIUS = 15.0
private const val HORIZONTAL_MARGIN_RATIO = 0.18
private const val PUCK_CLEARANCE_RADIUS_MULTIPLIER = 5.0
private const val POWER_UP_CLEARANCE_RADIUS_MULTIPLIER = 4.0
private const val SPAWN_ATTEMPTS = 16
private val SPAWN_INDEX_STEP = 0x632BE59BD9B4E019uL.toLong()
private val ATTEMPT_STEP = 0x85157AF5uL.toLong()
private val Y_SEED_MASK = 0xD1B54A32D192ED03uL.toLong()

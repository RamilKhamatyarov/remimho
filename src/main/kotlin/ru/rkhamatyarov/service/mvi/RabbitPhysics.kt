package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Replay-v2 zigzag: bounded turns on the logical clock, without changing speed. */
internal object RabbitPhysics {
    fun refresh(
        active: List<MviActivePowerUp>,
        collected: List<MviPowerUp>,
        elapsedNs: Long,
    ): List<MviActivePowerUp> {
        val refreshed = collected.any { it.type == PowerUpType.RABBIT_MODE }
        val retained = if (refreshed) active.filterNot { it.type == PowerUpType.RABBIT_MODE } else active
        val additions = collected.filterNot { it.type == PowerUpType.RABBIT_MODE }.map { it.activate(elapsedNs) }
        val rabbit = collected.firstOrNull { it.type == PowerUpType.RABBIT_MODE }?.activate(elapsedNs)
        return retained + additions + listOfNotNull(rabbit)
    }

    /** Parity accounts for missed intervals without a loop or duplicate turns after restore. */
    fun apply(
        puck: MviPuck,
        active: List<MviActivePowerUp>,
        elapsedNs: Long,
    ): Result {
        val effectIndex = active.indexOfFirst { it.type == PowerUpType.RABBIT_MODE }
        if (effectIndex < 0) return Result(puck, active)
        val effect = active[effectIndex]
        val age = elapsedNs - effect.activatedNs
        if (age < 0L || age >= effect.durationNs) return Result(puck, active)
        val due = age / TURN_INTERVAL_NS
        if (due <= effect.rabbitTurnsApplied) return Result(puck, active)
        val phaseChange = (due % 2 - effect.rabbitTurnsApplied % 2).toDouble()
        val angle = phaseChange * TURN_ANGLE
        val turned = turnPack(phaseChange, puck, angle)
        return Result(
            puck = turned,
            active =
                active.mapIndexed { index, item ->
                    if (index == effectIndex) item.copy(rabbitTurnsApplied = due) else item
                },
        )
    }

    private fun turnPack(
        phaseChange: Double,
        puck: MviPuck,
        angle: Double,
    ): MviPuck =
        if (phaseChange == 0.0) {
            puck
        } else {
            puck.copy(
                vx = puck.vx * cos(angle) - puck.vy * sin(angle),
                vy = puck.vx * sin(angle) + puck.vy * cos(angle),
            )
        }

    private fun MviPowerUp.activate(elapsedNs: Long): MviActivePowerUp =
        MviActivePowerUp(type, elapsedNs, PowerUpType.getDuration(type))

    data class Result(
        val puck: MviPuck,
        val active: List<MviActivePowerUp>,
    )

    private const val TURN_INTERVAL_NS = 300_000_000L
    private const val TURN_ANGLE = PI / 12.0
}

package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.service.mvi.Combo
import ru.rkhamatyarov.service.mvi.MviActivePowerUp
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.OneTimerConfig
import ru.rkhamatyarov.service.mvi.PuckTouch

/**
 * Canonical field order of the 2D state. Every constructor property is written; the 2D state
 * holds no presentation-only data, so [EXCLUDED_PATHS] is empty and exists to force a decision
 * when a field is added (see the completeness test).
 */
object MviGameStateEncoder : StateEncoder<MviGameState> {
    val EXCLUDED_PATHS: Set<String> = emptySet()

    override fun encode(
        state: MviGameState,
        sink: FieldSink,
    ) {
        puck(state.puck, sink)
        sink.int("score.playerA", state.score.playerA)
        sink.int("score.playerB", state.score.playerB)
        sink.double("paddle1Y", state.paddle1Y)
        sink.double("paddle2Y", state.paddle2Y)
        sink.double("paddle1Velocity", state.paddle1Velocity)
        sink.double("paddle2Velocity", state.paddle2Velocity)
        sink.boolean("paused", state.paused)
        sink.double("canvasWidth", state.canvasWidth)
        sink.double("canvasHeight", state.canvasHeight)
        sink.double("paddleHeight", state.paddleHeight)
        sink.count("lines", state.lines.size)
        state.lines.forEachIndexed { index, line -> line(line, "lines[$index]", sink) }
        sink.count("teleports", state.teleports.size)
        state.teleports.toSortedMap().forEach { (from, to) -> sink.text("teleports[$from]", to) }
        speedConfig(state.speedConfig, sink)
        sink.double("elapsedSeconds", state.elapsedSeconds)
        aiConfig(state.aiConfig, sink)
        sink.count("powerUps", state.powerUps.size)
        state.powerUps.forEachIndexed { index, powerUp -> powerUp(powerUp, "powerUps[$index]", sink) }
        sink.count("activePowerUps", state.activePowerUps.size)
        state.activePowerUps.forEachIndexed { index, active -> activePowerUp(active, "activePowerUps[$index]", sink) }
        sink.double("speedMultiplier", state.speedMultiplier)
        sink.boolean("ghostMode", state.ghostMode)
        sink.boolean("paddleShield", state.paddleShield)
        sink.count("touchLedger.entries", state.touchLedger.entries.size)
        state.touchLedger.entries.forEachIndexed { index, touch -> touch(touch, "touchLedger.entries[$index]", sink) }
        oneTimerConfig(state.oneTimerConfig, sink)
        combo(state.combo, sink)
        sink.text("serveSide", state.serveSide.name)
    }

    private fun puck(
        puck: MviPuck,
        sink: FieldSink,
    ) {
        sink.double("puck.x", puck.x)
        sink.double("puck.y", puck.y)
        sink.double("puck.vx", puck.vx)
        sink.double("puck.vy", puck.vy)
        sink.double("puck.radius", puck.radius)
        sink.double("puck.spin", puck.spin)
        sink.long("puck.spinRemainingNs", puck.spinRemainingNs)
        sink.long("puck.teleportCooldownUntilNs", puck.teleportCooldownUntilNs)
        sink.text("puck.lastTeleportPairId", puck.lastTeleportPairId)
    }

    private fun line(
        line: MviLine,
        path: String,
        sink: FieldSink,
    ) {
        sink.text("$path.id", line.id)
        sink.count("$path.points", line.points.size)
        line.points.forEachIndexed { index, point ->
            sink.double("$path.points[$index].x", point.x)
            sink.double("$path.points[$index].y", point.y)
        }
        sink.double("$path.width", line.width)
        sink.text("$path.ownerSide", line.ownerSide?.name)
        sink.text("$path.combinationId", line.combinationId)
    }

    private fun speedConfig(
        config: SpeedConfig,
        sink: FieldSink,
    ) {
        sink.double("speedConfig.baseMultiplier", config.baseMultiplier)
        sink.double("speedConfig.timeAccelerationRate", config.timeAccelerationRate)
        sink.double("speedConfig.levelAccelerationPerLine", config.levelAccelerationPerLine)
        sink.double("speedConfig.maxMultiplier", config.maxMultiplier)
        sink.double("speedConfig.rallyGrowthPerHit", config.rallyGrowthPerHit)
        sink.double("speedConfig.smashBoost", config.smashBoost)
        sink.double("speedConfig.maxRallySpeed", config.maxRallySpeed)
        sink.double("speedConfig.maxEffectiveSpeed", config.maxEffectiveSpeed)
    }

    private fun aiConfig(
        config: AiOpponentConfig,
        sink: FieldSink,
    ) {
        sink.boolean("aiConfig.enabled", config.enabled)
        sink.long("aiConfig.reactionDelayMs", config.reactionDelayMs)
        sink.double("aiConfig.aimError", config.aimError)
        sink.int("aiConfig.predictionDepth", config.predictionDepth)
        sink.double("aiConfig.aggression", config.aggression)
    }

    private fun powerUp(
        powerUp: MviPowerUp,
        path: String,
        sink: FieldSink,
    ) {
        sink.text("$path.id", powerUp.id)
        sink.double("$path.x", powerUp.x)
        sink.double("$path.y", powerUp.y)
        sink.text("$path.type", powerUp.type.name)
        sink.long("$path.createdNs", powerUp.createdNs)
        sink.long("$path.lifetimeNs", powerUp.lifetimeNs)
        sink.double("$path.radius", powerUp.radius)
    }

    private fun activePowerUp(
        active: MviActivePowerUp,
        path: String,
        sink: FieldSink,
    ) {
        sink.text("$path.type", active.type.name)
        sink.long("$path.activatedNs", active.activatedNs)
        sink.long("$path.durationNs", active.durationNs)
        sink.long("$path.rabbitTurnsApplied", active.rabbitTurnsApplied)
    }

    private fun touch(
        touch: PuckTouch,
        path: String,
        sink: FieldSink,
    ) {
        sink.text("$path.source", touch.source.name)
        sink.text("$path.ownerSide", touch.ownerSide?.name)
        sink.text("$path.identifier", touch.identifier)
        sink.long("$path.elapsedNs", touch.elapsedNs)
        sink.double("$path.speedAtContact", touch.speedAtContact)
    }

    private fun oneTimerConfig(
        config: OneTimerConfig,
        sink: FieldSink,
    ) {
        sink.double("oneTimerConfig.minimumIncomingSpeed", config.minimumIncomingSpeed)
        sink.long("oneTimerConfig.freshnessWindowNs", config.freshnessWindowNs)
        sink.double("oneTimerConfig.minimumMultiplier", config.minimumMultiplier)
        sink.double("oneTimerConfig.maximumMultiplier", config.maximumMultiplier)
        sink.double("oneTimerConfig.fullStrengthIncomingSpeed", config.fullStrengthIncomingSpeed)
        sink.double("oneTimerConfig.maximumRawSpeed", config.maximumRawSpeed)
    }

    private fun combo(
        combo: Combo,
        sink: FieldSink,
    ) {
        sink.boolean("combo.enabled", combo.enabled)
        sink.double("combo.giveAndGoMultiplier", combo.giveAndGoMultiplier)
        sink.double("combo.maximumRawSpeed", combo.maximumRawSpeed)
        sink.long("combo.giveAndGoWindowNs", combo.giveAndGoWindowNs)
        sink.int("combo.superGoalThreshold", combo.superGoalThreshold)
        sink.long("combo.superGoalWindowNs", combo.superGoalWindowNs)
    }
}

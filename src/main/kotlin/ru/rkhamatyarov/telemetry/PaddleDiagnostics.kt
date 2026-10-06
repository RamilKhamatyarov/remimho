package ru.rkhamatyarov.telemetry

import org.jboss.logging.Logger
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.PaddleSide

/** Logs goal context outside reduction; enabled through this logger's DEBUG category. */
internal object PaddleDiagnostics {
    private val log = Logger.getLogger(PaddleDiagnostics::class.java)

    fun record(
        roomId: String,
        before: MviGameState,
        after: MviGameState,
        action: GameAction,
    ) {
        if (!log.isDebugEnabled || action !is GameAction.Tick || before.score == after.score) return
        val defender = if (after.score.playerA > before.score.playerA) PaddleSide.B else PaddleSide.A
        val paddleY = if (defender == PaddleSide.A) before.paddle1Y else before.paddle2Y
        log.debugf(
            "Paddle miss room=%s side=%s elapsedNs=%d dt=%s puck=%s paddleY=%s height=%s " +
                "ghost=%s speedMultiplier=%s turbo=%s speedConfig=%s previousTouches=%s",
            roomId,
            defender,
            action.elapsedNs,
            action.deltaSeconds,
            before.puck,
            paddleY,
            before.paddleHeight,
            before.ghostMode,
            before.speedMultiplier,
            action.turboSpeedMultiplier,
            before.speedConfig,
            before.touchLedger.entries,
        )
    }
}

package ru.rkhamatyarov.service.mvi

/** Applies one deterministic action to the authoritative state. */
fun reduce(
    state: MviGameState,
    action: GameAction,
): MviGameState =
    when (action) {
        is GameAction.Tick -> {
            TickReducer.reduce(
                state = state,
                deltaSeconds = action.deltaSeconds,
                elapsedNs = action.elapsedNs,
                turboSpeedMultiplier = action.turboSpeedMultiplier,
            )
        }

        is GameAction.MovePaddle -> {
            state.movePaddle(action)
        }

        is GameAction.ActivateTurbo -> {
            state
        }

        GameAction.TogglePause -> {
            state.copy(paused = !state.paused)
        }

        GameAction.Reset -> {
            state.resetMatch()
        }

        is GameAction.CommitLine -> {
            state.commitLine(action.line)
        }

        is GameAction.EraseLine -> {
            state.copy(lines = state.lines.filterNot { it.id == action.lineId })
        }

        GameAction.ClearLines -> {
            state.copy(lines = emptyList())
        }

        is GameAction.RestoreSnapshot -> {
            action.state
        }

        is GameAction.ApplyTeleports -> {
            state.copy(teleports = action.portals)
        }

        is GameAction.SpawnPowerUp -> {
            state.copy(powerUps = state.powerUps + action.powerUp)
        }

        is GameAction.ApplySpeedConfig -> {
            state.copy(speedConfig = action.config)
        }

        is GameAction.ApplyAiConfig -> {
            state.copy(aiConfig = action.config)
        }
    }

private fun MviGameState.movePaddle(action: GameAction.MovePaddle): MviGameState {
    val y = action.y.coerceIn(0.0, canvasHeight - paddleHeight)
    return when (action.side) {
        PaddleSide.A -> copy(paddle1Y = y, paddle1Velocity = y - paddle1Y)
        PaddleSide.B -> copy(paddle2Y = y, paddle2Velocity = y - paddle2Y)
    }
}

private fun MviGameState.commitLine(line: MviLine): MviGameState =
    if (line.id.isBlank()) {
        this
    } else {
        copy(lines = lines.filterNot { it.id == line.id } + line)
    }

private fun MviGameState.resetMatch(): MviGameState =
    copy(
        puck = puck.resetForServe(canvasWidth, canvasHeight),
        paused = false,
        lines = emptyList(),
        elapsedSeconds = 0.0,
        powerUps = emptyList(),
        activePowerUps = emptyList(),
        speedMultiplier = 1.0,
        ghostMode = false,
        paddleShield = false,
        touchLedger = TouchLedger(),
    )

internal fun MviPuck.resetForServe(
    canvasWidth: Double,
    canvasHeight: Double,
): MviPuck =
    copy(
        x = canvasWidth / 2,
        y = canvasHeight / 2,
        vx = if (vx > 0) DEFAULT_SERVE_VX else -DEFAULT_SERVE_VX,
        vy = DEFAULT_SERVE_VY,
        spin = 0.0,
        spinRemainingNs = 0L,
        teleportCooldownUntilNs = 0L,
        lastTeleportPairId = null,
    )

private const val DEFAULT_SERVE_VX = 300.0
private const val DEFAULT_SERVE_VY = 200.0

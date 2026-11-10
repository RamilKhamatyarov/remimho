package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.PowerUpType

/** Applies one deterministic action to the authoritative state. */
fun reduce(
    state: MviGameState,
    action: GameAction,
): MviGameState = reduceReplay(state, action, PhysicsRules.CURRENT)

/** Compatibility entry point for historical recordings, never used by the live actor. */
internal fun reduceReplay(
    state: MviGameState,
    action: GameAction,
    rules: PhysicsRules,
): MviGameState =
    reduceAction(
        state = state.forRules(rules),
        action = action,
        rules = rules,
    )

private fun reduceAction(
    state: MviGameState,
    action: GameAction,
    rules: PhysicsRules,
): MviGameState =
    when (action) {
        is GameAction.Tick -> {
            TickReducer.reduce(
                state = state,
                deltaSeconds = action.deltaSeconds,
                elapsedNs = action.elapsedNs,
                turboSpeedMultiplier = action.turboSpeedMultiplier,
                rules = rules,
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

        is GameAction.ApplyCombination -> {
            state.applyCombination(action)
        }

        is GameAction.RestoreSnapshot -> {
            action.state.forRules(rules)
        }

        is GameAction.ApplyTeleports -> {
            state.copy(teleports = action.portals)
        }

        is GameAction.SpawnPowerUp -> {
            if (!rules.legacyGhostMode && action.powerUp.type == PowerUpType.GHOST_MODE) {
                state
            } else {
                state.copy(powerUps = state.powerUps + action.powerUp)
            }
        }

        is GameAction.ApplySpeedConfig -> {
            state.copy(speedConfig = action.config)
        }

        is GameAction.ApplyAiConfig -> {
            state.copy(aiConfig = action.config)
        }
    }

/**
 * [GameAction.MovePaddle.y] is the requested paddle CENTRE, so a pointer aimed at the puck puts the
 * middle of the paddle there rather than its top edge. Travel accumulates since the last tick so
 * several moves inside one frame all reach the physics step; [TickReducer] zeroes the velocities
 * once the tick has consumed them.
 */
private fun MviGameState.movePaddle(action: GameAction.MovePaddle): MviGameState {
    val top = (action.y - paddleHeight / 2.0).coerceIn(0.0, canvasHeight - paddleHeight)
    return when (action.side) {
        PaddleSide.A -> copy(paddle1Y = top, paddle1Velocity = paddle1Velocity + (top - paddle1Y))
        PaddleSide.B -> copy(paddle2Y = top, paddle2Velocity = paddle2Velocity + (top - paddle2Y))
    }
}

/** Prevents legacy snapshots from enabling ghost effects when resumed as a live match. */
private fun MviGameState.forRules(rules: PhysicsRules): MviGameState =
    if (rules.legacyGhostMode) this else withoutGhostMode()

private fun MviGameState.withoutGhostMode(): MviGameState {
    if (!ghostMode && powerUps.none { it.type == PowerUpType.GHOST_MODE } &&
        activePowerUps.none { it.type == PowerUpType.GHOST_MODE }
    ) {
        return this
    }
    return copy(
        ghostMode = false,
        powerUps = powerUps.filterNot { it.type == PowerUpType.GHOST_MODE },
        activePowerUps = activePowerUps.filterNot { it.type == PowerUpType.GHOST_MODE },
    )
}

private fun MviGameState.commitLine(line: MviLine): MviGameState =
    if (line.id.isBlank()) {
        this
    } else {
        copy(lines = lines.filterNot { it.id == line.id } + line)
    }

/** Keeps unrelated lines intact, including freehand geometry captured in a saved preset. */
private fun MviGameState.applyCombination(action: GameAction.ApplyCombination): MviGameState {
    val retained = lines.filterNot { it.ownerSide == action.side && it.combinationId != null }
    val additions =
        action.lines.filterNot { candidate ->
            retained.any {
                it.ownerSide == action.side && it.points == candidate.points && it.width == candidate.width
            }
        }
    val nextLines = retained + additions
    val ids = nextLines.mapTo(mutableSetOf()) { it.id }
    return copy(
        paused = true,
        lines = nextLines,
        teleports = teleports.filter { (entry, exit) -> entry in ids && exit in ids },
    )
}

private fun MviGameState.resetMatch(): MviGameState =
    copy(
        puck = puck.resetForServe(canvasWidth, canvasHeight, serveSide.opponent()),
        serveSide = serveSide.opponent(),
        paddle1Velocity = 0.0,
        paddle2Velocity = 0.0,
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

/** Alternates the diagonal using reliable serve state, not the last rally's velocity. */
internal fun MviPuck.resetForServe(
    canvasWidth: Double,
    canvasHeight: Double,
    side: PaddleSide,
): MviPuck =
    copy(
        x = canvasWidth / 2,
        y = canvasHeight / 2,
        vx = if (side == PaddleSide.B) DEFAULT_SERVE_VX else -DEFAULT_SERVE_VX,
        vy = if (side == PaddleSide.B) DEFAULT_SERVE_VY else -DEFAULT_SERVE_VY,
        spin = 0.0,
        spinRemainingNs = 0L,
        teleportCooldownUntilNs = 0L,
        lastTeleportPairId = null,
    )

private const val DEFAULT_SERVE_VX = 300.0
private const val DEFAULT_SERVE_VY = 200.0

package ru.rkhamatyarov.service

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.rkhamatyarov.service.mvi.EphemeralEvent
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviDomainEvents
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.service.turbo.TurboBoostStrategy
import ru.rkhamatyarov.service.turbo.TurboHistory
import ru.rkhamatyarov.service.turbo.TurboHudState
import ru.rkhamatyarov.service.turbo.TurboSnapshot
import ru.rkhamatyarov.telemetry.MonitoredMailbox
import ru.rkhamatyarov.telemetry.MonitoredReducer
import java.util.concurrent.ConcurrentHashMap

class GameRoom(
    val id: String,
    val history: StateHistory = StateHistory(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val mailbox: ActorMailbox<GameIntent> = MonitoredMailbox(INTENT_BUFFER_CAPACITY),
    private val reducer: (MviGameState, GameAction) -> MviGameState = ::reduce,
    private val turboBoostStrategy: TurboBoostStrategy = TurboBoostStrategy(),
    private val powerUpEscalationStrategy: PowerUpEscalationStrategy = PowerUpEscalationStrategy(roomId = id),
    private val botController: BotController = BotController(roomId = id),
    private val autoPowerUpsEnabled: Boolean = true,
    private val replayLogCapacity: Int = DEFAULT_REPLAY_LOG_CAPACITY,
) {
    private val mutableReliableState = MutableStateFlow(MviGameState())
    private val mutableEphemeralEvents =
        MutableSharedFlow<EphemeralEvent>(
            replay = 0,
            extraBufferCapacity = EPHEMERAL_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val mutableStateFlow =
        MutableSharedFlow<ByteArray>(
            replay = 0,
            extraBufferCapacity = STATE_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val replayLog = ArrayDeque<GameIntent.Reliable>(replayLogCapacity)
    private val humanSideCounts = mutableMapOf<PaddleSide, Int>()
    private val humanSideLock = Any()

    val turboHistory = TurboHistory()
    val reliableState: StateFlow<MviGameState> = mutableReliableState.asStateFlow()
    val ephemeralEvents: SharedFlow<EphemeralEvent> = mutableEphemeralEvents.asSharedFlow()
    val stateFlow: SharedFlow<ByteArray> = mutableStateFlow.asSharedFlow()
    val turboState: StateFlow<TurboHudState> = turboBoostStrategy.hudState
    val mailboxDepth: Int
        get() = mailbox.depth

    init {
        scope.launch {
            while (true) {
                val intent = mailbox.receive() ?: break
                apply(intent)
            }
        }
    }

    fun dispatch(intent: GameIntent): Boolean =
        when (intent) {
            is GameIntent.Reliable -> mailbox.trySend(intent)
            is GameIntent.Ephemeral -> emitEphemeral(intent.event)
        }

    fun emitEphemeral(event: EphemeralEvent): Boolean = mutableEphemeralEvents.tryEmit(event)

    fun registerHumanSide(side: PaddleSide) =
        synchronized(humanSideLock) {
            humanSideCounts[side] = (humanSideCounts[side] ?: 0) + 1
        }

    fun unregisterHumanSide(side: PaddleSide) =
        synchronized(humanSideLock) {
            val remaining = (humanSideCounts[side] ?: 0) - 1
            if (remaining > 0) {
                humanSideCounts[side] = remaining
            } else {
                humanSideCounts.remove(side)
            }
        }

    fun isHumanControlled(side: PaddleSide): Boolean = synchronized(humanSideLock) { (humanSideCounts[side] ?: 0) > 0 }

    fun getReplayLog(): List<GameIntent.Reliable> = synchronized(replayLog) { replayLog.toList() }

    fun tryEmit(bytes: ByteArray): Boolean = mutableStateFlow.tryEmit(bytes)

    fun turboSnapshot(): TurboSnapshot = turboBoostStrategy.snapshot(currentElapsedNs())

    fun restoreTurbo(snapshot: TurboSnapshot) = turboBoostStrategy.restore(snapshot)

    fun shutdown() {
        mailbox.close()
        scope.cancel()
    }

    private fun apply(intent: GameIntent) {
        when (intent) {
            is GameIntent.Reliable -> applyReliable(intent)
            is GameIntent.Ephemeral -> mutableEphemeralEvents.tryEmit(intent.event)
        }
    }

    private fun applyReliable(intent: GameIntent.Reliable) {
        val action = enrichAction(intent.action)
        val effectiveIntent = GameIntent.Reliable(action)
        val elapsedNs = actionElapsedNs(action)
        turboBoostStrategy.onAction(action, elapsedNs)
        appendReplayIntent(effectiveIntent)

        val captured = MviDomainEvents.capture { reducer(mutableReliableState.value, action) }
        mutableReliableState.value = captured.value
        turboBoostStrategy.onEvents(captured.events, elapsedNs)
        resetBotControllerAfter(action)
        spawnPowerUpAfterTick(action)
        moveBotAfterTick(action)
    }

    private fun enrichAction(action: GameAction): GameAction =
        when (action) {
            is GameAction.Tick -> {
                val elapsedNs = action.elapsedNs
                action.copy(
                    playerAControlledByHuman = isHumanControlled(PaddleSide.A),
                    turboSpeedMultiplier = turboBoostStrategy.speedMultiplier(elapsedNs),
                )
            }

            is GameAction.ActivateTurbo -> {
                action.copy(elapsedNs = action.elapsedNs.takeIf { it > 0L } ?: currentElapsedNs())
            }

            else -> {
                action
            }
        }

    private fun appendReplayIntent(intent: GameIntent.Reliable) =
        synchronized(replayLog) {
            if (intent.action is GameAction.RestoreSnapshot) replayLog.clear()
            if (replayLog.size >= replayLogCapacity) replayLog.removeFirst()
            replayLog.addLast(intent)
        }

    private fun actionElapsedNs(action: GameAction): Long =
        when (action) {
            is GameAction.Tick -> action.elapsedNs
            is GameAction.ActivateTurbo -> action.elapsedNs.takeIf { it > 0L } ?: currentElapsedNs()
            else -> currentElapsedNs()
        }

    private fun currentElapsedNs(): Long = (reliableState.value.elapsedSeconds * 1_000_000_000L).toLong()

    private fun spawnPowerUpAfterTick(action: GameAction) {
        if (!autoPowerUpsEnabled || action !is GameAction.Tick) return
        val elapsedNs = action.elapsedNs.takeIf { it > 0L } ?: currentElapsedNs()
        val powerUp = powerUpEscalationStrategy.nextSpawn(reliableState.value, elapsedNs) ?: return
        applyReliable(GameIntent.Reliable(GameAction.SpawnPowerUp(powerUp)))
    }

    private fun moveBotAfterTick(action: GameAction) {
        if (action !is GameAction.Tick) return
        if (isHumanControlled(PaddleSide.A)) {
            botController.reset()
            return
        }
        val elapsedNs = action.elapsedNs.takeIf { it > 0L } ?: currentElapsedNs()
        val move = botController.nextMove(reliableState.value, elapsedNs) ?: return
        applyReliable(GameIntent.Reliable(move))
    }

    private fun resetBotControllerAfter(action: GameAction) {
        if (action is GameAction.ApplyAiConfig || action is GameAction.RestoreSnapshot || action == GameAction.Reset) {
            botController.reset()
        }
    }

    companion object {
        private const val INTENT_BUFFER_CAPACITY = 64
        private const val STATE_BUFFER_CAPACITY = 3
        private const val EPHEMERAL_BUFFER_CAPACITY = 64
        const val DEFAULT_REPLAY_LOG_CAPACITY = 54_000
    }
}

@ApplicationScoped
class RoomRegistry
    @Inject
    constructor(
        private val monitoredReducer: MonitoredReducer,
    ) {
        constructor() : this(MonitoredReducer())

        @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "remimho.rooms.replay-log-capacity",
            defaultValue = "54000",
        )
        var replayLogCapacity: Int = GameRoom.DEFAULT_REPLAY_LOG_CAPACITY

        private val rooms = ConcurrentHashMap<String, GameRoom>()

        fun get(roomId: String): GameRoom =
            rooms.computeIfAbsent(roomId.ifBlank { DEFAULT_ROOM_ID }) { id ->
                GameRoom(
                    id = id,
                    mailbox = MonitoredMailbox(),
                    reducer = monitoredReducer.wrap(::reduce),
                    replayLogCapacity = replayLogCapacity,
                )
            }

        fun activeRooms(): Collection<GameRoom> = rooms.values

        fun roomCount(): Int = rooms.size

        @PreDestroy
        fun shutdown() {
            rooms.values.forEach { it.shutdown() }
            rooms.clear()
        }

        companion object {
            const val DEFAULT_ROOM_ID = "default"
        }
    }

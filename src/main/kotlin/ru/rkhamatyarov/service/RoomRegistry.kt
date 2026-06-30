package ru.rkhamatyarov.service

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.mvi.EphemeralEvent
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.telemetry.MonitoredMailbox
import ru.rkhamatyarov.telemetry.MonitoredReducer
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GameRoom(
    val id: String,
    val history: StateHistory = StateHistory(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val mailbox: ActorMailbox<GameIntent> = MonitoredMailbox(INTENT_BUFFER_CAPACITY),
    private val reducer: (MviGameState, GameAction) -> MviGameState = ::reduce,
    autoPowerUpsEnabled: Boolean = true,
    private val powerUpSpawnInterval: Duration = AUTO_POWER_UP_INTERVAL,
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

    val reliableState: StateFlow<MviGameState> = mutableReliableState.asStateFlow()
    val ephemeralEvents: SharedFlow<EphemeralEvent> = mutableEphemeralEvents.asSharedFlow()
    val stateFlow: SharedFlow<ByteArray> = mutableStateFlow.asSharedFlow()
    val mailboxDepth: Int
        get() = mailbox.depth

    init {
        scope.launch {
            while (true) {
                val intent = mailbox.receive() ?: break
                apply(intent)
            }
        }
        if (autoPowerUpsEnabled) {
            scope.launch {
                spawnPowerUpsPeriodically()
            }
        }
    }

    fun dispatch(intent: GameIntent): Boolean = mailbox.trySend(intent)

    fun getReplayLog(): List<GameIntent.Reliable> = synchronized(replayLog) { replayLog.toList() }

    fun tryEmit(bytes: ByteArray): Boolean = mutableStateFlow.tryEmit(bytes)

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
        synchronized(replayLog) {
            if (intent.action is GameAction.RestoreSnapshot) replayLog.clear()
            if (replayLog.size >= replayLogCapacity) replayLog.removeFirst()
            replayLog.addLast(intent)
        }
        mutableReliableState.value = reducer(mutableReliableState.value, intent.action)
    }

    private suspend fun spawnPowerUpsPeriodically() {
        while (true) {
            delay(powerUpSpawnInterval)
            spawnRandomPowerUp()
        }
    }

    internal fun spawnRandomPowerUp(): Boolean {
        val state = reliableState.value
        if (state.paused) return false
        val elapsedNs = (state.elapsedSeconds * 1_000_000_000L).toLong()
        val types = PowerUpType.entries
        val type = types[Random.nextInt(types.size)]
        return dispatch(GameIntent.Reliable(GameAction.SpawnPowerUp(createRandomPowerUp(type, state, elapsedNs))))
    }

    companion object {
        private val AUTO_POWER_UP_INTERVAL = 10.seconds
        private const val INTENT_BUFFER_CAPACITY = 64
        private const val STATE_BUFFER_CAPACITY = 3
        private const val EPHEMERAL_BUFFER_CAPACITY = 64
        const val DEFAULT_REPLAY_LOG_CAPACITY = 54_000
    }
}

fun createRandomPowerUp(
    type: PowerUpType,
    state: MviGameState,
    elapsedNs: Long = (state.elapsedSeconds * 1_000_000_000L).toLong(),
): MviPowerUp =
    MviPowerUp(
        id = "${type.name.lowercase()}-$elapsedNs-${Random.nextInt()}",
        x = randomCoordinate(state.canvasWidth - POWER_UP_RADIUS),
        y = randomCoordinate(state.canvasHeight - POWER_UP_RADIUS),
        type = type,
        createdNs = elapsedNs,
        radius = POWER_UP_RADIUS,
    )

private fun randomCoordinate(maximum: Double): Double =
    if (maximum <= POWER_UP_RADIUS) POWER_UP_RADIUS else Random.nextDouble(POWER_UP_RADIUS, maximum)

private const val POWER_UP_RADIUS = 15.0

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

package ru.rkhamatyarov.service

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

data class GameRoom(
    val id: String,
    val engine: GameEngine = GameEngine(),
    val history: StateHistory = StateHistory(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableStateFlow =
        MutableSharedFlow<ByteArray>(
            replay = 0,
            extraBufferCapacity = 3,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val stateFlow: SharedFlow<ByteArray> = mutableStateFlow.asSharedFlow()

    fun tryEmit(bytes: ByteArray): Boolean = mutableStateFlow.tryEmit(bytes)
}

@ApplicationScoped
class RoomRegistry {
    private val rooms = ConcurrentHashMap<String, GameRoom>()

    fun get(roomId: String): GameRoom = rooms.computeIfAbsent(roomId.ifBlank { DEFAULT_ROOM_ID }) { GameRoom(it) }

    fun activeRooms(): Collection<GameRoom> = rooms.values

    fun roomCount(): Int = rooms.size

    @PreDestroy
    fun shutdown() {
        rooms.values.forEach { it.scope.cancel() }
        rooms.clear()
    }

    companion object {
        const val DEFAULT_ROOM_ID = "default"
    }
}

package ru.rkhamatyarov.event

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.CopyOnWriteArrayList

sealed class GameEvent {
    data class GoalScored(
        val scoringPlayer: Int,
        val scoreA: Int,
        val scoreB: Int,
    ) : GameEvent()

    data class PuckHit(
        val x: Double,
        val y: Double,
    ) : GameEvent()

    data class LineDrawn(
        val pointCount: Int,
    ) : GameEvent()
}

@ApplicationScoped
class EventManager {
    private val listeners = CopyOnWriteArrayList<(GameEvent) -> Unit>()

    fun subscribe(handler: (GameEvent) -> Unit) {
        listeners.add(handler)
    }

    fun unsubscribe(handler: (GameEvent) -> Unit) {
        listeners.remove(handler)
    }

    fun emit(event: GameEvent) {
        listeners.forEach { it(event) }
    }
}

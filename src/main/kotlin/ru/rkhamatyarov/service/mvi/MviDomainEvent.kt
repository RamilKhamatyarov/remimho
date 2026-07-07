package ru.rkhamatyarov.service.mvi

enum class PaddleSide {
    A,
    B,
}

sealed interface MviDomainEvent {
    data class PaddleHit(
        val side: PaddleSide,
    ) : MviDomainEvent

    data object LineDeflect : MviDomainEvent
}

object MviDomainEvents {
    private val events = ThreadLocal<MutableList<MviDomainEvent>?>()

    fun record(event: MviDomainEvent) {
        events.get()?.add(event)
    }

    fun <T> capture(block: () -> T): CapturedResult<T> {
        val previous = events.get()
        val captured = mutableListOf<MviDomainEvent>()
        events.set(captured)
        return try {
            CapturedResult(block(), captured.toList())
        } finally {
            if (previous == null) {
                events.remove()
            } else {
                events.set(previous)
            }
        }
    }
}

data class CapturedResult<T>(
    val value: T,
    val events: List<MviDomainEvent>,
)

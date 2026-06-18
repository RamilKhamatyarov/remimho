package ru.rkhamatyarov.telemetry

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import ru.rkhamatyarov.service.ActorMailbox
import java.util.concurrent.atomic.AtomicInteger

class MonitoredMailbox<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) : ActorMailbox<T> {
    private val channel = Channel<T>(capacity = capacity, onBufferOverflow = onBufferOverflow)
    private val depthCounter = AtomicInteger(0)

    override val depth: Int
        get() = depthCounter.get()

    override fun trySend(value: T): Boolean {
        val sent = channel.trySend(value).isSuccess
        if (sent) {
            depthCounter.updateAndGet { current -> (current + 1).coerceAtMost(capacity) }
        }
        return sent
    }

    override suspend fun receive(): T? {
        val value = channel.receiveCatching().getOrNull() ?: return null
        depthCounter.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        return value
    }

    override fun close() {
        channel.close()
    }

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}

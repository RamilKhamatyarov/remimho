package ru.rkhamatyarov.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventManagerTest {
    private lateinit var manager: EventManager

    @BeforeEach
    fun setUp() {
        manager = EventManager()
    }

    @Test
    fun `emit calls registered listener`() {
        val received = mutableListOf<GameEvent>()
        manager.subscribe { received.add(it) }

        val event = GameEvent.GoalScored(scoringPlayer = 1, scoreA = 1, scoreB = 0)
        manager.emit(event)

        assertEquals(1, received.size)
        assertEquals(event, received[0])
    }

    @Test
    fun `emit calls all registered listeners`() {
        val count =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        repeat(3) { manager.subscribe { count.incrementAndGet() } }

        manager.emit(GameEvent.PuckHit(x = 100.0, y = 200.0))

        assertEquals(3, count.get())
    }

    @Test
    fun `emit with no listeners does not throw`() {
        manager.emit(GameEvent.LineDrawn(pointCount = 5))
    }

    @Test
    fun `unsubscribe removes listener`() {
        val received = mutableListOf<GameEvent>()
        val handler: (GameEvent) -> Unit = { received.add(it) }
        manager.subscribe(handler)
        manager.unsubscribe(handler)

        manager.emit(GameEvent.GoalScored(scoringPlayer = 2, scoreA = 0, scoreB = 1))

        assertTrue(received.isEmpty())
    }

    @Test
    fun `multiple event types are dispatched correctly`() {
        val goals = mutableListOf<GameEvent.GoalScored>()
        val hits = mutableListOf<GameEvent.PuckHit>()
        val lines = mutableListOf<GameEvent.LineDrawn>()

        manager.subscribe { e ->
            when (e) {
                is GameEvent.GoalScored -> goals.add(e)
                is GameEvent.PuckHit -> hits.add(e)
                is GameEvent.LineDrawn -> lines.add(e)
            }
        }

        manager.emit(GameEvent.GoalScored(1, 1, 0))
        manager.emit(GameEvent.PuckHit(50.0, 60.0))
        manager.emit(GameEvent.LineDrawn(10))

        assertEquals(1, goals.size)
        assertEquals(1, hits.size)
        assertEquals(1, lines.size)
    }

    @Test
    fun `concurrent emit and subscribe do not throw or lose events`() {
        val received = CopyOnWriteArrayList<GameEvent>()
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        val emitCount = 500

        // Pre-register a listener before threads start
        manager.subscribe { received.add(it) }

        // 4 threads emit events; 4 threads concurrently subscribe new listeners
        repeat(4) {
            executor.submit {
                latch.await()
                repeat(emitCount / 4) { manager.emit(GameEvent.PuckHit(it.toDouble(), 0.0)) }
            }
            executor.submit {
                latch.await()
                manager.subscribe { /* no-op listener added mid-flight */ }
            }
        }

        latch.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // At least the pre-registered listener must have received all emitted events
        assertTrue(received.size >= emitCount, "Expected >= $emitCount events, got ${received.size}")
    }
}

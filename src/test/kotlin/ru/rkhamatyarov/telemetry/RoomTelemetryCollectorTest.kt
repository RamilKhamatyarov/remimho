package ru.rkhamatyarov.telemetry

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.RoomRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoomTelemetryCollectorTest {
    @Test
    fun `collect registers active room gauges`() {
        val meterRegistry = SimpleMeterRegistry()
        val roomRegistry = RoomRegistry(MonitoredReducer(meterRegistry))
        val collector = RoomTelemetryCollector(roomRegistry, meterRegistry)
        collector.start()
        val room = roomRegistry.get("alpha")
        room.history.add(
            GameStateDelta
                .newBuilder()
                .setFullState(true)
                .build()
                .toByteArray(),
        )

        collector.collect()

        val activeRooms = meterRegistry.find(RoomTelemetryCollector.ACTIVE_ROOMS_METRIC).gauge()
        val historySize =
            meterRegistry
                .find(RoomTelemetryCollector.HISTORY_SIZE_METRIC)
                .tag("room_id", "alpha")
                .gauge()
        val mailboxDepth =
            meterRegistry
                .find(RoomTelemetryCollector.MAILBOX_DEPTH_METRIC)
                .tag("room_id", "alpha")
                .gauge()

        assertEquals(1.0, activeRooms?.value())
        assertEquals(1.0, historySize?.value())
        assertNotNull(mailboxDepth)
        roomRegistry.shutdown()
    }
}

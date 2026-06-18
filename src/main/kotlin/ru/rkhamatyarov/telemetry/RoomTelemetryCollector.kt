package ru.rkhamatyarov.telemetry

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.scheduler.Scheduled
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.service.GameRoom
import ru.rkhamatyarov.service.RoomRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.function.ToDoubleFunction

@ApplicationScoped
class RoomTelemetryCollector
    @Inject
    constructor(
        private val roomRegistry: RoomRegistry,
        private val meterRegistry: MeterRegistry,
    ) {
        private val roomMeters = ConcurrentHashMap<String, List<Meter.Id>>()

        @PostConstruct
        fun start() {
            Gauge
                .builder(ACTIVE_ROOMS_METRIC, roomRegistry, ToDoubleFunction { it.roomCount().toDouble() })
                .register(meterRegistry)
        }

        @Scheduled(every = "10s")
        fun collect() {
            val activeRooms = roomRegistry.activeRooms().associateBy { it.id }
            activeRooms.values.forEach { room ->
                roomMeters.computeIfAbsent(room.id) { registerRoomMeters(room) }
            }
            roomMeters.keys
                .filterNot(activeRooms::containsKey)
                .forEach(::removeRoomMeters)
        }

        private fun registerRoomMeters(room: GameRoom): List<Meter.Id> =
            listOf(
                Gauge
                    .builder(MAILBOX_DEPTH_METRIC, room, ToDoubleFunction { it.mailboxDepth.toDouble() })
                    .tag(ROOM_ID_TAG, room.id)
                    .register(meterRegistry)
                    .id,
                Gauge
                    .builder(HISTORY_SIZE_METRIC, room, ToDoubleFunction { it.history.size().toDouble() })
                    .tag(ROOM_ID_TAG, room.id)
                    .register(meterRegistry)
                    .id,
            )

        private fun removeRoomMeters(roomId: String) {
            roomMeters.remove(roomId)?.forEach { meterRegistry.remove(it) }
        }

        companion object {
            const val ACTIVE_ROOMS_METRIC = "remimho.rooms.active"
            const val MAILBOX_DEPTH_METRIC = "remimho.room.mailbox.depth"
            const val HISTORY_SIZE_METRIC = "remimho.room.history.size"
            private const val ROOM_ID_TAG = "room_id"
        }
    }

package ru.rkhamatyarov.telemetry

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import java.util.concurrent.TimeUnit

@ApplicationScoped
class MonitoredReducer
    @Inject
    constructor(
        meterRegistry: MeterRegistry,
    ) {
        private val timer: Timer = meterRegistry.timer(REDUCER_DURATION_METRIC)

        fun wrap(reducer: (MviGameState, GameAction) -> MviGameState): (MviGameState, GameAction) -> MviGameState =
            { state, action ->
                val startedNs = System.nanoTime()
                try {
                    reducer(state, action)
                } finally {
                    timer.record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS)
                }
            }

        constructor() : this(SimpleMeterRegistry())

        companion object {
            const val REDUCER_DURATION_METRIC = "remimho.reducer.duration"
        }
    }

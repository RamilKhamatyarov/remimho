package ru.rkhamatyarov.telemetry

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.reduce
import kotlin.test.assertEquals

class MonitoredReducerTest {
    @Test
    fun `records reducer execution time`() {
        val meterRegistry = SimpleMeterRegistry()
        val reducer = MonitoredReducer(meterRegistry).wrap(::reduce)

        val state = reducer(MviGameState(), GameAction.MovePaddle(123.0))

        assertEquals(123.0, state.paddle2Y, 0.001)
        assertEquals(1, meterRegistry.find(MonitoredReducer.REDUCER_DURATION_METRIC).timer()?.count())
    }
}

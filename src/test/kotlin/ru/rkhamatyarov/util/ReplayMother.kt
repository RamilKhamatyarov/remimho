package ru.rkhamatyarov.util

import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState

internal object ReplayMother {
    fun replay(
        version: String,
        start: MviGameState,
    ): ReplayFile =
        ReplayFile.newBuilder()
            .setVersion(version)
            .setStartingState(ReplayConverter.stateToSnapshot(start))
            .addAllIntents(
                (1..60).map { tick ->
                    ReplayConverter.toProto(
                        GameIntent.Reliable(
                            GameAction.Tick(
                                deltaSeconds = 0.016,
                                elapsedNs = tick * 16_000_000L,
                            ),
                        ),
                    )
                },
            )
            .build()
}

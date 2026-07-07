package ru.rkhamatyarov.replay

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.service.mvi.MviDomainEvents
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.service.turbo.TurboBoostStrategy
import ru.rkhamatyarov.service.turbo.TurboSnapshot

@ApplicationScoped
class HeadlessReplayImporter {
    @ConfigProperty(name = "remimho.replay.snapshot-interval-frames", defaultValue = "60")
    var snapshotIntervalFrames: Int = 60

    fun import(replayFile: ReplayFile): HeadlessImportResult {
        val startingState =
            if (replayFile.hasStartingState()) {
                ReplayConverter.snapshotToState(replayFile.startingState)
            } else {
                MviGameState()
            }

        var state = startingState
        val turboBoostStrategy = TurboBoostStrategy()
        val snapshots = mutableListOf<Pair<Long, ByteArray>>()
        var frameIndex = 0

        for (replayIntent in replayFile.intentsList) {
            val (intent, intentElapsedNs) = ReplayConverter.fromProto(replayIntent)
            val elapsedNs = if (intentElapsedNs > 0L) intentElapsedNs else (state.elapsedSeconds * 1_000_000_000L).toLong()
            turboBoostStrategy.onAction(intent.action, elapsedNs)
            val captured = MviDomainEvents.capture { reduce(state, intent.action) }
            state = captured.value
            turboBoostStrategy.onEvents(captured.events, elapsedNs)
            frameIndex++
            if (frameIndex % snapshotIntervalFrames == 0) {
                val logicalNs = (state.elapsedSeconds * 1_000_000_000L).toLong()
                snapshots.add(logicalNs to state.toDelta().toByteArray())
            }
        }

        return HeadlessImportResult(
            finalState = state,
            snapshots = snapshots,
            frameCount = frameIndex,
            turboSnapshot = turboBoostStrategy.snapshot((state.elapsedSeconds * 1_000_000_000L).toLong()),
        )
    }
}

data class HeadlessImportResult(
    val finalState: MviGameState,
    val snapshots: List<Pair<Long, ByteArray>>,
    val frameCount: Int,
    val turboSnapshot: TurboSnapshot = TurboSnapshot.initial(),
)

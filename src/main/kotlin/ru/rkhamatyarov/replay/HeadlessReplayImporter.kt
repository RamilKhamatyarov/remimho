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

    fun import(replayFile: ReplayFile): HeadlessImportResult = importInternal(replayFile).result

    fun importSampledStates(
        replayFile: ReplayFile,
        sampleEveryFrames: Int,
    ): List<MviGameState> {
        require(sampleEveryFrames > 0) { "sampleEveryFrames must be positive" }
        return importInternal(replayFile, sampleEveryFrames).sampledStates
    }

    private fun importInternal(
        replayFile: ReplayFile,
        sampleEveryFrames: Int? = null,
    ): HeadlessImportRun {
        val startingState =
            if (replayFile.hasStartingState()) {
                ReplayConverter.snapshotToState(replayFile.startingState)
            } else {
                MviGameState()
            }

        var state = startingState
        val turboBoostStrategy = TurboBoostStrategy()
        val snapshots = mutableListOf<Pair<Long, ByteArray>>()
        val sampledStates = mutableListOf<MviGameState>()
        var frameIndex = 0
        if (sampleEveryFrames != null) sampledStates.add(state)

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
            if (sampleEveryFrames != null && frameIndex % sampleEveryFrames == 0) {
                sampledStates.add(state)
            }
        }

        return HeadlessImportRun(
            result =
                HeadlessImportResult(
                    finalState = state,
                    snapshots = snapshots,
                    frameCount = frameIndex,
                    turboSnapshot = turboBoostStrategy.snapshot((state.elapsedSeconds * 1_000_000_000L).toLong()),
                ),
            sampledStates = sampledStates,
        )
    }
}

private data class HeadlessImportRun(
    val result: HeadlessImportResult,
    val sampledStates: List<MviGameState>,
)

data class HeadlessImportResult(
    val finalState: MviGameState,
    val snapshots: List<Pair<Long, ByteArray>>,
    val frameCount: Int,
    val turboSnapshot: TurboSnapshot = TurboSnapshot.initial(),
)

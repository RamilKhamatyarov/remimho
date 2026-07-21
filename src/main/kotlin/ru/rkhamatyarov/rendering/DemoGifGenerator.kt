package ru.rkhamatyarov.rendering

import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviScore
import ru.rkhamatyarov.service.mvi.PaddleSide
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.math.PI
import kotlin.math.sin

object DemoGifGenerator {
    private const val SAMPLE_EVERY_FRAMES = 5
    private const val FRAME_DELAY_MS = 83

    @JvmStatic
    fun main(args: Array<String>) {
        val projectDir = Path.of(args.getOrNull(0) ?: ".").toAbsolutePath().normalize()
        generate(projectDir)
    }

    fun generate(projectDir: Path) {
        val replayPath = projectDir.resolve("src/test/resources/demo.replay")
        val outputPath = projectDir.resolve("docs/demo.gif")

        if (Files.notExists(replayPath)) {
            writeSampleReplay(replayPath)
        }

        val replay = replayPath.inputStream().use { ReplayFile.parseFrom(it) }
        val states = HeadlessReplayImporter().importSampledStates(replay, SAMPLE_EVERY_FRAMES)
        val renderer = GameRenderer()
        AnimatedGifWriter.write(states.map(renderer::render), outputPath, FRAME_DELAY_MS)
    }

    private fun writeSampleReplay(path: Path) {
        Files.createDirectories(path.parent)
        val startingState =
            MviGameState(
                score = MviScore(playerA = 1, playerB = 2),
            )
        val line =
            MviLine(
                id = "demo-line",
                points =
                    listOf(
                        MviPoint(260.0, 420.0),
                        MviPoint(330.0, 360.0),
                        MviPoint(420.0, 390.0),
                        MviPoint(505.0, 335.0),
                    ),
                width = 6.0,
            )
        val intents = mutableListOf(GameIntent.Reliable(GameAction.CommitLine(line)))
        repeat(180) { frame ->
            val elapsedNs = frame * 16_000_000L
            if (frame % 6 == 0) {
                val paddleY = 250.0 + sin(frame / 24.0 * PI) * 110.0
                intents += GameIntent.Reliable(GameAction.MovePaddle(paddleY, PaddleSide.B))
            }
            intents += GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs))
        }

        val replay =
            ReplayFile
                .newBuilder()
                .setVersion("1")
                .setRoomId("demo")
                .setStartingState(ReplayConverter.stateToSnapshot(startingState))
                .addAllIntents(intents.mapNotNull(ReplayConverter::toProto))
                .build()

        path.outputStream().use(replay::writeTo)
    }
}

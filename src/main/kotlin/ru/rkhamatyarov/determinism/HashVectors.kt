package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.mvi.MviActivePowerUp
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.PuckTouch
import ru.rkhamatyarov.service.mvi.TouchLedger
import ru.rkhamatyarov.service.mvi.TouchSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixed states whose hashes are committed, so any machine can prove it hashes the way the
 * reference machine did. They cover the awkward values: NaN, signed zero, extremes, nulls.
 */
object HashVectors {
    const val RESOURCE = "src/test/resources/determinism/vectors/mvi-states.txt"

    fun states(): Map<String, MviGameState> =
        linkedMapOf(
            "default" to MviGameState(),
            "populated" to populated(),
            "negative-zero" to MviGameState(puck = MviPuck(vx = -0.0, vy = 0.0)),
            "positive-zero" to MviGameState(puck = MviPuck(vx = 0.0, vy = 0.0)),
            "not-a-number" to MviGameState(puck = MviPuck(spin = Double.NaN)),
            "extremes" to
                MviGameState(
                    puck = MviPuck(x = Double.MAX_VALUE, y = Double.MIN_VALUE, vx = -Double.MAX_VALUE, vy = 1.0e-310),
                    elapsedSeconds = Double.POSITIVE_INFINITY,
                    score = MviScore(Int.MAX_VALUE, Int.MIN_VALUE),
                ),
        )

    fun computed(): Map<String, Long> = states().mapValues { (_, state) -> MviGameStateEncoder.hashOf(state) }

    fun readExpected(projectDir: Path): Map<String, Long> =
        Files
            .readAllLines(projectDir.resolve(RESOURCE))
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val (name, hash) = line.trim().split(' ')
                name to java.lang.Long.parseUnsignedLong(hash, HEX_RADIX)
            }

    fun write(projectDir: Path) {
        val target = projectDir.resolve(RESOURCE)
        Files.createDirectories(target.parent)
        val lines =
            listOf("# name hash; regenerate with: gradlew determinismUpdateReferences") +
                computed().map { (name, hash) -> "$name ${hex(hash)}" }
        Files.write(target, lines)
    }

    /** Names whose computed hash differs from the committed one, with both values. */
    fun mismatches(projectDir: Path): List<String> {
        val expected = readExpected(projectDir)
        val actual = computed()
        return (expected.keys + actual.keys).mapNotNull { name ->
            val want = expected[name]
            val got = actual[name]
            if (want == got) {
                null
            } else {
                "$name: expected ${want?.let(::hex) ?: "<absent>"}, computed ${got?.let(::hex) ?: "<absent>"}"
            }
        }
    }

    private fun populated(): MviGameState =
        MviGameState(
            puck =
                MviPuck(
                    x = 123.456,
                    y = 78.9,
                    vx = -321.5,
                    vy = 0.1,
                    spin = -0.75,
                    spinRemainingNs = 5L,
                    lastTeleportPairId = "p1",
                ),
            score = MviScore(3, 2),
            paddle1Y = 10.5,
            paddle2Y = 480.25,
            paddle1Velocity = -4.0,
            paddle2Velocity = 7.5,
            paused = true,
            lines =
                listOf(
                    MviLine(
                        "l1",
                        listOf(MviPoint(1.0, 2.0), MviPoint(3.5, 4.5)),
                        ownerSide = PaddleSide.A,
                        combinationId = "wall",
                    ),
                    MviLine("l2", emptyList(), width = 9.0),
                ),
            teleports = mapOf("zeta" to "alpha", "alpha" to "zeta"),
            elapsedSeconds = 42.125,
            powerUps = listOf(MviPowerUp("u1", 400.0, 300.0, PowerUpType.RABBIT_MODE, createdNs = 9L)),
            activePowerUps =
                listOf(
                    MviActivePowerUp(
                        PowerUpType.PADDLE_SHIELD,
                        activatedNs = 1L,
                        durationNs = 2L,
                        rabbitTurnsApplied = 3L,
                    ),
                ),
            speedMultiplier = 1.5,
            paddleShield = true,
            touchLedger =
                TouchLedger(
                    listOf(
                        PuckTouch(TouchSource.PADDLE, PaddleSide.B, "paddle-b", 100L, 512.5),
                        PuckTouch(TouchSource.WALL, null, "top", 200L, 300.0),
                    ),
                ),
            serveSide = PaddleSide.A,
        )

    private const val HEX_RADIX = 16
}

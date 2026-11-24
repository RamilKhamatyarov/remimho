package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.proto.PaddleTarget
import ru.rkhamatyarov.proto.Serve
import ru.rkhamatyarov.proto.TapeHeader
import ru.rkhamatyarov.proto.TapeRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Deterministic input scripts for the tunnel golden fixtures.
 *
 * Each scenario declares its starting tunnel state, its input records and the step reach of the
 * tape. The same definitions feed the updater that writes the candidates and the tests that verify
 * the committed goldens, so a scenario can never drift from the physics it pins.
 */
object TunnelGoldenScenarios {
    val all =
        listOf(
            Scenario(
                name = "zero-spin-wall-bounce",
                initialState = "puck-spin=0,puck-vel=(90,0,0)",
                steps = 360L,
                records = listOf(serve(vx = 4L, vz = 0L)),
                qualitativeInvariant = "zero initial spin remains zero through wall bounces",
            ),
            Scenario(
                name = "constant-spin-curve",
                initialState = "puck-spin-x=0.5,puck-vel=(0,0,0)",
                steps = 360L,
                records = listOf(serve(vx = 0L, vz = 0L)),
                qualitativeInvariant = "nonzero constant spin curves the puck away from its straight path",
            ),
            Scenario(
                name = "known-sweep-paddle-contact",
                initialState = "puck-sweeps-through-side-a-paddle-plane,paddle-a=(400,300)",
                steps = 120L,
                records =
                    listOf(
                        serve(vx = 0L, vz = -20L),
                        sweepPaddleTarget(),
                    ),
                qualitativeInvariant = "a swept puck contacts the paddle when its translated rectangle covers it",
            ),
        )

    fun byName(name: String): Scenario =
        all.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown tunnel golden scenario '$name'")

    /** Starting state for [scenario]; the tape supplies the ordered inputs, not the state. */
    fun initialState(scenario: Scenario): TunnelState {
        val rules = TunnelRules()
        val base = TunnelState(rules = rules)
        return when (scenario.name) {
            "constant-spin-curve" ->
                base.copy(
                    puck = base.puck.copy(spin = TunnelVector2(TunnelMath.fromRatio(1L, 2L), TunnelScalar.ZERO)),
                )
            "known-sweep-paddle-contact" ->
                base.copy(
                    puck =
                        base.puck.copy(
                            position =
                                TunnelVector3(
                                    TunnelMath.fromInt(500L),
                                    TunnelMath.fromInt(340L),
                                    TunnelMath.fromInt(150L),
                                ),
                        ),
                    paddleA =
                        TunnelPaddle(
                            position = TunnelVector2(TunnelMath.fromInt(400L), TunnelMath.fromInt(300L)),
                            target = TunnelVector2(TunnelMath.fromInt(400L), TunnelMath.fromInt(300L)),
                        ),
                )
            else -> base
        }
    }

    fun serialize(scenario: Scenario): ByteArray {
        val output = ByteArrayOutputStream()
        TunnelTapeWriter(output, header(), markIntervalSteps = Long.MAX_VALUE).use { writer ->
            scenario.records.forEach(writer::append)
            writer.mark(scenario.steps)
        }
        return output.toByteArray()
    }

    fun parse(scenario: Scenario): TunnelTape =
        TunnelTapeReader.read(
            ByteArrayInputStream(serialize(scenario)),
            setOf(PHYSICS_VERSION),
            setOf(SUBSTRATE),
        )

    fun header(): TapeHeader {
        val rules = TunnelRules()
        return TapeHeader.newBuilder()
            .setFormatVersion(TunnelTapeWriter.FORMAT_VERSION)
            .setPhysicsVersion(rules.physicsVersion)
            .setSubstrateId(SUBSTRATE)
            .setStepDurationNs(rules.stepDurationNs)
            .setWidthRaw(rules.width.raw)
            .setHeightRaw(rules.height.raw)
            .setDepthRaw(rules.depth.raw)
            .setInitialState(com.google.protobuf.ByteString.copyFromUtf8("scenario-initial-state"))
            .build()
    }

    private fun serve(
        vx: Long,
        vz: Long,
    ): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.SERVE)
            .setStep(1L)
            .setServe(
                Serve.newBuilder()
                    .setVxRaw(TunnelMath.fromInt(vx).raw)
                    .setVyRaw(TunnelScalar.ZERO.raw)
                    .setVzRaw(TunnelMath.fromInt(vz).raw),
            )
            .build()

    private fun sweepPaddleTarget(): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.PADDLE_TARGET)
            .setStep(1L)
            .setPaddleTarget(
                PaddleTarget.newBuilder()
                    .setSide(PaddleTarget.Side.A)
                    .setXRaw(TunnelMath.fromInt(460L).raw)
                    .setYRaw(TunnelMath.fromInt(300L).raw),
            )
            .build()

    data class Scenario(
        val name: String,
        val initialState: String,
        val steps: Long,
        val records: List<TapeRecord>,
        val qualitativeInvariant: String,
    )

    val PHYSICS_VERSION: String = TunnelRules.PHYSICS_VERSION
    private const val SUBSTRATE = "q32.32-provisional"
}

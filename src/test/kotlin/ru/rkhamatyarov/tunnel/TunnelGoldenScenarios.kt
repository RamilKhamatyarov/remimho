package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.proto.PaddleTarget
import ru.rkhamatyarov.proto.Serve
import ru.rkhamatyarov.proto.TapeHeader
import ru.rkhamatyarov.proto.TapeRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Deterministic input scripts for validating the golden fixture pipeline before physics exists. */
internal object TunnelGoldenScenarios {
    val all =
        listOf(
            Scenario(
                name = "zero-spin-wall-bounce",
                initialState = "puck-spin=0",
                records = listOf(serve(step = 1L, vx = 0L, vy = 0L, vz = 180L)),
                qualitativeInvariant = "zero initial spin remains zero through a wall bounce",
            ),
            Scenario(
                name = "constant-spin-curve",
                initialState = "puck-spin-x=0.5,puck-spin-y=0",
                records = listOf(serve(step = 1L, vx = 20L, vy = 0L, vz = 120L)),
                qualitativeInvariant = "nonzero constant spin curves the puck away from its straight path",
            ),
            Scenario(
                name = "known-sweep-paddle-contact",
                initialState = "puck-sweeps-through-side-a-paddle-plane",
                records = listOf(paddleTarget(step = 1L, side = PaddleTarget.Side.A, x = 0L, y = 0L)),
                qualitativeInvariant = "a swept puck contacts the paddle when its translated rectangle covers it",
            ),
        )

    fun serialize(scenario: Scenario): ByteArray {
        val output = ByteArrayOutputStream()
        TunnelTapeWriter(output, header(scenario.initialState), markIntervalSteps = Long.MAX_VALUE).use { writer ->
            scenario.records.forEach(writer::append)
        }
        return output.toByteArray()
    }

    fun parse(scenario: Scenario): TunnelTape =
        TunnelTapeReader.read(
            ByteArrayInputStream(serialize(scenario)),
            setOf(PHYSICS_VERSION),
            setOf(SUBSTRATE),
        )

    private fun header(initialState: String): TapeHeader =
        TapeHeader.newBuilder()
            .setFormatVersion(TunnelTapeWriter.FORMAT_VERSION)
            .setPhysicsVersion(PHYSICS_VERSION)
            .setSubstrateId(SUBSTRATE)
            .setStepDurationNs(16_666_667L)
            .setWidthRaw(TunnelMath.fromInt(800L).raw)
            .setHeightRaw(TunnelMath.fromInt(600L).raw)
            .setDepthRaw(TunnelMath.fromInt(300L).raw)
            .setInitialState(com.google.protobuf.ByteString.copyFromUtf8(initialState))
            .build()

    private fun serve(
        step: Long,
        vx: Long,
        vy: Long,
        vz: Long,
    ): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.SERVE)
            .setStep(step)
            .setServe(
                Serve.newBuilder()
                    .setVxRaw(TunnelMath.fromInt(vx).raw)
                    .setVyRaw(TunnelMath.fromInt(vy).raw)
                    .setVzRaw(TunnelMath.fromInt(vz).raw),
            )
            .build()

    private fun paddleTarget(
        step: Long,
        side: PaddleTarget.Side,
        x: Long,
        y: Long,
    ): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.PADDLE_TARGET)
            .setStep(step)
            .setPaddleTarget(
                PaddleTarget.newBuilder()
                    .setSide(side)
                    .setXRaw(TunnelMath.fromInt(x).raw)
                    .setYRaw(TunnelMath.fromInt(y).raw),
            )
            .build()

    data class Scenario(
        val name: String,
        val initialState: String,
        val records: List<TapeRecord>,
        val qualitativeInvariant: String,
    )

    private const val PHYSICS_VERSION = "tunnel-harness-v1"
    private const val SUBSTRATE = "q32.32-provisional"
}

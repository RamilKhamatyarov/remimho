package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.FieldSink
import ru.rkhamatyarov.determinism.HashSink
import ru.rkhamatyarov.determinism.StateEncoder

data class TunnelVector2(val x: TunnelScalar, val y: TunnelScalar)

data class TunnelVector3(val x: TunnelScalar, val y: TunnelScalar, val z: TunnelScalar)

data class TunnelPuck(
    val position: TunnelVector3,
    val velocity: TunnelVector3,
    val spin: TunnelVector2,
    val radius: TunnelScalar,
)

data class TunnelPaddle(
    val position: TunnelVector2,
    val target: TunnelVector2,
)

enum class TunnelSide { A, B }

sealed interface TunnelInput {
    data class PaddleTarget(val side: TunnelSide, val position: TunnelVector2) : TunnelInput

    data class Serve(val velocity: TunnelVector3) : TunnelInput

    data object Reset : TunnelInput
}

data class TunnelRules(
    val physicsVersion: String = PROVISIONAL_PHYSICS_VERSION,
    val stepDurationNs: Long = DEFAULT_STEP_NS,
    val width: TunnelScalar = TunnelMath.fromInt(800L),
    val height: TunnelScalar = TunnelMath.fromInt(600L),
    val depth: TunnelScalar = TunnelMath.fromInt(300L),
    val paddleTravelPerStep: TunnelScalar = TunnelMath.fromInt(8L),
) {
    init {
        require(physicsVersion.isNotBlank()) { "physicsVersion must not be blank" }
        require(stepDurationNs > 0L) { "stepDurationNs must be positive" }
        require(
            width.raw in 1L..MAX_DIMENSION_RAW &&
                height.raw in 1L..MAX_DIMENSION_RAW &&
                depth.raw in 1L..MAX_DIMENSION_RAW,
        ) {
            "tunnel dimensions must be in the supported range"
        }
        require(paddleTravelPerStep.raw >= 0L) { "paddleTravelPerStep must not be negative" }
    }

    companion object {
        const val PROVISIONAL_PHYSICS_VERSION = "tunnel-skeleton-v1"
        const val DEFAULT_STEP_NS = 8_333_333L
        const val MAX_DIMENSION_RAW = 42_949_672_960_000L
    }
}

data class TunnelState(
    val puck: TunnelPuck =
        TunnelPuck(
            position = TunnelVector3(TunnelMath.fromInt(400L), TunnelMath.fromInt(300L), TunnelMath.fromInt(150L)),
            velocity = TunnelVector3(TunnelScalar.ZERO, TunnelScalar.ZERO, TunnelScalar.ZERO),
            spin = TunnelVector2(TunnelScalar.ZERO, TunnelScalar.ZERO),
            radius = TunnelMath.fromRatio(1L, 2L),
        ),
    val paddleA: TunnelPaddle =
        TunnelPaddle(
            TunnelVector2(TunnelScalar.ZERO, TunnelScalar.ZERO),
            TunnelVector2(TunnelScalar.ZERO, TunnelScalar.ZERO),
        ),
    val paddleB: TunnelPaddle =
        TunnelPaddle(
            TunnelVector2(TunnelScalar.ZERO, TunnelScalar.ZERO),
            TunnelVector2(TunnelScalar.ZERO, TunnelScalar.ZERO),
        ),
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val step: Long = 0L,
    val rules: TunnelRules = TunnelRules(),
)

/** Canonical state-field order for tunnel fingerprints and debugging dumps. */
object TunnelStateEncoder : StateEncoder<TunnelState> {
    const val VERSION = 1
    val EXCLUDED_PATHS: Set<String> = emptySet()

    override fun encode(
        state: TunnelState,
        sink: FieldSink,
    ) {
        sink.long("puck.position.x.raw", state.puck.position.x.raw)
        sink.long("puck.position.y.raw", state.puck.position.y.raw)
        sink.long("puck.position.z.raw", state.puck.position.z.raw)
        sink.long("puck.velocity.x.raw", state.puck.velocity.x.raw)
        sink.long("puck.velocity.y.raw", state.puck.velocity.y.raw)
        sink.long("puck.velocity.z.raw", state.puck.velocity.z.raw)
        sink.long("puck.spin.x.raw", state.puck.spin.x.raw)
        sink.long("puck.spin.y.raw", state.puck.spin.y.raw)
        sink.long("puck.radius.raw", state.puck.radius.raw)
        encodePaddle("paddleA", state.paddleA, sink)
        encodePaddle("paddleB", state.paddleB, sink)
        sink.int("scoreA", state.scoreA)
        sink.int("scoreB", state.scoreB)
        sink.long("step", state.step)
        encodeRules(state.rules, sink)
    }

    fun hashOf(state: TunnelState): Long = HashSink().also { encode(state, it) }.value

    private fun encodePaddle(
        prefix: String,
        paddle: TunnelPaddle,
        sink: FieldSink,
    ) {
        sink.long("$prefix.position.x.raw", paddle.position.x.raw)
        sink.long("$prefix.position.y.raw", paddle.position.y.raw)
        sink.long("$prefix.target.x.raw", paddle.target.x.raw)
        sink.long("$prefix.target.y.raw", paddle.target.y.raw)
    }

    private fun encodeRules(
        rules: TunnelRules,
        sink: FieldSink,
    ) {
        sink.text("rules.physicsVersion", rules.physicsVersion)
        sink.long("rules.stepDurationNs", rules.stepDurationNs)
        sink.long("rules.width.raw", rules.width.raw)
        sink.long("rules.height.raw", rules.height.raw)
        sink.long("rules.depth.raw", rules.depth.raw)
        sink.long("rules.paddleTravelPerStep.raw", rules.paddleTravelPerStep.raw)
    }
}

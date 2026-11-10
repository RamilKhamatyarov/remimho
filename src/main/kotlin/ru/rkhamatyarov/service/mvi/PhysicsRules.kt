package ru.rkhamatyarov.service.mvi

/** Fixed replay rules; selected outside state and passed explicitly through physics. */
internal enum class PhysicsRules(
    val replayVersion: String,
    val legacyGhostMode: Boolean = false,
) {
    V1("1", legacyGhostMode = true),
    V2("2"),
    V3("3"),
    ;

    companion object {
        val CURRENT: PhysicsRules = V3

        /** Missing versions use the original rules; unknown versions must not silently upgrade. */
        fun fromReplayVersion(version: String): PhysicsRules =
            if (version.isEmpty()) {
                V1
            } else {
                entries.firstOrNull { it.replayVersion == version }
                    ?: throw IllegalArgumentException("Unsupported replay version: $version")
            }
    }
}

internal object FootballSpin {
    const val DURATION_NS = 2_000_000_000L
    const val HALF_LIFE_NS = 800_000_000L
    const val MAX_SPIN = 1.5
    const val MIN_SPIN = 0.02
    const val PADDLE_NORMALIZER = 45.0
    const val EDGE_GAIN = 1.25
    const val CURVE_ACCELERATION = 400.0
    const val EDGE_TANGENTIAL_GAIN = 12.0
    const val MAX_EDGE_TANGENTIAL_IMPULSE = 360.0
}

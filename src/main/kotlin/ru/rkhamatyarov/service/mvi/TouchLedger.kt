package ru.rkhamatyarov.service.mvi

enum class TouchSource {
    PADDLE,
    WALL,
    DRAWN_LINE,
    BUMPER,
    POWER_UP,
}

data class PuckTouch(
    val source: TouchSource,
    val ownerSide: PaddleSide?,
    val identifier: String,
    val elapsedNs: Long,
    val speedAtContact: Double,
)

data class TouchLedger(
    val entries: List<PuckTouch> = emptyList(),
) {
    init {
        require(entries.size <= MAX_ENTRIES) { "Touch ledger cannot exceed $MAX_ENTRIES entries" }
    }

    fun append(touch: PuckTouch): TouchLedger = copy(entries = (entries + touch).takeLast(MAX_ENTRIES))

    companion object {
        const val MAX_ENTRIES = 8
    }
}

data class OneTimerConfig(
    val minimumIncomingSpeed: Double = 500.0,
    val freshnessWindowNs: Long = 2_000_000_000L,
    val minimumMultiplier: Double = 1.25,
    val maximumMultiplier: Double = 1.50,
    val fullStrengthIncomingSpeed: Double = 800.0,
    val maximumRawSpeed: Double = 800.0,
) {
    init {
        require(minimumIncomingSpeed.isFinite() && minimumIncomingSpeed >= 0.0)
        require(freshnessWindowNs >= 0L)
        require(minimumMultiplier.isFinite() && minimumMultiplier >= 1.0)
        require(maximumMultiplier.isFinite() && maximumMultiplier >= minimumMultiplier)
        require(fullStrengthIncomingSpeed.isFinite() && fullStrengthIncomingSpeed >= minimumIncomingSpeed)
        require(maximumRawSpeed.isFinite() && maximumRawSpeed > 0.0)
    }
}

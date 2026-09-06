package ru.rkhamatyarov.service.mvi

/** Replay-owned balance settings; missing historical settings disable the mechanics. */
data class Combo(
    val enabled: Boolean = true,
    val giveAndGoMultiplier: Double = 2.5,
    val maximumRawSpeed: Double = 800.0,
    val giveAndGoWindowNs: Long = 3_000_000_000L,
    val superGoalThreshold: Int = 4,
    val superGoalWindowNs: Long = 10_000_000_000L,
) {
    init {
        require(giveAndGoMultiplier.isFinite() && giveAndGoMultiplier >= 1.0)
        require(maximumRawSpeed.isFinite() && maximumRawSpeed > 0.0)
        require(giveAndGoWindowNs >= 0L && superGoalWindowNs >= 0L)
        require(superGoalThreshold in 2..TouchLedger.MAX_ENTRIES)
    }

    companion object {
        val DISABLED = Combo(enabled = false)
    }
}

package ru.rkhamatyarov.service.mvi

/** Pure combo evaluations over the bounded contact history. */
internal object ComboMechanics {
    fun isGiveAndGo(
        ledger: TouchLedger,
        side: PaddleSide,
        elapsedNs: Long,
        config: Combo,
    ): Boolean {
        if (!config.enabled) return false
        val contacts = collapseDuplicates(ledger.entries).takeLast(2)
        if (contacts.size != 2) return false
        val (paddle, line) = contacts
        return paddle.source == TouchSource.PADDLE && paddle.ownerSide == side &&
            line.source == TouchSource.DRAWN_LINE && line.ownerSide == side &&
            paddle.isFresh(elapsedNs, config.giveAndGoWindowNs) &&
            line.elapsedNs in paddle.elapsedNs..elapsedNs
    }

    /** Returns zero unless the recent uninterrupted suffix qualifies for a Super Goal. */
    fun superGoalChainLength(
        ledger: TouchLedger,
        side: PaddleSide,
        elapsedNs: Long,
        config: Combo,
    ): Int {
        if (!config.enabled) return 0
        val recent =
            ledger.entries
                .asReversed()
                .takeWhile {
                    it.isFresh(elapsedNs, config.superGoalWindowNs) && (it.isOwnedBy(side) || it.isNeutral())
                }.asReversed()
                .filterNot { it.isNeutral() }
        val chain = collapseDuplicates(recent)
        if (chain.size < config.superGoalThreshold) return 0
        if (chain.none { it.source == TouchSource.PADDLE }) return 0
        if (chain.none { it.source == TouchSource.DRAWN_LINE }) return 0
        return chain.size
    }

    private fun collapseDuplicates(contacts: List<PuckTouch>): List<PuckTouch> =
        contacts.fold(emptyList()) { result, contact ->
            val previous = result.lastOrNull()
            if (previous?.source == contact.source && previous.ownerSide == contact.ownerSide &&
                previous.identifier == contact.identifier
            ) {
                result
            } else {
                result + contact
            }
        }

    private fun PuckTouch.isOwnedBy(side: PaddleSide): Boolean =
        ownerSide == side && (source == TouchSource.PADDLE || source == TouchSource.DRAWN_LINE)

    private fun PuckTouch.isNeutral(): Boolean =
        ownerSide == null && (source == TouchSource.POWER_UP || source == TouchSource.BUMPER)

    private fun PuckTouch.isFresh(
        elapsedNs: Long,
        windowNs: Long,
    ): Boolean = this.elapsedNs >= 0L && elapsedNs >= this.elapsedNs && elapsedNs - this.elapsedNs <= windowNs
}

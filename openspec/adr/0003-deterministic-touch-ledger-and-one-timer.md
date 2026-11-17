# Use serialized touch history for deterministic combo mechanics

Status: accepted

Puck combo mechanics will share a bounded `TouchLedger` stored in immutable `MviGameState`. Each of its eight entries records a stable contact source, optional `PaddleSide` owner, object identifier, logical `elapsedNs`, and effective incoming speed before collision resolution. New player-drawn lines carry optional side ownership while old and system-generated lines remain ownerless. State-history frames and replay snapshots serialize the ledger, line ownership, and One-Timer configuration so rewind and replay restore the exact eligibility context and future balance defaults cannot alter new replay files.

The first consumer is One-Timer: a direct opponent-paddle return with no intervening contact, at least `500 px/s` effective incoming speed, and no more than two logical seconds between touches. New matches scale outgoing raw velocity by `2.0x` and cap it at `800 px/s`, without changing score or requiring a separate cooldown. Serialized replay configuration preserves the original multiplier for older matches. Paddle, wall, line, and power-up contacts append to the ledger; scoring and reset clear it; bumper attribution is reserved until bumper physics exists.

Consequences: the reducer derives `OneTimerFired` from reliable state and replayed tick data, while `GameRoom` translates that event into ephemeral presentation feedback. Highlight markers are neither stored in core state nor appended to the replay intent log. Reducer logic uses only serialized state, reliable action data, and logical time; wall-clock time, randomness, WebSocket identities, and visual effects cannot influence eligibility or physics.

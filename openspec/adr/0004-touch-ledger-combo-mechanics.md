# Deterministic Give-and-Go and Super Goals

Both mechanics derive from the existing eight-contact ledger and stable paddle sides. Give-and-Go requires paddle -> owned line -> same paddle within three logical seconds, applies 2.5x outgoing raw speed capped at 800, and preserves the ledger. Consecutive duplicate object contacts collapse during evaluation only; neutral contacts and ownerless lines interrupt Give-and-Go. Owned teleport lines count as drawn lines. Repeated valid sequences can trigger without separate cooldown state.

Super Goals award two points for at least four counted trailing contacts within ten logical seconds, including the scorer's paddle and an owned line. Opponent contacts, walls, and ownerless lines terminate the suffix; older interruptions do not invalidate a new chain. Neutral power-ups and bumpers neither interrupt nor count. Consecutive duplicate eligible objects count once. Recent qualifying suffixes remain eligible after older contacts expire. Scoring evaluates the ledger before clearing it for the next serve.

Balance settings belong to immutable state and are serialized in replay headers, full snapshots, and history deltas. Missing combo configuration disables both mechanics for historical playback. No activation flags or highlight intents are added. Pure evaluations determine velocity and score; the existing domain-event capture path delivers ephemeral messages and re-derived replay highlights. The frontend displays brief messages only. No native build is required for verification.

New rooms capture `remimho.combos` deployment settings once: `enabled`, `give-and-go-multiplier`, `maximum-raw-speed`, `give-and-go-window-ns`, `super-goal-threshold`, and `super-goal-window-ns`. Historical snapshots always retain their own settings.

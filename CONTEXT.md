# Domain Context

## Glossary

### Capture Dossier

A player-requested evidence bundle of the preceding 15 seconds of a match and a screenshot of the player's view, captured without interrupting gameplay to investigate suspected paddle pass-throughs.

### Passing Combination

A prepared arrangement of short drawn deflectors owned by the activating player and oriented toward their opponent.

### Give-and-Go

A wall pass from a player's paddle to their owned line and back to their paddle within three logical seconds. The return receives a 2.5x speed boost capped at 800 raw speed. Consecutive duplicate contacts count once; neutral contacts, walls, opponent contacts, and ownerless lines interrupt the pass. Owned teleport lines qualify. A rally may contain repeated successful passes, which can also contribute to a Super Goal.

### Super Goal

A two-point goal following at least four counted contacts in the scorer's uninterrupted trailing chain within ten logical seconds. The chain includes the scorer's paddle and at least one owned line. Walls, opponent contacts, and ownerless lines break the chain. Neutral power-ups and bumpers neither break it nor count; consecutive duplicate eligible objects count once. A recent qualifying suffix may follow an older interruption or expired contact. The next serve begins with an empty touch ledger.

### Touch Ledger

A bounded, immutable history of recent puck contacts used by deterministic combo mechanics. Each entry records the contact source, attribution, logical `elapsedNs`, and the puck's incoming speed immediately before collision resolution.

`speedAtContact` is the effective on-board speed, including deterministic match progression, turbo, and active power-up multipliers available to the replayed tick. The ledger is part of immutable game state so state-history restoration also restores the exact touch sequence.

The ledger retains at most eight touches and is cleared on scoring and match reset so combo context cannot cross rally boundaries. Time travel restores the ledger from the selected historical state without separate cleanup.

### One-Timer

A strengthened direct return triggered when a paddle contacts a sufficiently fast puck whose immediately preceding touch was the opponent's paddle. Any intervening wall, drawn-line, bumper, or power-up touch makes the return ineligible, but the touch remains in the ledger.

The initial balance requires at least `500 px/s` effective incoming speed within `2 seconds` of logical time. Shot strength scales from `1.25x` to `1.50x`, with outgoing raw velocity capped at `800 px/s`. There is no separate cooldown because each trigger requires a new direct opponent paddle touch.

One-Timer changes outgoing puck velocity only. Scoring remains unchanged; the stronger shot is the complete gameplay reward.

`OneTimerFired` is a deterministic reducer domain event, not core state and not a replay intent. Live infrastructure translates it into ephemeral trail, shake, sound, and UI feedback; replay playback re-derives it while reducing recorded actions. Replay snapshots serialize the touch ledger, line ownership, and One-Timer balance configuration so future defaults do not change historical matches.

The initial ledger records paddles (`paddle:A`, `paddle:B`), top and bottom walls, drawn-line IDs, and collected power-up IDs. `BUMPER` is a reserved source until bumper physics exists. Power-up collection is an intervening touch and invalidates a direct One-Timer chain.

### Touch Ownership

Touch attribution uses the match-stable `PaddleSide` (`A` or `B`) when a contact belongs to a player. The contacted object's stable identifier is stored separately. Neutral contacts have no owner; transient connection or player IDs are not part of deterministic touch state.

`MviLine.ownerSide` is optional for backward compatibility. New player-drawn lines receive the creator's stable side; legacy replay lines and system-generated lines deserialize without an owner while retaining their stable line ID.

### Rabbit Mode

A six-second power-up that gives the puck alternating small turns without changing its speed
or disabling collisions. A repeat pickup refreshes the effect rather than increasing its strength.
Rabbit mode replaces ghost mode in live matches; ghost pass-through exists only in historical replay playback.

### Edge Swipe

A paddle-edge strike that adds a bounded sideways impulse to the puck's incoming motion.
An upward swipe biases the puck left and a downward swipe biases it right on either side
of the board. Existing momentum can outweigh the impulse; a swipe does not guarantee a reversal.

Edge spin is additive: a moving edge contributes spin in its movement direction, but incoming
opposite spin can outweigh or cancel that contribution. Spin denotes a curving trajectory,
not a positional force field around the puck.

# Combo Mechanics Specification

## Purpose

Combo mechanics reward prepared play: One-Timer, Give-and-Go and Tic-Tac-Toe (the two-point Super Goal chain) are deterministic evaluations over the touch ledger and stable paddle sides, configured per room and serialized for replay. See `openspec/adr/0003-deterministic-touch-ledger-and-one-timer.md` and `0004-touch-ledger-combo-mechanics.md`.

## Requirements

### Requirement: One-Timer boosts a fresh direct opponent-paddle return

A One-Timer SHALL fire when the puck's immediately preceding touch is the opponent's paddle with no intervening contact, the effective incoming speed is at least 500 px/s, and the touch is no older than two logical seconds. The outgoing raw velocity SHALL be multiplied by 2.0 and capped at 800 px/s. A new direct opponent touch is required for each trigger, so no separate cooldown is stored.

#### Scenario: Direct fast return

- **WHEN** a paddle returns a puck whose previous touch was the opponent's paddle at 600 px/s within two seconds
- **THEN** the outgoing raw speed is doubled but never exceeds 800 px/s

#### Scenario: Intervening contact invalidates eligibility

- **WHEN** a wall, drawn line, bumper or power-up is contacted between the opponent's paddle touch and the return
- **THEN** no One-Timer fires, although the intervening touch remains in the ledger

#### Scenario: One-Timer never brakes the puck

- **WHEN** the puck is already at or above the 800 px/s cap
- **THEN** the One-Timer returns it unchanged rather than slowing it

### Requirement: Give-and-Go rewards paddle-line-paddle play

Give-and-Go SHALL fire when a player's paddle, an owned line of that same side and the same paddle contact the puck within three logical seconds, with consecutive duplicate contacts counting once. Neutral contacts, walls, opponent contacts and ownerless lines SHALL interrupt the pass; owned teleport lines SHALL qualify. The return SHALL multiply raw speed by 2.5, cap it at 800 px/s and require no cooldown state.

#### Scenario: Valid wall pass

- **WHEN** a paddle, one of its owned lines and that same paddle contact the puck within three logical seconds
- **THEN** the final return is boosted by 2.5x and capped at 800 px/s

#### Scenario: Wall contact interrupts the pass

- **WHEN** the puck touches a wall between the paddle and the owned line
- **THEN** Give-and-Go does not fire

#### Scenario: Repeated contacts with one object count once

- **WHEN** the puck contacts the same owned line twice in a row
- **THEN** those contacts count as a single element of the passing sequence

#### Scenario: Owned teleport line qualifies

- **WHEN** the qualifying drawn line is an owned teleport line
- **THEN** Give-and-Go still fires

### Requirement: Tic-Tac-Toe awards two points for a sustained chain

A Tic-Tac-Toe chain (reported internally as `SuperGoalScored`) SHALL award two points when the scorer's uninterrupted trailing chain has at least four counted contacts within ten logical seconds and includes the scorer's paddle and one owned line. Walls, opponent contacts and ownerless lines SHALL break it; neutral power-ups and bumpers SHALL neither break nor count; duplicate eligible objects SHALL count once. A recent qualifying suffix SHALL remain eligible after an older interruption.

#### Scenario: Four-contact chain scores two points

- **WHEN** the scorer's trailing chain contains at least four counted contacts within ten seconds, including the scorer's paddle and an owned line
- **THEN** the goal is worth two points

#### Scenario: Opponent touch ends the chain

- **WHEN** the opponent's paddle touches the puck inside the window
- **THEN** only the contacts after that touch can form a Super Goal chain

#### Scenario: Recent suffix qualifies after an older interruption

- **WHEN** an older contact has expired but the most recent contacts still form a qualifying suffix
- **THEN** the Super Goal is awarded

#### Scenario: Scoring evaluates before the ledger is cleared

- **WHEN** the scoring contact completes a Super Goal chain
- **THEN** the goal is evaluated against the ledger before that ledger is cleared for the next serve

### Requirement: Combo balance is captured per room and replayed

A new room SHALL capture `remimho.combos` settings once: `enabled`, `give-and-go-multiplier`, `maximum-raw-speed`, `give-and-go-window-ns`, `super-goal-threshold` and `super-goal-window-ns`. The resulting `Combo` SHALL be immutable game state serialized in replay headers, full snapshots and history deltas. Historical snapshots SHALL retain their own settings, and a missing combo configuration SHALL disable Give-and-Go and Tic-Tac-Toe for historical playback.

#### Scenario: Old recordings keep their own multiplier

- **WHEN** a replay recorded with a 1.8x Give-and-Go multiplier is replayed
- **THEN** it uses 1.8x rather than the current deployment default

#### Scenario: Missing configuration disables combos

- **WHEN** a historical snapshot carries no combo configuration
- **THEN** Give-and-Go and Tic-Tac-Toe do not fire during that playback

### Requirement: Combo feedback is derived, never stored

Combo physics SHALL be pure evaluations of reliable state, and combo feedback SHALL be delivered as ephemeral domain events re-derived during replay. No activation flag, highlight marker or presentation intent SHALL be stored in core state or appended to the replay intent log.

#### Scenario: Replay re-derives highlights

- **WHEN** a recorded match is imported headlessly
- **THEN** One-Timer highlights are re-derived by reducing the recorded actions, and the stored final state equals the live final state

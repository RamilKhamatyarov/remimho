# Touch Ledger Specification

## Purpose

Combo eligibility depends on a bounded, immutable history of recent puck contacts with stable attribution and effective incoming speed. See `openspec/adr/0003-deterministic-touch-ledger-and-one-timer.md` and `0004-touch-ledger-combo-mechanics.md`.

## Requirements

### Requirement: The ledger is a bounded immutable contact history

`MviGameState` SHALL carry a `TouchLedger` of at most eight entries. Each entry SHALL record the contact source, the optional owning `PaddleSide`, the contacted object's stable identifier, the logical `elapsedNs`, and the puck's effective `speedAtContact` immediately before collision resolution, including deterministic progression, turbo and active power-up multipliers.

#### Scenario: Ninth contact evicts the oldest

- **WHEN** a ninth touch is appended to a full ledger
- **THEN** the ledger keeps the eight most recent entries and drops the oldest

#### Scenario: Speed at contact uses the effective on-board speed

- **WHEN** a paddle contact occurs while deterministic progression, turbo or a power-up multiplier is active
- **THEN** the stored `speedAtContact` reflects the multiplied effective speed available to the replayed tick

### Requirement: Ledger state survives snapshots, history and rewind

The ledger SHALL be part of immutable game state and SHALL be serialized in replay snapshots and history deltas, so restoring a state also restores the exact touch sequence and combo context.

#### Scenario: Rewind restores the exact ledger

- **WHEN** a match is rewound to an earlier time-travel frame
- **THEN** the restored ledger equals the ledger at that frame, without separate cleanup

### Requirement: Scoring and reset clear the ledger

The ledger SHALL be cleared on scoring and on match reset so combo context cannot cross rally boundaries, and the next serve SHALL begin with an empty touch history.

#### Scenario: Serve after a goal

- **WHEN** a goal is scored and the next serve begins
- **THEN** the ledger is empty for that serve

### Requirement: Attribution uses stable match identity

Contact attribution SHALL use the match-stable `PaddleSide` (`A` or `B`) for player contacts and SHALL store the contacted object's stable identifier separately. Neutral contacts SHALL have no owner, and transient connection or player identifiers SHALL NOT be stored in deterministic touch state.

#### Scenario: Power-up collection is an intervening touch

- **WHEN** the puck collects a power-up
- **THEN** a `POWER_UP` entry with no owner is appended, breaking a direct One-Timer chain while remaining in the ledger

#### Scenario: Legacy lines deserialize without an owner

- **WHEN** a replay line recorded before line ownership existed is decoded
- **THEN** it keeps its stable line ID and deserializes with no owner side

### Requirement: New drawn lines carry their creator's side

New player-drawn lines SHALL receive the creator's stable `PaddleSide` as `ownerSide`. Legacy replay lines and system-generated lines SHALL remain ownerless while retaining their stable identifiers.

#### Scenario: Owned line counts for a passing combination

- **WHEN** a player draws a line and later plays it back through their own paddle
- **THEN** the owned line can satisfy Give-and-Go or Super Goal ownership requirements, while an ownerless line cannot

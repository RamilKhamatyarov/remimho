# Deterministic Simulation Specification

## Purpose

Every gameplay outcome in remimho is reproducible: physics, scoring, combos and bot decisions are derived only from serialized reliable state, recorded actions and logical time. This is the foundational contract that lets the server replay, rewind and re-derive matches. See `openspec/adr/0001-deterministic-redirect-and-spin.md`.

## Requirements

### Requirement: Reduction uses only reliable state, recorded actions and logical time

The reducer SHALL derive every gameplay outcome from immutable game state, reliable recorded actions and logical `elapsedNs`. It SHALL NOT read wall-clock time, randomness, WebSocket identities or connection state when deciding velocity, collisions, scoring, combos or bot intent.

#### Scenario: Replaying the same actions reproduces the same state

- **WHEN** the same initial snapshot and index-ordered action log are reduced twice
- **THEN** both runs produce identical final game state, including puck velocity, score, ledger entries and active power-ups

#### Scenario: Wall-clock time does not influence physics

- **WHEN** a match is reduced with real time advancing differently between two runs, but the recorded tick durations and actions are identical
- **THEN** the two runs produce identical puck positions and scoring outcomes

### Requirement: Presentation effects are derived and never authoritative

Visual feedback such as trails, tints, glows, swirl indicators, shake and highlight messages SHALL be ephemeral derivatives of reliable state. They SHALL NOT be stored in core game state or appended to the replay intent log, and their absence or presence SHALL NOT change physics or scoring.

#### Scenario: Disabling presentation feedback leaves gameplay unchanged

- **WHEN** a match is reduced with all visual feedback suppressed
- **THEN** the resulting game state is byte-identical to a run with feedback enabled

### Requirement: Time travel restores the complete physics context

State-history frames, replay snapshots and history deltas SHALL serialize every field that can affect future physics, including the touch ledger, line ownership, combo and speed rules, serve side and power-up timers.

#### Scenario: Rewind restores configuration, not defaults

- **WHEN** a match with non-default speed rules is rewound through REST `/time-travel` or the WebSocket timeshift commit
- **THEN** the restored state uses the speed rules active at that moment rather than the runtime defaults

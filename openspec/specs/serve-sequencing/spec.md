# Serve Sequencing Specification

## Purpose

Serves alternate deterministically after every goal and restart, and the remembered serve side is part of immutable state so restore returns the correct next serve. See `openspec/adr/0009-state-decoding-and-alternating-serves.md`.

## Requirements

### Requirement: Serves alternate directions

The serve direction SHALL alternate between right/down and left/up after each goal and after each explicit restart.

#### Scenario: Consecutive goals alternate

- **WHEN** one side scores and the next serve begins, then the other side scores
- **THEN** the two serves leave in opposite directions

#### Scenario: Restart also alternates

- **WHEN** the match is explicitly restarted
- **THEN** the next serve uses the opposite direction from the previous serve

### Requirement: Last serve side is immutable state

The last serve side SHALL be stored in immutable game state, independent of the puck's current velocity and of the number of points awarded, and SHALL be serialized in replay and history snapshots so restoring a rally also restores the next serve. No wall-clock input, random choice or presentation event SHALL determine the direction.

#### Scenario: Restored rally serves correctly

- **WHEN** a snapshot or history frame captured before a serve is restored
- **THEN** the next serve uses the same direction the live match would have used

### Requirement: A new room starts right/down

A newly created room SHALL begin with a right/down serve.

#### Scenario: First serve of a new room

- **WHEN** a fresh room starts its first rally
- **THEN** the puck serves to the right and downward

### Requirement: Older snapshots decode with a default side

Additive snapshot fields for the last serve side SHALL default to side B for files written before the field existed. This preserves readability, not historical physics equivalence.

#### Scenario: Replay recorded before serve tracking

- **WHEN** a snapshot without the last-serve field is decoded
- **THEN** it deserializes successfully with the default serve side

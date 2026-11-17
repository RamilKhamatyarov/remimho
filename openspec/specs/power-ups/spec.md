# Power-Ups Specification

## Purpose

Power-ups act on the whole match through immutable effect state. Rabbit mode replaces ghost mode in live play, while version-1 ghost playback remains available for historical recordings. See `openspec/adr/0015-ghost-mode-paddle-pass-through.md` and `0016-rabbit-mode-replaces-live-ghost-mode.md`.

## Requirements

### Requirement: Ghost mode is a legacy-only pass-through

The `GHOST_MODE` power-up SHALL remain decodable but SHALL NOT be available as a live mechanic. Under version-1 playback it SHALL last six logical seconds, disable both paddles including faces, edges and shield, and record no paddle touch or same-tick goal veto, while walls and drawn lines still collide. A second pickup SHALL add an independent duration.

#### Scenario: Live spawn rejects ghost mode

- **WHEN** a client requests a ghost-mode spawn in a live room
- **THEN** the request is rejected and no ghost pickup is created

#### Scenario: Historical ghost pass-through is preserved

- **WHEN** a version-1 recording containing ghost mode is imported headlessly
- **THEN** the puck still passes through both paddles as it did in the original match

#### Scenario: Overlapping ghost pickups extend the effect

- **WHEN** a second ghost pickup occurs while ghost mode is active
- **THEN** ghost mode lasts until the later independent duration expires

### Requirement: Rabbit mode curves the puck deterministically

`RABBIT_MODE` SHALL last six logical seconds and rotate the puck's velocity by +15 degrees, then -15 degrees, alternating every 300 ms, with the first turn 300 ms after collection. Rotation SHALL preserve speed while walls, lines, paddles, shields, spin and magnet keep their behaviour. Due turns SHALL be applied after movement and collisions, driven by recorded tick boundaries rather than wall-clock time.

#### Scenario: First turn timing

- **WHEN** rabbit mode is collected at `elapsedNs`
- **THEN** no turn is applied before 300 ms of logical time has passed, and the first due turn rotates by +15 degrees

#### Scenario: Speed is preserved through turns

- **WHEN** a rabbit turn is applied
- **THEN** the puck's speed magnitude is unchanged and only its direction rotates

#### Scenario: Turn schedule survives a multi-interval tick

- **WHEN** one recorded tick spans several 300 ms intervals
- **THEN** every due turn is applied in order and the recorded turn count reflects them

### Requirement: Repeated rabbit pickups refresh, never stack

A repeated rabbit pickup SHALL refresh the effect's duration and restart the turn schedule instead of adding strength or a second independent effect. Pausing SHALL freeze processing, reset SHALL clear the effect, and goals SHALL retain active effects as before.

#### Scenario: Second pickup refreshes

- **WHEN** the puck collects rabbit mode while it is already active
- **THEN** the effect expires six seconds after the later pickup and the turn schedule restarts

#### Scenario: Pause freezes the turn schedule

- **WHEN** the match is paused during rabbit mode
- **THEN** no rabbit turns advance until the match resumes

### Requirement: Rabbit phase is replayable state

Activation time, duration and applied turn count SHALL be persisted in both replay snapshots and history deltas. No transient flag or presentation event SHALL drive rabbit physics.

#### Scenario: Snapshot continuation keeps the phase

- **WHEN** a match is continued from a history or full snapshot during rabbit mode
- **THEN** subsequent turns follow the stored activation time and applied count

### Requirement: A shield contact is a save

For replay version 2 and later, a shield reflection SHALL count as a save for that tick. A puck left exactly on the goal boundary by a shield SHALL not score.

#### Scenario: Shield reflection on the goal line

- **WHEN** a shield reflects the puck and leaves it on the goal boundary
- **THEN** no goal is awarded for that tick

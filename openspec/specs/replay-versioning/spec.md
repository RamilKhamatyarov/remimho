# Replay Versioning Specification

## Purpose

Physics changes must not silently rewrite old recordings. A replay version selector chooses the rules used for headless playback, while live play always uses the current rules. See `openspec/adr/0016-rabbit-mode-replaces-live-ghost-mode.md` and `0017-edge-hit-deflection-and-football-spin.md`.

## Requirements

### Requirement: The replay version selects the physics rules

Replay playback SHALL select a fixed, explicit physics-rules set from the replay version, passed through reduction rather than stored in game state or a thread-local. Version 1 (or absent) SHALL enable legacy ghost behaviour, version 2 SHALL enable rabbit and shield rules, and version 3 SHALL change edge and spin physics. New exports and capture segments SHALL be marked version 3. Unknown replay versions SHALL be rejected.

#### Scenario: New export is version 3

- **WHEN** a new match is exported for replay
- **THEN** the replay file is marked version 3

#### Scenario: Unknown version is rejected

- **WHEN** a replay file declares an unsupported version
- **THEN** import fails instead of silently applying current physics

### Requirement: Historical versions reproduce their original physics

Versions 1 and 2 SHALL retain their historical edge response, spin tuning and decay, and version 1 SHALL retain legacy ghost pass-through. Version 3 SHALL change edge and spin physics only, preserving the version-2 rabbit and shield rules exactly. Historical version-1 and version-2 fixture snapshot hashes SHALL remain unchanged.

#### Scenario: Version-1 ghost playback is isolated

- **WHEN** a version-1 recording is reduced headlessly
- **THEN** ghost pass-through occurs exactly as recorded, while version-2 and version-3 playback keeps paddles active

#### Scenario: Version-2 rabbit timing is unchanged

- **WHEN** a version-2 recording with rabbit mode is replayed
- **THEN** its rabbit turn timing and constants match the version-2 behaviour

### Requirement: Old recordings are never relabelled

The demo generator and export paths SHALL emit version 3 only for newly generated recordings and SHALL NOT silently relabel existing replay files.

#### Scenario: Existing replay file keeps its version

- **WHEN** an existing replay file is read and written back through a new export
- **THEN** its original version label is preserved

### Requirement: Live resume uses current rules

Resuming an old imported match as live play SHALL use current rules. Restoring an old snapshot into live play SHALL remove legacy ghost pickups and effects, including their collision bypass, while historical headless results remain unchanged.

#### Scenario: Restored ghost snapshot becomes a live match

- **WHEN** a snapshot containing an active ghost-mode effect is restored into a live room
- **THEN** the ghost effect and its collision bypass are removed and current rules apply

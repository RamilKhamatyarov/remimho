# Passing Combinations Specification

## Purpose

Players arrange short deflectors into prepared passing combinations. Layouts are owned by the activating side, applied atomically, and recorded as resolved geometry so playback never consults the preset catalog or browser storage. See `openspec/adr/0010-passing-combination-presets.md`.

## Requirements

### Requirement: Five preset arrangements are available

The game SHALL offer five arrangements of short deflectors for player-aimed live passing combinations. Every line SHALL belong to the activating paddle side and SHALL mirror toward the opponent.

#### Scenario: Activating a preset

- **WHEN** a player applies a preset
- **THEN** its lines are owned by that player's side and mirrored toward the opponent

### Requirement: Applying or disabling a layout is atomic

Applying or disabling a layout SHALL atomically pause the match and replace only that side's previous preset lines. Freehand lines and opponent lines SHALL remain untouched, and the player SHALL resume explicitly.

#### Scenario: Replacing a previous preset

- **WHEN** a player applies a second preset
- **THEN** the match pauses and only that player's earlier preset lines are replaced

#### Scenario: Freehand and opponent lines survive

- **WHEN** a preset is applied while freehand and opponent lines exist
- **THEN** those lines remain in the match

### Requirement: Layouts are recorded as resolved geometry

The reliable action and the replay and history snapshots SHALL record resolved geometry, stable line IDs, ownership and the combination ID. Playback SHALL NOT consult the preset catalog or browser storage, and layout changes SHALL be unavailable during history preview.

#### Scenario: Replay renders the recorded layout

- **WHEN** a match containing a preset layout is replayed
- **THEN** the lines are reconstructed from recorded geometry and stable IDs, without reading any catalog

### Requirement: Custom combinations are captured and versioned

A custom combination SHALL capture the player's current lines, including preset lines, in a canonical attacking direction. Custom combinations SHALL be stored locally with versioned JSON import and export, and finite, bounded coordinates and sizes SHALL be validated at both boundaries. The server SHALL assign ownership and line IDs. Saved arrangements SHALL NOT include teleport links or opponent lines.

#### Scenario: Importing a malformed custom combination

- **WHEN** imported JSON contains non-finite or out-of-bounds coordinates
- **THEN** the import is rejected at the boundary and no lines are created

#### Scenario: Server assigns ownership on apply

- **WHEN** a valid custom combination is applied
- **THEN** the server assigns the acting player's ownership and new stable line IDs to the created lines

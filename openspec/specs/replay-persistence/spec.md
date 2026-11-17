# Replay Persistence Specification

## Purpose

Replay and history data must stay binary-compatible across releases: field numbers and wire types are stable, additive fields are marked optional, and domain naming maps explicitly onto the protobuf schema. See `openspec/adr/0005-combo-naming-and-protobuf-mapping.md`, `0002-room-owned-replayable-bot-controller.md` and `0013-rally-speed-and-smash.md`.

## Requirements

### Requirement: Domain combo naming maps one-to-one onto the schema

The domain value SHALL be named `Combo`, the state property `combo`, and its protobuf schema `combo.proto` SHALL define a `Combo` message. `ComboMapper.kt` and `GameStateMapper.kt` SHALL expose the existing extension functions from `ru.rkhamatyarov.mapping.proto`, keeping protobuf conversion outside the service package, and an import alias SHALL distinguish the protobuf `Combo` from the domain value.

#### Scenario: Mapping code stays in the mapping package

- **WHEN** combo state is converted to or from protobuf
- **THEN** the conversion functions live in `ru.rkhamatyarov.mapping.proto` and the service package does not depend on generated protobuf types

### Requirement: Field numbers and wire types remain stable

Existing protobuf field numbers and wire types SHALL remain unchanged so binary replay and history data stay readable. Renaming a protobuf field SHALL be limited to generated accessor and JSON-name changes. Deployment settings SHALL keep the `remimho.combos.*` prefix and the `ComboSettings` class because they describe server settings captured at room creation.

#### Scenario: Old replay file still decodes

- **WHEN** a replay file written before a field rename is decoded
- **THEN** its values are read into the renamed domain fields without a migration step

### Requirement: Additive snapshot fields are proto3 optional

New snapshot, replay and speed-rule fields SHALL be declared proto3 `optional` so the decoder can distinguish an absent field, written before the change and given the default, from an explicitly written value. A real setting such as `smashBoost = 0` SHALL NOT be read as unset.

#### Scenario: Explicit zero survives a round trip

- **WHEN** a snapshot with `smashBoost = 0` is serialized and decoded
- **THEN** the restored config keeps zero rather than substituting the default

#### Scenario: Absent field takes the default

- **WHEN** a snapshot written before a field existed is decoded
- **THEN** that field takes the documented default

### Requirement: Reserved field numbers protect backward import

Replay protobuf field numbers that existed before a model change SHALL remain reserved, and new configuration fields SHALL use new field numbers.

#### Scenario: Importing a replay with legacy fields

- **WHEN** a replay containing legacy bot configuration fields is imported
- **THEN** the importer maps them without reusing or reassigning their field numbers

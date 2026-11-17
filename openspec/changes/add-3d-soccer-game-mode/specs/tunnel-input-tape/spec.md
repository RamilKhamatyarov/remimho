# Spec Delta

## Purpose

Records everything that can influence a 3D tunnel match as an append-only, timestamp-free sequence, so a match can be replayed exactly and two runs can be compared step by step.

## ADDED Requirements

### Requirement: The tape is the complete input of a simulation

A tape SHALL contain a header fixing the format version, physics version, numerical substrate, step duration and initial configuration, followed by records. Replaying a tape from its header SHALL require no other input.

#### Scenario: Replay needs only the tape

- **WHEN** a recorded tape is replayed in a fresh process with no other data
- **THEN** the replay produces the same per-step state hashes as the recording run

#### Scenario: A dropped input changes the outcome

- **WHEN** a record that altered the simulation is removed from a tape, the remaining records are renumbered, and the tape is replayed
- **THEN** the hash sequence differs from the original from that record's step onward

### Requirement: Records carry kind, sequence and step and no timestamps

Each record SHALL carry a `kind`, a `seq` that increases by exactly one from the previous record, and the `step` at which it takes effect. Records SHALL NOT contain wall-clock time, durations, connection identifiers or session identifiers.

#### Scenario: Two recordings of the same inputs are byte-identical

- **WHEN** the same inputs are applied at the same steps in two recordings made at different real times and speeds
- **THEN** the two tapes are byte-identical

#### Scenario: Several inputs in one step keep their order

- **WHEN** more than one record names the same step
- **THEN** replay applies them in ascending `seq` order

### Requirement: The tape is append-only

Recording SHALL only append records. A record once written SHALL NOT be rewritten, reordered or removed, and the `step` of a record SHALL NOT be lower than the `step` of the record before it.

#### Scenario: A truncated tape replays its prefix

- **WHEN** a tape is cut after any complete record and replayed
- **THEN** the hashes for the steps it still covers equal those of the full tape

#### Scenario: Out-of-order step is rejected

- **WHEN** a tape contains a record whose step is lower than the preceding record's step
- **THEN** reading the tape fails with an error naming the offending `seq`

### Requirement: Malformed and unsupported tapes are rejected

Reading SHALL fail, without simulating, when a tape has a gap or repeat in `seq`, an unknown `kind`, an unsupported format or physics version, or a substrate that the reader does not implement.

#### Scenario: Sequence gap is rejected

- **WHEN** a tape skips a `seq` value
- **THEN** reading fails with an error naming the missing `seq`

#### Scenario: Unknown physics version is rejected

- **WHEN** a tape header declares a physics version the reader does not support
- **THEN** reading fails instead of replaying with current physics

### Requirement: The 2D replay format is unaffected

The tunnel tape SHALL be a separate format. Existing 2D replay files of versions 1, 2 and 3 SHALL continue to import with unchanged results.

#### Scenario: Existing replay fixtures still import

- **WHEN** the existing 2D replay fixtures are imported after the tape format is added
- **THEN** their results are identical to before

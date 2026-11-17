# Spec Delta

## Purpose

Gives every simulation state a compact, portable fingerprint so that two runs can be compared tick by tick, on one machine or across CPU architectures, without comparing whole states.

## ADDED Requirements

### Requirement: State hash is a pure function of simulation state

The system SHALL compute a 64-bit hash from every state field that can affect future simulation, and from nothing else. Object identity, memory addresses, iteration order of unordered containers, wall-clock time, process identity and locale SHALL NOT influence the hash.

#### Scenario: Equal states hash equally in separate processes

- **WHEN** the same state is hashed in two separately started processes
- **THEN** both processes report the same 64-bit value

#### Scenario: A physics-relevant field change alters the hash

- **WHEN** two states differ in exactly one field that can affect future simulation, by the smallest representable amount
- **THEN** their hashes differ

#### Scenario: Presentation-only data does not alter the hash

- **WHEN** two states differ only in data that cannot affect future simulation
- **THEN** their hashes are equal

### Requirement: Numeric values are hashed by exact representation

The hash SHALL consume each numeric field by its exact stored representation, so that values which print alike but differ in their last bit hash differently. Positive and negative zero SHALL hash differently. Every not-a-number value SHALL hash as one canonical value.

#### Scenario: One unit in the last place changes the hash

- **WHEN** a floating-point field is replaced by its adjacent representable value
- **THEN** the state hash changes

#### Scenario: Not-a-number payloads do not matter

- **WHEN** two states differ only in the bit payload of a not-a-number field
- **THEN** their hashes are equal

### Requirement: Hashes are identical across architectures

For the same state, the hash SHALL be identical on JVM x86-64 and JVM ARM64. Committed reference hashes produced on one architecture SHALL be verifiable on the other.

#### Scenario: Reference vectors verify on the other architecture

- **WHEN** the committed set of states with known hashes is hashed on ARM64
- **THEN** every hash equals the value committed from x86-64

### Requirement: Hashing is available for both simulation modes

The system SHALL hash the existing 2D game state and the 3D tunnel state through the same hashing contract, and SHALL be able to produce one hash per reduced tick or step when replaying headlessly.

#### Scenario: A 2D replay yields a hash per tick

- **WHEN** a 2D replay file is reduced headlessly with hashing enabled
- **THEN** the output contains exactly one hash for each reduced tick, in order

### Requirement: Live rooms do not compute state hashes

State hashing SHALL run only in headless replay, diagnostic tools and tests. Enabling hashing SHALL NOT change the result of any reduction.

#### Scenario: Hashing does not perturb the simulation

- **WHEN** the same recording is reduced once with per-tick hashing and once without
- **THEN** both runs end in an identical final state

### Requirement: The 2D determinism audit is evidenced by hashes

The system SHALL provide a repeatable audit that reduces a fixed corpus of 2D recordings and records the per-tick hash sequence. The audit report SHALL state, for same-machine repetition and for x86-64 versus ARM64, whether the sequences are identical, and where they are not, the first divergent tick and the differing fields.

#### Scenario: Same-machine repetition is reported

- **WHEN** the audit corpus is reduced twice on one machine
- **THEN** the report states for each recording whether the two hash sequences are identical

#### Scenario: Cross-architecture divergence is located

- **WHEN** a recording produces different hash sequences on x86-64 and ARM64
- **THEN** the report names the first divergent tick and the state fields that differ at that tick

#### Scenario: Audit leaves 2D physics unchanged

- **WHEN** the audit has been added
- **THEN** the historical version 1, 2 and 3 replay fixture results are unchanged

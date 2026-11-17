# Spec Delta

## Purpose

Turns "these two runs disagree" into "they first disagree at this step, in these fields", which is the primary diagnostic for all determinism work.

## ADDED Requirements

### Requirement: The first divergent step is reported

Given two hash traces, the tool SHALL report the lowest step at which their hashes differ. When every shared step matches it SHALL report that the traces agree, and how many steps were compared.

#### Scenario: Divergence in the middle of a trace

- **WHEN** two traces have equal hashes up to step 1,499 and different hashes at step 1,500
- **THEN** the tool reports step 1,500 as the first divergent step

#### Scenario: Identical traces

- **WHEN** two traces have equal hashes at every step
- **THEN** the tool reports agreement and the number of steps compared

#### Scenario: One trace is shorter

- **WHEN** two traces agree on every shared step but one ends earlier
- **THEN** the tool reports agreement on the shared steps and states the length difference

### Requirement: Differing state fields are listed at the divergent step

When the states at the first divergent step are available for both sides, the tool SHALL list every field whose value differs, with both values shown exactly enough to distinguish them, including values that differ only in their last bit.

#### Scenario: A last-bit difference is visible

- **WHEN** the two states at the divergent step differ only in the last bit of one velocity component
- **THEN** the tool names that field and prints two distinguishable values

#### Scenario: Only the differing fields are listed

- **WHEN** the two states at the divergent step differ in two fields
- **THEN** the output lists exactly those two fields

### Requirement: Traces from another machine can be compared

The tool SHALL accept a trace and a state dump produced on a different machine or architecture, so a local run can be compared with a foreign one without re-running the foreign side.

#### Scenario: Local tape against a foreign trace

- **WHEN** a tape is replayed locally and compared with a trace recorded on another architecture
- **THEN** the tool reports the first divergent step, or agreement

#### Scenario: Traces of different tapes are refused

- **WHEN** the two inputs were produced from tapes with different headers
- **THEN** the tool refuses the comparison and states which header values differ

### Requirement: The outcome is machine-readable

The tool SHALL signal agreement and divergence through distinct exit statuses, so it can gate a build.

#### Scenario: Divergence fails a build step

- **WHEN** the tool is run in a build on two traces that diverge
- **THEN** it exits with a failure status

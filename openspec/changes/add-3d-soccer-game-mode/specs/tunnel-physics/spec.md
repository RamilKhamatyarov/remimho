# Spec Delta

## Purpose

Defines the 3D tunnel simulation: a puck travelling the length of a rectangular tunnel between two paddles, curving under spin, reproducible bit for bit from its input tape.

## ADDED Requirements

### Requirement: The same tape produces the same hash sequence

For one input tape, the tunnel simulation SHALL produce an identical sequence of per-step state hashes on two independent runs and on JVM x86-64 and JVM ARM64.

#### Scenario: Two runs on one machine

- **WHEN** a tape is replayed twice in separate processes on one machine
- **THEN** the two hash sequences are identical

#### Scenario: Two architectures

- **WHEN** a tape is replayed on x86-64 and on ARM64
- **THEN** the two hash sequences are identical

### Requirement: Time advances in fixed steps

The tunnel simulation SHALL advance by a single fixed step duration declared in the tape header. The outcome SHALL depend on the number of steps taken and the inputs applied at each, never on real elapsed time or frame rate.

#### Scenario: Real-time pacing does not matter

- **WHEN** the same tape is replayed as fast as possible and paced at real time
- **THEN** both replays produce identical hash sequences

### Requirement: Numerical results do not depend on the platform

Every arithmetic operation that affects tunnel state SHALL be performed through the numerical substrate selected for the mode, whose results are defined exactly rather than within a tolerance. State-affecting code SHALL NOT use platform-dependent mathematical functions.

#### Scenario: Substrate operations match reference vectors

- **WHEN** the committed operation test vectors are evaluated on x86-64 and on ARM64
- **THEN** every result equals its committed value exactly

### Requirement: Walls contain the puck

The puck SHALL remain inside the tunnel cross-section. On reaching a side wall, floor or ceiling it SHALL reflect off that surface, and it SHALL NOT pass through a surface however far it travels in one step.

#### Scenario: Zero-spin puck bounces between walls

- **WHEN** a puck with zero spin travels obliquely for many steps without paddle contact
- **THEN** it stays within the cross-section at every step and its spin remains zero

#### Scenario: A fast puck does not pass through a wall

- **WHEN** a puck would cross a wall and travel beyond it within a single step
- **THEN** it is reflected at the wall within that step

### Requirement: Spin curves the path

A spinning puck SHALL be deflected sideways relative to its direction of travel, in the direction given by the spin, by an amount that grows with spin magnitude. A puck with zero spin SHALL travel in a straight line between contacts.

#### Scenario: Constant spin follows a curve

- **WHEN** a puck travels the tunnel with a constant non-zero spin and no contact
- **THEN** its path bends steadily toward the side given by the spin's sign

#### Scenario: Opposite spin mirrors the curve

- **WHEN** the same shot is repeated with the spin sign reversed
- **THEN** the path is the mirror image of the first

### Requirement: Paddle contact uses a swept test

Contact between the puck and a paddle SHALL be found by sweeping the puck's movement over the step against the paddle's movement over the same step, so that neither a fast puck nor a fast paddle causes a missed contact.

#### Scenario: A fast puck still hits the paddle

- **WHEN** a puck's path over one step crosses a paddle that covers the crossing point
- **THEN** the puck is returned by the paddle within that step

#### Scenario: A puck beyond the paddle's reach passes

- **WHEN** a puck's path crosses the paddle plane outside the paddle's extent
- **THEN** no contact is made

### Requirement: Paddle sweep imparts spin

The spin a paddle imparts SHALL be determined by the paddle's own movement across the tunnel at contact: a stationary paddle SHALL impart none, and a moving paddle SHALL impart spin whose direction follows the paddle's movement and whose magnitude is bounded.

#### Scenario: Stationary paddle imparts no spin

- **WHEN** a zero-spin puck is returned by a paddle that did not move during the step
- **THEN** the puck leaves with zero spin

#### Scenario: Known sweep gives known spin

- **WHEN** a puck is returned by a paddle moving with a defined sweep
- **THEN** the puck leaves with the spin recorded for that sweep in the golden trace

#### Scenario: Spin is bounded

- **WHEN** a paddle sweeps arbitrarily fast at contact
- **THEN** the imparted spin does not exceed the maximum spin

### Requirement: Golden traces pin the physics

The system SHALL keep committed golden hash traces for at least the zero-spin wall bounce, the constant-spin curve and the known-sweep paddle contact. Verification SHALL fail when the current physics produces any different hash for these tapes.

#### Scenario: Reference implementation passes

- **WHEN** the golden tapes are replayed with unmodified physics
- **THEN** every step hash equals the committed golden hash

#### Scenario: Altering a physics constant fails the goldens

- **WHEN** one physics constant is changed and the golden tapes are replayed
- **THEN** at least one golden trace fails, naming the first divergent step

### Requirement: Golden traces change only deliberately

Golden traces SHALL NOT be rewritten by an ordinary build or test run. Regeneration SHALL be a separate explicit action, and a regenerated trace SHALL record the physics version it was produced with.

#### Scenario: A normal build never rewrites goldens

- **WHEN** the build and test suite run with physics that no longer matches the goldens
- **THEN** the run fails and the committed golden files are unmodified

#### Scenario: Stale physics version is rejected

- **WHEN** a golden trace records a physics version different from the one being verified
- **THEN** verification fails with a message naming both versions

# Puck Edge And Spin Specification

## Purpose

A moving paddle edge imparts a bounded sideways impulse and a football-style curved shot. Edge response and spin tuning are selected by replay version so older recordings keep their original physics. See `openspec/adr/0017-edge-hit-deflection-and-football-spin.md`.

## Requirements

### Requirement: Edge contact adds a bounded horizontal impulse

An edge contact SHALL add a bounded horizontal impulse to existing horizontal momentum: upward paddle movement biases left, downward biases right, identically on both paddles. The impulse SHALL use `EDGE_TANGENTIAL_GAIN` 12.0 px/s per pixel of accumulated travel, clamped to 360.0 px/s, and need not reverse the final direction. Reflected vertical magnitude SHALL be preserved, the effective-speed cap SHALL apply, and no face rally growth or smash SHALL be granted.

#### Scenario: Upward swipe on either paddle

- **WHEN** a paddle moving upward contacts the puck with its edge
- **THEN** a leftward horizontal impulse is added within the 360 px/s cap

#### Scenario: Incoming momentum outweighs the impulse

- **WHEN** the puck's incoming horizontal momentum is larger than the edge impulse
- **THEN** the final horizontal direction is unchanged even though the impulse was added

#### Scenario: Stationary edge adds no impulse

- **WHEN** a stationary paddle's edge contacts the puck
- **THEN** no tangential impulse is added and the vertical reflection is preserved

### Requirement: Edge spin is additive and bounded

Edge contact SHALL add movement-derived spin to incoming spin using `EDGE_SPIN_GAIN` 1.25, clamped to `[-1.5, 1.5]`. The contribution follows paddle movement while the result need not, so opposite spin can weaken, cancel or reverse the curve. A result below 0.02 SHALL clear spin and lifetime; a contribution of at least 0.02 SHALL refresh a surviving result to two seconds; weaker movement SHALL preserve incoming spin and lifetime. Face spin SHALL remain replacement-based.

#### Scenario: Same-sign accumulation saturates at the cap

- **WHEN** consecutive edge contacts add spin in the same direction
- **THEN** the spin magnitude grows but never exceeds 1.5

#### Scenario: Opposing spin cancels

- **WHEN** edge-derived spin opposes the incoming spin and the sum falls below 0.02 in magnitude
- **THEN** spin and its lifetime are cleared

#### Scenario: Stationary edge does not refresh spin

- **WHEN** the paddle moves too little to contribute at least 0.02 spin
- **THEN** the incoming spin keeps its magnitude and remaining lifetime unchanged

### Requirement: Version 3 uses longer-lived, stronger spin

Version-3 physics SHALL use `SPIN_DURATION_NS` of 2 s, `MAX_SPIN` of 1.5, `PADDLE_SPIN_NORMALIZER` of 45 px, `MIN_SPIN` of 0.02, `SPIN_CURVE_ACCELERATION` of 400 px/s squared per spin unit and `SPIN_HALF_LIFE_NS` of 800 ms. Versions 1 and 2 SHALL retain 750 ms duration, spin cap 1.0, normalizer 90 px, minimum 0.05, curve acceleration 260 and per-tick decay of 0.96.

#### Scenario: Version-3 spin lives past the old window

- **WHEN** a version-3 match imparts moderate spin and reduces ticks beyond 750 ms
- **THEN** the curve is still active while its remaining lifetime has not expired

### Requirement: Spin decays by logical time

Version-3 spin SHALL decay using the recorded tick's logical duration in nanoseconds and an exponential half-life, and SHALL never subtract absolute `elapsedNs` from the remaining duration. Curve application SHALL be limited to the still-active part of a tick, and expired or subthreshold spin SHALL be cleared. No wall clock SHALL participate.

#### Scenario: Identical recorded ticks are byte-identical

- **WHEN** the same recorded tick sequence is reduced twice
- **THEN** the resulting puck positions and serialized state are identical

#### Scenario: Spin expires inside a long tick

- **WHEN** a tick is longer than the spin's remaining lifetime
- **THEN** the curve applies only for the active portion and the spin is cleared at expiry

### Requirement: Historical versions keep their edge and spin behaviour

Replay versions 1 and 2 SHALL retain their historical edge response and spin tuning and decay. Version 3 SHALL change edge and spin physics only, leaving face bounce, combos, scoring, rabbit mode and legacy ghost behaviour unchanged.

#### Scenario: Old recording keeps its spin constants

- **WHEN** a version-2 recording is imported
- **THEN** it uses the version-2 spin duration, cap and per-tick decay rather than version-3 values

### Requirement: Capture exposes the contact axis

For capture dossiers, the existing `PaddleHit` event SHALL carry a FACE/EDGE axis defaulting to FACE. This axis SHALL NOT add reliable state, replay intents or change physics.

#### Scenario: Edge contact is attributable in a dossier

- **WHEN** the puck contacts a paddle edge during a captured match
- **THEN** the captured paddle-hit event reports the EDGE axis while the final state is unchanged by capture

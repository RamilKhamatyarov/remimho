# Paddle Collision Specification

## Purpose

Paddle contact, edge response and goal resolution are continuous and non-penetrating, so a puck can never be rendered entering a paddle and continuing into the goal. See `openspec/adr/0007-paddle-contact-and-goal-resolution.md`, `0008-paddle-rectangle-sweep-and-centre-aiming.md`, `0011-wall-aware-paddle-sweeps.md` and `0012-paddle-edges-and-motion-frame-recovery.md`.

## Requirements

### Requirement: Contact sweeps the whole paddle rectangle

Paddle contact SHALL be resolved by sweeping the puck's travel segment against the paddle rectangle expanded by the puck radius, in constant time and without position sampling or sub-stepping. The puck SHALL be placed on the contact boundary in the same tick, so it can never end a tick inside the paddle body.

#### Scenario: Fast puck aimed at the centre of a stationary paddle

- **WHEN** a fast puck's travel segment reaches a stationary paddle face within one tick
- **THEN** the puck is deflected and a paddle touch is recorded in that tick

#### Scenario: Puck curving in above the face crossing point

- **WHEN** a puck clears the face plane above the paddle and descends into the paddle rectangle later in the same tick
- **THEN** it enters through the top edge, is placed on that edge, and has its vertical velocity inverted instead of passing into the goal

#### Scenario: Puck never stays inside the paddle rectangle

- **WHEN** any tick with a paddle contact is reduced
- **THEN** the resulting puck position lies on or outside the paddle rectangle

### Requirement: Face and edge entries have distinct, anchored outcomes

A face entry SHALL keep the existing bounce angle, rally growth, smash and spin rules. A top or bottom entry SHALL reflect the puck's vertical velocity while preserving its magnitude. An entry time that ties between axes SHALL resolve as a face hit so corner contacts behave like the face.

#### Scenario: Top-edge contact records a paddle touch

- **WHEN** the puck enters the paddle through its top or bottom edge
- **THEN** the contact is attributed to that paddle, so the same-tick goal veto applies

### Requirement: Wall contacts split the swept travel

When a tick reaches both a wall and a paddle, the travel SHALL be split at each wall contact and every straight portion swept separately in encounter order, with the arrival height mirrored back into the playfield. The number of wall contacts per tick SHALL be bounded, and excess travel discarded rather than looping.

#### Scenario: Same-tick wall rebound still reaches the paddle

- **WHEN** a fast diagonal puck strikes a wall and reaches the paddle within one tick
- **THEN** contact is evaluated at its true post-rebound height and deflects instead of scoring

#### Scenario: Wall and paddle contacts are recorded in encounter order

- **WHEN** a tick contains a wall contact followed by a paddle contact
- **THEN** the wall entry is appended to the touch ledger before the paddle entry

### Requirement: A same-tick paddle touch vetoes the goal

A goal SHALL NOT be scored for a side whose paddle registered a touch with the same `elapsedNs`, even if a later collision or teleport relocates the puck across the goal line. The veto SHALL be scoped to the defending side so a legitimate goal at the far end still counts.

#### Scenario: Deflected puck relocated across the goal line

- **WHEN** a paddle deflects the puck and a drawn-line collision moves it across that side's goal line within the same tick
- **THEN** no goal is awarded

#### Scenario: Legitimate goal at the far end

- **WHEN** the puck crosses the opponent's goal line without that paddle touching it in the tick
- **THEN** the goal is awarded normally

### Requirement: Paddle travel accumulates within a tick

Multiple `MovePaddle` actions received within one tick SHALL accumulate into a single travel delta that the tick consumes once, so bounce angle and spin reflect how far the paddle actually moved.

#### Scenario: Several moves in one frame

- **WHEN** two `MovePaddle` actions arrive inside one tick
- **THEN** the tick uses their combined travel and then zeroes the accumulated delta

### Requirement: A freed puck leaves the paddle at a usable speed

A puck released from a paddle at below `MIN_CONTACT_SPEED` (1.0 px/s) SHALL be ejected at `MIN_EJECT_SPEED` (120.0 px/s) along its outgoing direction instead of crawling off the face.

#### Scenario: Resting puck released from the dead zone

- **WHEN** a puck with near-zero velocity is deflected by a paddle
- **THEN** its outgoing speed is at least 120 px/s

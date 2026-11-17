# Rally Speed Specification

## Purpose

Rallies accelerate the way professional table tennis does: each face return grows the raw speed, a fast swing adds a smash bonus, and hard ceilings keep the match playable. One `SpeedModel` owns every speed formula, and rewinds restore the rules that were active. See `openspec/adr/0013-rally-speed-and-smash.md`.

## Requirements

### Requirement: Face returns grow raw speed with a bounded smash

Each paddle face return SHALL multiply raw speed by `rallyGrowthPerHit` (default 1.06). Paddle travel since the previous tick SHALL add up to `smashBoost` (default 0.45) in proportion to the swing, reaching full strength at 80 px. Growth SHALL stop at `maxRallySpeed` (default 3200 px/s) and never slow a faster puck. An edge glance SHALL keep its speed, growth SHALL be stateless, and `resetForServe` SHALL restore serve speed.

#### Scenario: Twenty-hit rally accelerates

- **WHEN** the same paddle returns the puck repeatedly with ordinary travel
- **THEN** each face return increases raw speed by the configured growth factor instead of preserving the incoming speed

#### Scenario: Ceiling is never exceeded

- **WHEN** a return would push raw speed past `maxRallySpeed`
- **THEN** raw speed stops at the ceiling and a puck already above it is not slowed

#### Scenario: Edge glance keeps speed

- **WHEN** the puck glances off a paddle's top or bottom edge
- **THEN** it does not receive rally growth or a smash bonus

### Requirement: Effective speed is capped after all multipliers

The final on-screen speed, raw speed multiplied by the progressive, power-up and turbo multipliers, SHALL be clamped to `maxEffectiveSpeed` (default 4800 px/s).

#### Scenario: Stacked boosts are clamped

- **WHEN** progressive, power-up and turbo multipliers would push on-screen speed above the effective ceiling
- **THEN** the tick clamps it to `maxEffectiveSpeed`

### Requirement: SpeedModel is the single source of truth

A `SpeedModel` SHALL own every speed formula: the state multiplier, the capped effective multiplier and the rally return. `TickReducer`, `PaddlePhysics`, `BotController` and the test oracle SHALL all call it rather than duplicating formulas.

#### Scenario: Oracle and reducer agree

- **WHEN** the paddle-interception test oracle predicts capped travel
- **THEN** it uses the same `SpeedModel` formulas as the reducer

### Requirement: Speed rules are configurable within documented bounds

The four rally values SHALL live on `SpeedConfig` and SHALL be tunable through `ApplySpeedConfig`, the workshop REST endpoint and the DSL keys `speed.rallyGrowthPerHit`, `speed.smashBoost`, `speed.maxRallySpeed` and `speed.maxEffectiveSpeed`. The workshop SHALL accept growth 1.0-1.25, smash 0-1, a rally ceiling of 300-6000 and an effective ceiling from the rally ceiling up to 12000. Omitted fields SHALL take defaults so existing clients keep working.

#### Scenario: Out-of-range workshop values are rejected

- **WHEN** a client submits `rallyGrowthPerHit` of 1.5
- **THEN** the workshop returns a validation error instead of applying it

#### Scenario: Omitted fields keep defaults

- **WHEN** a client that sends only the original four fields applies a speed config
- **THEN** the new rally fields take their documented defaults

### Requirement: Rewinds restore the active speed rules

`GameStateDelta` SHALL carry a `SpeedRules` message so both rewind paths, REST `/time-travel` and the WebSocket timeshift commit, restore the speed rules active at that moment instead of runtime defaults.

#### Scenario: Workshop speed settings survive a rewind

- **WHEN** a match with non-default speed rules is rewound
- **THEN** the restored state keeps those rules and does not fall back to `SpeedConfig` defaults

### Requirement: The speed trail is a velocity-derived rendering

The client SHALL draw a fading trail along the reversed velocity whose length and opacity scale from 700 to 3200 px/s raw. The trail SHALL be derived from the current velocity only, with no position history, and SHALL NOT affect physics.

#### Scenario: Serve reset does not smear the trail

- **WHEN** a serve resets the puck velocity or a rewind restores an earlier state
- **THEN** the trail follows the new velocity without leaving a smear from the previous one

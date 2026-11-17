# Rally speed and smash

## Context

Professional table tennis gets faster as a rally goes on: players loop and smash, and the ball
leaves each stroke quicker than it arrived. Here a paddle return preserved the incoming speed, so a
twenty-hit rally stayed near serve speed (about 360 px/s raw) apart from the slow progressive drift.

Speed was also computed in three places with three formulas: `TickReducer` (progressive, power-up
and turbo), `BotController` (raw `vy` against a fixed 900 px/s paddle cap) and the
`PaddleInterception` test oracle (a hand-written copy of the reducer formula). None of them knew
about a ceiling, so any speed-up would have let stacked boosts reach unplayable speeds and let the
bot be outrun rather than out-aimed.

`OneTimerMechanic.apply` scaled a puck to `min(raw * multiplier, maximumRawSpeed)`. Below the cap
that boosts, but for a puck already past the 800 px/s cap it **slowed the puck down**, which any
higher-speed mechanic would trigger.

History deltas (`GameStateDelta`) carried no speed rules, so both rewind paths, REST
`/time-travel` and WebSocket timeshift commit, restored `SpeedConfig()` defaults. Any workshop speed
setting was silently lost on rewind before this change.

## Decision

- **Rally and smash.** Each paddle **face** return multiplies the raw speed by `rallyGrowthPerHit`
  (1.06). The paddle travel since the previous tick adds up to `smashBoost` (0.45) more, in
  proportion to the swing, reaching full strength at 80 px of travel. Growth stops at `maxRallySpeed`
  (3200 px/s raw), and a return never slows a puck already past it. A top or bottom **edge** glance
  is not a stroke and keeps its speed. Growth is multiplicative and stateless: it compounds on its
  own, needs no rally counter (the 8-entry ledger could not count a long rally), and resets with the
  serve because `resetForServe` restores serve speed. A passive rally reaches the ceiling after 38
  returns.
- **Effective cap.** The final on-screen speed, raw speed times progressive, power-up and turbo
  multipliers, is clamped to `maxEffectiveSpeed` (4800 px/s, 1.5x the rally ceiling). Swept
  collision is speed-independent, so the cap is a playability limit, not a correctness one.
- **One source of truth.** `SpeedModel` owns every speed formula: the state multiplier, the capped
  effective multiplier and the rally return. `TickReducer`, `PaddlePhysics`, `BotController` and the
  test oracle all call it.
- **Bot.** Bot paddle speed tracks the puck's **on-screen** vertical speed and is capped at the
  match's `maxEffectiveSpeed` instead of a fixed 900 px/s, so it misses by reaction delay and aim
  error (ADR 0006), not by being outrun.
- **One-timer.** `OneTimerMechanic.apply` returns the puck unchanged when it is already at or above
  its cap, so a one-timer can boost but never brake.
- **Configuration.** The four values live on `SpeedConfig` and are tunable through
  `ApplySpeedConfig`, the workshop REST endpoint and the DSL (`speed.rallyGrowthPerHit`,
  `speed.smashBoost`, `speed.maxRallySpeed`, `speed.maxEffectiveSpeed`). The workshop accepts growth
  1.0-1.25, smash 0-1, rally ceiling 300-6000 and an effective ceiling from the rally ceiling up to
  12000. The upper bounds are named constants on `SpeedConfig`. Omitted fields take the defaults, so
  existing clients that send four fields keep working.
- **Rewinds restore the rules.** `GameStateDelta` gains a `SpeedRules` message (field 21), so a
  rewind restores the speed rules that were active at that moment.
- **Readability.** At the ceiling the puck moves about 53 px per frame. The client draws a fading
  trail along the reversed velocity whose length and opacity scale from 700 to 3200 px/s raw.
  The trail is derived from the current velocity only, with no position history, so it cannot smear
  across a serve reset or a rewind. It is rendering only.

## Consequences

The replay and snapshot schemas gain proto3 `optional` fields (`FullGameSnapshot` 46-49,
`ReplayApplySpeedConfig` 5-8, `SpeedRules` 5-8) so the decoder can tell an absent field, written
before this change and given the defaults, from an explicit value. `smashBoost = 0` is a real
setting and must not be read as "unset". Existing files stay readable. Matches replayed from
recordings made before this change accelerate at each return and can diverge from the recording.
Separate versioned physics would be needed to replay them faithfully, and is not provided.

Every live delta now carries `speed_rules`, about 70 bytes at 60 Hz, following the existing
`one_timer_config` and `combo` precedent. The hand-written browser decoder already skips unknown
fields, so it needed no change. The demo GIF changes, because rally speed alters how `demo.replay`
plays out, and must be regenerated and committed with this change.

Fast rallies are meant to be hard: a paddle that is merely near the puck can now miss it. Tests of
other mechanics (combos, one-timers, contact correctness) use the rally-neutral `NO_RALLY_SPEED`
fixture, so they don't depend on rally tuning. Tunneling stress properties run at the widest legal
envelope (`MAX_LEGAL_SPEED`), and their oracle uses `SpeedModel`, so it predicts capped travel.

The workshop UI exposes no controls for the new fields yet. The fields are reachable through the
REST endpoint and the DSL.

## Verification

`SpeedModelTest` covers the pure rules and `RallySpeedTest` covers them through the reducer: face
return, smash, edge glance, goal reset, the effective cap per tick, one-timer no-brake, and a save at
the effective ceiling. `SpeedRulesMapperTest` and an end-to-end `/time-travel` test in
`GameResourceTest` cover rewinds. With the delta change removed, those two tests and
`ComboReplayTest` fail; with it, they pass. The frontend passed 21 tests, including
`puck_trail.test.mjs`, and `vue-tsc` passed.

The JVM suite passed in full with `./gradlew test`. One later `clean build` run had a single failure,
`GameWebSocketTest > combination is owned by sender recorded and cleared for both clients`: the
browser client did not receive a full-state frame in time. It passed 5 of 5 runs in isolation.
Whether this change makes that timing-sensitive test fail more often was not settled before
verification stopped. Treat it as open until a full build passes. No native compilation was run.

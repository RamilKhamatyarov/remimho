# Edge-hit deflection and football-style spin

Status: Accepted, 2026-11-08. Implementation approved after five design questions.

## Context

The rectangle sweep distinguishes face and edge contacts, but an edge contact currently
reflects vertical velocity and clears spin. A moving edge should instead impart a bounded
sideways impulse and spin, creating a football-inspired curved shot.

Related decisions: [0007](0007-paddle-contact-and-goal-resolution.md),
[0008](0008-paddle-rectangle-sweep-and-centre-aiming.md),
[0011](0011-wall-aware-paddle-sweeps.md), and [0013](0013-rally-speed-and-smash.md).
Replay compatibility must also account for [0016](0016-rabbit-mode-replaces-live-ghost-mode.md).

## Confirmed decisions

- Add a bounded horizontal impulse to existing horizontal momentum. Upward paddle
  movement biases left; downward movement biases right, identically on both paddles.
  The impulse need not reverse the final horizontal direction. NFR-5(a) therefore tests
  the sign of the added impulse, not necessarily the sign of final horizontal velocity.
- Use `EDGE_TANGENTIAL_GAIN = 12.0` px/s per pixel of accumulated paddle travel and
  `MAX_EDGE_TANGENTIAL_IMPULSE = 360.0` px/s. Clamp the added impulse, not incoming
  horizontal momentum. Preserve the reflected vertical magnitude. This can increase
  total raw speed; the existing effective-speed cap remains in force. Edge contacts do
  not receive face-hit rally growth or smash bonuses. These constants intentionally use
  the existing travel-per-tick input, not a newly inferred instantaneous paddle velocity.
- Keep the edge's vertical reflection, paddle touch attribution, and same-tick goal veto.
- Spin radius means a longer-lived, stronger curve, not a positional force field.
- Preserve face bounce-angle rules, combo eligibility, scoring, rabbit mode, and legacy
  ghost behavior. No client rendering changes are included.
- Export new recordings as replay version 3. Versions 1 and 2 retain their historical
  edge response and spin tuning/decay. Version 1 retains legacy ghost behavior; version 2
  retains rabbit and shield rules from ADR 0016. Version 3 changes edge/spin physics only.
  Reuse the existing replay version field; no new schema fields are needed for this cutoff.
  Older recordings must not silently adopt the new physics. This adds a maintenance and
  regression-testing obligation for the historical paths.
- Version 3 uses `SPIN_DURATION_NS = 2_000_000_000L` (previously 750 ms),
  `MAX_SPIN = 1.5` (previously 1.0), `PADDLE_SPIN_NORMALIZER = 45.0` pixels of
  accumulated travel (previously 90), and `MIN_SPIN = 0.02` (previously 0.05).
  Edge-generated spin additionally uses `EDGE_SPIN_GAIN = 1.25`; face-generated spin
  does not use this edge gain. Both are bounded by the same spin cap.
- Set `SPIN_CURVE_ACCELERATION = 400.0` px/s squared per spin unit (previously 260).
  Replace version-3 per-tick decay with logical-time decay using
  `SPIN_HALF_LIFE_NS = 800_000_000L`. Historical versions keep their 0.96 per-tick
  decay. The two-second duration is an upper limit; a sufficiently weak spin can fall
  below the minimum threshold earlier. Wall and shield retention remain 0.75; line
  retention is unchanged. This is stronger, longer-lived spin, not a spatial force field.

## Additive spin

- Add movement-derived edge spin to the incoming spin at contact, then clamp the result
  to [-1.5, 1.5]. The contribution follows paddle movement; the resulting spin need not.
  This explicitly relaxes FR-4 and NFR-5(b)'s final-sign requirement. Opposite spin can
  weaken, cancel or reverse the curve. Cancellation below 0.02 clears spin and lifetime.
- A contribution of at least 0.02 refreshes
  a surviving result to two seconds. Stationary or below-threshold movement preserves
  incoming spin and its remaining lifetime, without refreshing or accumulating tiny spin.
  Horizontal impulse still applies independently. Face spin remains replacement-based.

## Implementation plan

1. Introduce a fixed, explicit physics-rules selector, passed through reduction rather
   than stored in game state or a thread-local. Live play selects version 3. Headless
   import selects legacy rules for absent/1, rabbit-era rules for 2, and new rules for 3.
   Unknown versions remain rejected. Preserve version-1 ghost and shield behavior and
   version-2 rabbit/shield behavior exactly; do not change rabbit timing or constants.
2. In `PaddlePhysics.resolveEdge`, retain impact positioning and vertical reflection.
   Add the bounded movement-derived horizontal impulse and additive spin described above.
   Continue recording the effective incoming speed, defending side and paddle identifier
   in the ledger. Do not call face-only rally, smash, one-timer or give-and-go processing.
3. In `PaddlePhysics.redirect`, select old/new spin constants only. Leave `bounceAngle`,
   `ejectionSpeed`, `rallyReturn`, `applyOneTimer`, combo checks and collision sweeps alone.
   Keep scalar calculations in the existing response copies, without new per-contact
   helper-result objects, substeps, or loops.
4. In `PuckPhysics.applySpinCurve`, retain the historical branch for versions 1-2.
   Version 3 consumes the recorded tick's logical duration, expressed in nanoseconds,
   to decrement `spinRemainingNs` and apply exponential half-life decay. Never subtract
   absolute `elapsedNs` directly from remaining duration. Limit curve application to
   the still-active part of a tick and clear expired/subthreshold spin. No wall clock.
   Identical recorded ticks must be byte-identical; different tick partitions are not
   promised byte-identical trajectories. Decay across equal logical durations is tested
   with floating-point tolerance, not a false exact-equality guarantee.
5. Thread the rules selection through `TickReducer`, boundary/paddle resolution, and
   puck advancement. Keep tick ordering, collision ordering, goals and combo conditions
   unchanged. Newly imparted spin survives the contact tick because advancement already
   preceded that contact. Reuse existing snapshot spin fields; no proto additions.
6. Mark new replay exports and capture segments as version 3. The demo generator emits
   version 3 only for newly generated recordings; do not silently relabel old replay files.
   Rewind remains based on existing spin/lifetime fields. Resuming an old imported match
   as live play uses current rules, as in ADR 0016; historical playback retains old rules.
7. Diagnostics currently log misses, not successful contacts. For capture dossiers,
   add a FACE/EDGE axis to the existing `PaddleHit` domain event with FACE as its default.
   Capture formatting can expose this axis without adding reliable state or replay intents.
   Existing turbo/presentation consumers continue using the same event type and side.
   No logging is added inside reduction and event capture must not affect physics results.

All magnitudes below are named constants, selected by rules version and documented with units:

| Constant | Versions 1-2 | Version 3 | Units |
| --- | --- | --- | --- |
| EDGE_TANGENTIAL_GAIN | No impulse | 12.0 | px/s per pixel of paddle travel |
| MAX_EDGE_TANGENTIAL_IMPULSE | No impulse | 360.0 | px/s, absolute added impulse |
| EDGE_SPIN_GAIN | Clears spin | 1.25 | dimensionless contribution gain |
| PADDLE_SPIN_NORMALIZER | 90.0 | 45.0 | pixels of paddle travel per spin unit |
| SPIN_DURATION_NS | 750000000 | 2000000000 | logical nanoseconds |
| MAX_SPIN | 1.0 | 1.5 | absolute spin units |
| MIN_SPIN | 0.05 | 0.02 | spin units |
| SPIN_CURVE_ACCELERATION | 260.0 | 400.0 | px/s squared per spin unit |
| SPIN_DECAY_PER_TICK | 0.96 | Not used | multiplier per tick |
| SPIN_HALF_LIFE_NS | Not used | 800000000 | logical nanoseconds |
| WALL_SPIN_RETENTION | 0.75 | 0.75 | multiplier per collision |

Edge response remains O(1), with no new actions or authoritative state. FR-10 is interpreted
as constant-driven tuning, not literally constant-only edits: adding an edge impulse and
time-based decay requires logic changes. Version-3 trajectories intentionally differ from
older physics, but versioned playback prevents that divergence for old recorded files.

## Test plan

- Both sides, top/bottom edges, upward/downward travel: pin horizontal impulse sign and
  cap, preserved vertical reflection, boundary placement and current-tick ledger contact.
  Include zero incoming horizontal speed and incoming momentum that outweighs the impulse.
- Pin additive spin from zero, same-sign accumulation, opposing-spin dominance, exact
  cancellation, reversal, saturation and subthreshold behavior. Assert contribution sign
  rather than final sign. Check lifetime refresh and stationary lifetime preservation.
- Pin moderate-motion spin over several ticks and beyond the previous 750 ms window;
  logical half-life, expiry including a partially active final tick, pause and rewind.
  Wall/shield retention stays 0.75 and line retention stays unchanged.
- Compare immediate face-contact velocity, bounce angle, rally/smash and combo outcomes
  against historical fixtures. Only spin magnitude/lifetime and subsequent curved travel
  may differ. Do not assert unchanged full trajectories once spin is active.
- Freeze historical version-1/2 edge/spin fixtures before implementation, including ghost,
  rabbit and shield cases, with expected serialized final snapshots. Version-3 export/import
  must reproduce live final snapshot bytes; repeated reduction, captured/uncaptured events,
  snapshot round trips and history continuation must agree.
- Verify FACE/EDGE capture attribution without changing ledger identifiers, replay intents,
  event-based turbo behavior or reducer output. New game state must not depend on event capture.
- Keep `PaddleContactTest` goal-veto and nonpenetration assertions, `MviGameEngineTest`
  actor/error/velocity-consumption invariants, and tunnelling properties unchanged. Update
  only assertions that explicitly pin old spin constants or clearing on a moving edge,
  retaining their old expectations as legacy-version tests. Stationary, unspun edge speed
  tests in `RallySpeedTest` remain valid; new moving-edge tests allow bounded added energy.
- Run full JVM tests, frontend regression tests, ktlint, Spotless and JVM build. No native
  build or client rendering changes. Tests that merely derive expected values by running
  the same updated physics are not sufficient historical regression oracles.

## Risks and scope

The strong impulse can increase raw speed and can bias a shot toward the player's own goal;
this is intentional under the agreed world-coordinate mapping. Effective-speed limits remain
unchanged. Paddle input remains accumulated travel per tick, so equal recorded inputs reproduce
exactly but different input batching can produce different strikes. Additive spin and refresh
can sustain a curve through repeated contacts, bounded by the spin cap and logical lifetime.

The approved lifetime convention is implemented with explicit versioned physics rules.
Historical edge, face and wall-spin snapshot hashes were captured before changing physics.
Existing ghost, rabbit and shield regression tests additionally cover the compatibility boundary.

## Verification

- `gradlew.bat build ktlintCheck`: passed, including Spotless and 288 JVM tests.
- Frontend regression suite: 38 tests passed; no presentation changes.
- Historical snapshot hashes for absent/1/2 replay versions remain identical.
- Existing demo replay and GIF remain unchanged after generation.
- No native compilation was run.

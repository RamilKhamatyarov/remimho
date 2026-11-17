# Wall-aware paddle sweeps

Regression tests reproduce missed saves on both sides when a tick reaches a
paddle and a wall: interpolating toward a folded endpoint cuts across the real
trajectory. Split travel at wall contacts and sweep each straight portion,
recording contacts in encounter order so a prior wall correctly breaks combos.
Paddle responses retain their existing end-of-contact tick behavior. Bound the
number of wall contacts for pathological imported ticks; discard excess travel.

Goal diagnostics run in the room layer under the
`ru.rkhamatyarov.telemetry.PaddleDiagnostics` DEBUG logging category and include
logical time, tick duration, puck, paddle, ghost mode, speed settings, and recent
touches. They describe misses, including legitimate misses, without side effects
inside the reducer. Render puck and paddles from the same snapshot by removing
the additional puck-only smoothing stage that can display a false overlap.

Collision corrections change results for affected historical ticks. Existing
replay files remain readable; replaying under older physics requires separate
versioned simulation support and is not provided by this change.

## Follow-up reproduction coverage

The reported live failure remains unresolved. Passing the earlier regressions
does not establish that every paddle miss is fixed. Added property coverage:

- 5,000 velocity pairs in [-10,000, 10,000], each simulated for 200 ticks.
- 5,000 aimed-shot cases mirrored to both sides, moving the paddle before
  contact, with tick durations from 1 to 250 ms and same-tick wall rebounds.
- 500 room-actor rallies per opponent mode (human or the real bot), each lasting
  200 ticks, with reliable replay actions reproducing the final live state.

The oracle computes height at face arrival, independently of the collision
sweep, while reusing only free-flight integration for spin. A predicted save
requires a current-tick paddle touch, unchanged score, contact at the face, and
outward horizontal velocity. Genuine misses above or below a paddle are allowed;
old ledger entries cannot satisfy the assertion. Both sides must be exercised.

On 2026-10-07, all 29 paddle tests plus ktlint and Spotless passed without
reproducing the reported failure. No production physics was changed in this
follow-up. These properties do not cover client rendering/network timing,
drawn-line interactions, teleports, or automatic power-up spawning. A replay
or paddle-miss diagnostic from the failing live session is needed to narrow
the remaining case.

Follow-up on 2026-10-12: [0012](0012-paddle-edges-and-motion-frame-recovery.md)
documents reproduced edge-collision and WebSocket delivery defects missed by
these original face-plane and room-only properties.

# Rabbit mode replaces live ghost mode

Accepted: 2026-11-03. Supersedes the live gameplay options in ADR 0015.

## Context

ADR 0015 established that ghost mode intentionally disables both paddles and causes
apparently missed saves. New matches must always retain paddle collisions. Historical
recordings must still reproduce their original ghost pass-throughs.

## Decision

- Replace ghost pickups in the spawn schedule with `RABBIT_MODE`.
- For six logical seconds, rotate current velocity by +15 degrees, then -15 degrees,
  alternating every 300 ms. The first turn is 300 ms after collection. Rotation preserves
  speed; walls, lines, paddles, shields, spin and magnet keep their existing behavior.
- Apply due turns after movement/collisions, alongside other active power-up effects.
  Recorded tick boundaries determine trajectories, not wall-clock time or rendering.
- Repeated pickups refresh duration and restart the turn schedule, never stack strength.
  Pausing freezes processing. Reset clears the effect; goals retain active effects as before.
- Persist activation time, duration and applied turn count in both replay snapshots and
  history deltas. No transient flag or presentation event drives physics.
- New exports use replay version 2. Version 1 (or absent version) enables isolated legacy
  ghost compatibility during headless playback only. The historical enum/wire field remain
  for decoding, not as an available live mechanic. Unknown replay versions are rejected.
- Live spawn requests reject ghost mode. Restoring an old snapshot into live play removes
  ghost pickups/effects and its collision bypass; historical headless results remain unchanged.
- Existing generic power-up rendering displays the rabbit badge; no client physics is added.
- Collision regression tests exposed an existing shield scoring defect: a shield reflection
  left the puck exactly on the goal boundary and still awarded a goal. Version 2 treats
  a shield contact as a save for that tick; legacy playback preserves historical scoring.

## Consequences

Old recordings still reproduce ghost behavior, but continuing them as a live match uses
current rules. Rabbit turn scheduling is bounded and deterministic even if a tick crosses
several intervals. Future changes to these constants require a new replay rules version.

## Verification

Test turn timing, alternating angles, speed preservation, refresh, expiry, pause/reset,
both paddles, snapshot/history continuation, replay parity and legacy ghost isolation.

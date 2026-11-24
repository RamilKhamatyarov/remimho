# Proposal

## Why

The product goal is a 3D button in the existing game that switches a match to a 3D tunnel mode.
That mode curves the puck through spin, which accumulates numerical error every step, so it is
only replayable if the arithmetic underneath is bit-identical across runs and CPU architectures.
The 2D reducer has never been checked at that level: it reaches `sin`, `cos`, `atan2` and `hypot`
through `kotlin.math` at 34 call sites (only spin decay uses `StrictMath`), and its replay tests
compare results on one platform only. This change builds and verifies the deterministic numerical
core first, and writes the 3D physics on top of it only once that core is proven.

## What Changes

Work is ordered; each item depends on the ones before it.

1. **2D determinism audit.** A report, backed by committed per-tick hashes, stating whether the
   existing 2D reducer is bit-identical across two runs on one machine and across JVM x86-64 and
   JVM ARM64, with the first divergent tick and the differing fields if it is not. The 2D physics
   is not changed: any migration is reported as a cost and left to a later change (it would need
   replay version 4).
2. **State hashing.** A 64-bit hash of the complete simulation state, computed from a canonical
   field encoding, identical across processes and architectures. Computed in headless replay,
   tools and tests only; live rooms do not pay for it.
3. **3D input tape.** A new standalone, append-only, timestamp-free format of `kind`, `seq`,
   `step` records, with a reference recorder and replayer. The existing `ReplayFile` format and
   its versions 1-3 are untouched.
4. **Numerical substrate decision.** Fixed-point, software floating-point, or a strict IEEE 754
   subset, chosen from the audit evidence and recorded in a new ADR, including the stated cost of
   moving 2D onto it.
5. **Trace diff tool.** Reports the first step where two hash traces diverge and the state fields
   that differ.
6. **Golden fixtures.** Full-trace golden hash sequences for 3D invariant scenarios, which fail
   when a physics constant changes and can only be regenerated deliberately.
7. **3D tunnel physics**, gated on items 1-6 being verified: fixed-timestep simulation with tunnel
   geometry, sweep-based spin and 3D swept collision, headless only. Its acceptance criterion is
   identical hash sequences for the same tape on two runs and on two architectures.
8. **ARM64 parity job in CI.** A workflow job on a GitHub ARM runner replays the committed tapes
   and compares against the committed hashes. Workflow file only; no repository settings.

Out of scope, left to a follow-up change: the 3D button itself, rendering, the WebSocket and
protobuf state protocol for 3D, room integration, bot AI, power-ups, and any change to 2D physics.

## Capabilities

### New Capabilities

- `simulation-state-hash`: canonical 64-bit hashing of simulation state and the cross-run and
  cross-architecture parity evidence built on it, for both the 2D and the 3D state.
- `tunnel-input-tape`: the append-only, timestamp-free input record format for the 3D mode, with
  recording and replay.
- `simulation-trace-diff`: locating the first divergent step between two hash traces and
  reporting the differing state fields.
- `tunnel-physics`: the 3D tunnel simulation contract: fixed timestep, deterministic numerical
  substrate, tunnel walls, paddle contact, sweep-based spin, and the golden traces that pin it.

### Modified Capabilities

None. `deterministic-simulation` and `replay-versioning` keep their requirements; the 2D reducer
is audited, not changed.

## Impact

- **New code** (main, headless only): state hasher, tape reader/writer, trace recorder and diff
  tool, tunnel numerics and tunnel reducer. Nothing is wired into `GameRoom`, `RoomRegistry`,
  `GameWebSocket` or the frontend.
- **Existing code**: read-only for `service/mvi`. A hash encoder for `MviGameState` is added
  beside it without altering any physics or state class.
- **Protobuf**: one new file for the tape. `replay.proto` and `game_state.proto` are unchanged.
- **Build**: new Gradle tasks for the audit, trace recording, trace diff and golden regeneration.
  None of them are attached to `build`.
- **CI**: one added job in `.github/workflows/ci.yml` on `ubuntu-24.04-arm`.
- **Docs**: audit report under `docs/determinism/`, and [ADR 0018](../../adr/0018-tunnel-numerical-substrate.md) for the substrate decision.
- **Risk to existing behavior**: none intended; versions 1-3 fixture hashes must stay unchanged.

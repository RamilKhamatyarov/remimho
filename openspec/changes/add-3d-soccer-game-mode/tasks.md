# Tasks

Groups are ordered. Task 7.1 gates tunnel physics tasks 7.4-7.11. Tasks 7.2-7.3 are pre-gate state
and input-only reducer scaffolding; they must not add puck physics or be treated as parity evidence.

## 1. State hashing

- [x] 1.1 Create package `ru.rkhamatyarov.determinism` with `FieldSink`, `HashSink` (FNV-1a 64) and `DumpSink` per design D2; verify with a unit test that fixed byte inputs give the published FNV-1a 64 reference values
- [x] 1.2 Implement numeric canonicalization (raw bits, one canonical NaN, distinct signed zeros, sorted maps and sets); verify with tests for adjacent-double, NaN-payload and signed-zero cases from the `simulation-state-hash` spec
- [x] 1.3 Write the `MviGameState` encoder covering every physics-relevant field including touch ledger, lines, power-ups, speed and combo rules; verify with a reflection completeness test that fails when a constructor property is neither encoded nor in the explicit exclusion list
- [x] 1.4 Add a property test that changing any single encoded field changes the hash, and that changing only excluded presentation data does not; verify it passes under `./gradlew test`
- [x] 1.5 Commit hash reference vectors (states with expected hashes) under `src/test/resources/determinism/vectors/` and a test that checks them; verify the test passes locally on x86-64
- [x] 1.6 Add an optional per-tick observer to `HeadlessReplayImporter` that is absent by default; verify existing importer and replay tests pass unchanged and a new test shows the final state is identical with and without the observer

## 2. Trace recording and diff tool

- [x] 2.1 Define the text trace format (header plus `step hash` lines) with reader and writer per design D3; verify with a round-trip test
- [x] 2.2 Implement trace comparison returning the first divergent step, agreement with step count, or length difference; verify with tests for the three trace-diff scenarios (middle divergence, identical, shorter trace)
- [x] 2.3 Implement state dump export at a given step and field-level diff over two dumps showing exact hex values; verify with a test where two states differ by one unit in the last place of one field and only that field is listed
- [x] 2.4 Refuse comparison of traces with different headers and report which header values differ; verify with a test
- [x] 2.5 Add Gradle tasks `traceRecord`, `traceDump` and `traceDiff` (not attached to `build`) with distinct exit codes for agreement and divergence; verify by running `traceDiff` on an identical pair (exit 0) and a deliberately altered pair (non-zero)
- [x] 2.6 Document tool usage in `docs/determinism/README.md`; verify each documented command runs as written

## 3. 2D determinism audit

- [x] 3.1 Add deterministic generators for the audit corpus (bounce angles, line collisions, rabbit turns, long spin decay, edge hits, extreme speeds) as committed fixtures; verify each generator produces a byte-identical recording on two runs
- [x] 3.2 Add Gradle task `determinismAudit` that reduces the corpus plus `demo.replay` and writes per-tick traces to `build/determinism/`; verify it produces one trace per recording with one hash per tick
- [x] 3.3 Run the audit twice in fresh processes and once with `-Xint`, comparing with `traceDiff`; verify results for all recordings are captured in the report draft
- [x] 3.4 Commit x86-64 reference traces under `src/test/resources/determinism/2d/`; verify a test replays the corpus and matches them on x86-64
- [ ] 3.5 Add Gradle task `determinismParity` and a `ubuntu-24.04-arm` job in `.github/workflows/ci.yml` running it (hash vectors blocking, 2D trace comparison report-only, per design D11); verify the job runs on a pull request and uploads its traces as an artifact
- [x] 3.6 Inventory every state-affecting floating-point operation in `service/mvi` by file and class (basic IEEE op, `StrictMath`, `Math`); verify the inventory accounts for all `sin`, `cos`, `atan2`, `hypot` and `pow` call sites found by search
- [ ] 3.7 Write `docs/determinism/2d-audit.md` with the inventory, the compared CPUs and JVM builds, and per-recording results for same-machine, `-Xint` and x86-64 versus ARM64, including hashes and first divergent tick and fields where they differ; verify every claim links to a committed trace or CI run
- [x] 3.8 Confirm 2D physics is untouched: verify `git diff` shows no change to physics or state classes under `service/mvi` and the version 1-3 replay fixture tests pass

## 4. Numerical substrate decision

- [x] 4.1 Record ADR 0018's provisional fixed-point choice with range/precision analysis and explicit pending ARM64 criteria; verify no local evidence is described as cross-architecture proof
- [x] 4.2 State in ADR 0018 the 2D migration cost (inventory units, replay version 4, retained arithmetic for versions 1-3); verify the listed math call count matches the reviewed inventory
- [x] 4.3 Implement the Q32.32 scalar and `TunnelMath` facade in `ru.rkhamatyarov.tunnel`; verify exact committed operation vectors locally
- [x] 4.4 Add the source-scan test forbidding `kotlin.math`, `java.lang.Math`, `Random` and clock access under `ru.rkhamatyarov.tunnel`; verify an injected forbidden source is rejected
- [ ] 4.5 Add the substrate vectors to `determinismParity` as a blocking check; verify the ARM64 job passes

## 5. Input tape

- [x] 5.1 Add `src/main/proto/tunnel_tape.proto` with `TapeHeader` and `TapeRecord` (`kind`, `seq`, `step`, payload) per design D6; verify the build generates the classes and `replay.proto` is unchanged
- [x] 5.2 Implement the append-only tape writer (header, length-delimited records, `MARK` on interval and close); verify with a test that two recordings of the same inputs made at different real speeds are byte-identical
- [x] 5.3 Implement the tape reader with validation (sequence gap or repeat, decreasing step, unknown kind, unsupported format, physics version or substrate); verify with one rejection test per case, each naming the offending `seq` or value
- [x] 5.4 Implement the replay driver that feeds records to a step function in `seq` order and emits a trace; verify with a stand-in reducer that a truncated tape reproduces the hash prefix of the full tape
- [x] 5.5 Document the tape format (header fields, kinds, ordering rules) in `docs/determinism/tunnel-tape.md`; verify the documented field list matches the proto file

## 6. Golden fixture harness

- [x] 6.1 Implement the golden comparison harness and first-divergence report; verify with a stand-in reducer and a deliberately corrupted trace
- [x] 6.2 Record the physics version in the trace header and reject a mismatch; verify with a test naming both versions in the failure
- [ ] 6.3 Add Gradle task `updateTunnelGoldens` as the only writer of golden files; verify a failing golden test run leaves the committed files unmodified
- [x] 6.4 Implement the mutation check that replays the tape with one altered stand-in rules value and expects failure; verify an altered value is detected and an unaltered run passes
- [x] 6.5 Define the three golden scenarios (zero-spin wall bounce, constant-spin curve, known-sweep paddle contact) as tape-generating scripts with their expected qualitative invariants; verify the scripts produce valid tapes accepted by the reader

## 7. 3D tunnel physics

- [ ] 7.1 GATE: complete `docs/determinism/gate.md` listing each verification criterion for groups 1-6 with a link to its evidence (audit report, ARM64 CI run, hash vectors, tape reference implementation, diff tool test, golden harness mutation test); verify every item is evidenced before any later task in this group begins
- [x] 7.2 Define `TunnelState`, `TunnelRules` and the `TunnelState` hash encoder with its completeness test; verify a missing state field causes the encoder-coverage assertion to fail
- [x] 7.3 Implement the input-only fixed-step reducer skeleton `reduceTunnel(state, inputs, rules)` with bounded paddle movement; verify targets are reached in the expected number of steps without puck physics
- [ ] 7.4 Implement swept wall, floor and ceiling collision with an iteration cap; verify with property tests that the puck never leaves the cross-section at any speed and zero spin stays zero
- [ ] 7.5 Implement the spin curve and per-step decay; verify with tests that zero spin travels straight, constant spin bends toward the spin's side, and reversed spin mirrors the path exactly
- [ ] 7.6 Implement the swept sphere-versus-moving-paddle contact and goal detection; verify with tests for a fast puck hitting a covering paddle, a puck outside the paddle's extent passing, and a fast-moving paddle not missing
- [ ] 7.7 Implement sweep-based spin on paddle contact with clamping; verify a stationary paddle imparts none, direction follows paddle movement, and magnitude never exceeds the maximum
- [ ] 7.8 Generate and commit the three golden tapes and traces with `updateTunnelGoldens`; verify the golden test passes and the mutation runner fails the goldens for every constant in `TunnelRules`
- [ ] 7.9 Add tunnel goldens to `determinismParity` as a blocking check; verify the ARM64 job reports identical hash sequences to x86-64
- [ ] 7.10 Add a test that replays each golden tape in two fresh processes; verify identical traces
- [ ] 7.11 Record the tunnel mechanics and constants in a new ADR in `.github/adr/` (do not stage the file in git); verify the constants listed match `TunnelRules`

## 8. Integration checks

- [ ] 8.1 Run `./gradlew build`, `ktlintCheck` and `spotlessCheck`; verify they pass and that no new task is pulled into `build` other than tests
- [x] 8.2 Verify nothing live changed: no edits to `GameRoom`, `RoomRegistry`, `GameWebSocket`, `replay.proto`, `game_state.proto` or the frontend, and version 1-3 replay fixtures still pass
- [x] 8.3 Run `openspec validate add-3d-soccer-game-mode --strict`; verify it passes

## Workflow follow-up

- Propose the follow-up change for the 3D button, rendering, state protocol and room integration.
- Archive this change after review.

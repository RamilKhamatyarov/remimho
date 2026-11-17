# Tunnel physics gate

Status: CLOSED for tunnel physics. State/hash contracts and the input-only reducer scaffold may be
verified locally; no puck physics task (7.4-7.11) may start until ARM64/numerical gate evidence is
complete. The scaffold is not a real tunnel physics reducer.

- [x] Canonical hashing, completeness and committed vector tests verified by
  [`StateHashTest`](../../src/test/kotlin/ru/rkhamatyarov/determinism/StateHashTest.kt)
  and `src/test/resources/determinism/vectors/mvi-states.txt`.
- [x] Trace format, field diff and CLI exit status verified by
  [`TraceDiffTest`](../../src/test/kotlin/ru/rkhamatyarov/determinism/TraceDiffTest.kt)
  and the commands in [`README.md`](README.md).
- [x] Fresh-process and interpreter audit evidence reviewed in
  [`2d-audit.md`](2d-audit.md); artifacts remain local and ARM64 is pending.
- [ ] ARM64 CI run linked and cross-architecture results reviewed.
- [ ] ADR 0018's provisional fixed-point choice is confirmed or revised after ARM64 evidence.
- [ ] Substrate vectors pass on x86-64 and ARM64.
- [x] Input tape validation, truncation and deterministic recording verified by
  [`TunnelTapeTest`](../../src/test/kotlin/ru/rkhamatyarov/tunnel/TunnelTapeTest.kt)
  and [`tunnel-tape.md`](tunnel-tape.md).
- [x] Golden harness, scenario scripts and mutation self-tests verified with a
  stand-in reducer by [`TunnelGoldenHarnessTest`](../../src/test/kotlin/ru/rkhamatyarov/tunnel/TunnelGoldenHarnessTest.kt).
- [ ] `updateTunnelGoldens` updater implemented against the real fixed-step reducer.

The machine-readable blocker list is [`physics-gate.yaml`](physics-gate.yaml); run
[`determinismGate`](golden-updater.md#physics-gate) to print the evidence table and obtain a
nonzero exit while blockers remain. Golden update policy is documented in
[`golden-updater.md`](golden-updater.md).

The workflow definition is not evidence of a successful ARM64 run. This local
workspace cannot establish cross-architecture parity by itself. Record a CI run
URL here before accepting that criterion. Q32.32 is implemented as a provisional
risk-reduction choice; its reference vectors have only been run locally on x86-64
and do not open the physics gate.

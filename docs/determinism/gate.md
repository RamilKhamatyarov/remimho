# Tunnel physics gate

Status: OPEN for tunnel physics (2026-11-19). The real fixed-step reducer and the goldens produced
through it now exist, so the deferred-physics prerequisites are met.

- [x] Canonical hashing, completeness and committed vector tests verified by
  [`StateHashTest`](../../src/test/kotlin/ru/rkhamatyarov/determinism/StateHashTest.kt)
  and `src/test/resources/determinism/vectors/mvi-states.txt`.
- [x] Trace format, field diff and CLI exit status verified by
  [`TraceDiffTest`](../../src/test/kotlin/ru/rkhamatyarov/determinism/TraceDiffTest.kt)
  and the commands in [`README.md`](README.md).
- [x] Fresh-process and interpreter audit evidence recorded in
  [`2d-audit.md`](2d-audit.md); local run outputs are not committed.
- [x] [ARM64 CI run 44112987043](https://github.com/RamilKhamatyarov/remimho/actions/runs/44112987043)
  completed via `workflow_dispatch`; its hash-vector and all 12 2D trace-comparison steps passed.
- [x] [ADR 0018](../../openspec/adr/0018-tunnel-numerical-substrate.md) and D5 select the strict IEEE subset for future tunnel physics from the matching
  2D trace evidence.
- [x] Substrate operation vectors passed on x86-64 and ARM64 in the blocking `determinismParity`
  check.
- [x] Input tape validation, truncation and deterministic recording verified by
  [`TunnelTapeTest`](../../src/test/kotlin/ru/rkhamatyarov/tunnel/TunnelTapeTest.kt)
  and [`tunnel-tape.md`](tunnel-tape.md).
- [x] Golden harness, scenario scripts and mutation self-tests verified with a
  stand-in reducer by [`TunnelGoldenHarnessTest`](../../src/test/kotlin/ru/rkhamatyarov/tunnel/TunnelGoldenHarnessTest.kt).
- [x] `updateTunnelGoldens` updater implemented against the real fixed-step reducer; it generates
  candidates from the committed scenario scripts, compares by default, and only writes after the
  bootstrap or ARM64-provenance guard. The three goldens are committed under
  `src/test/resources/tunnel/golden/`.

The machine-readable blocker list is [`physics-gate.yaml`](physics-gate.yaml); run
[`determinismGate`](golden-updater.md#physics-gate) to print the evidence table. With every row
passing it prints `GATE OPEN` and exits zero.

Tunnel physics is defined in [ADR 0019](../../openspec/adr/0019-tunnel-physics-mechanics.md). The
gate covers the determinism foundation and the standalone physics; it does not authorize room,
WebSocket, protobuf state or frontend integration.

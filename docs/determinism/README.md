# Determinism tools

Run from the repository root with JDK 21. On Windows replace `./gradlew` with
`.\gradlew.bat`. These diagnostic tasks are not dependencies of `build`.

```sh
./gradlew traceRecord --args="src/test/resources/demo.replay build/determinism/demo.trace"
./gradlew traceDump --args="src/test/resources/demo.replay 1 build/determinism/demo.dump"
./gradlew traceDiff --args="build/determinism/demo.trace build/determinism/demo.trace"
./gradlew determinismAudit -PauditLabel=first
./gradlew determinismAudit -PauditLabel=second -PauditCompare=first
./gradlew determinismAudit -PauditLabel=interpreted -PauditCompare=first -PauditJvmArgs=-Xint
./gradlew determinismParity
./gradlew determinismParity2d
```

The CLI reports status 0 for agreement, 1 for divergence and 2 for invalid or
incomparable inputs. Gradle wraps a nonzero child status as a failed task.
Trace comparisons report shared-prefix agreement separately from length differences;
the audit requires identical lengths as well as identical hashes.

Hash-vector checks block CI. The ARM64 2D comparison is report-only; the current 12-recording
corpus passed in [run 44112987043](https://github.com/RamilKhamatyarov/remimho/actions/runs/44112987043).
Download `determinism-arm64` from CI to inspect its traces and exact runtime metadata.

Reference regeneration is explicit: `./gradlew determinismUpdateReferences`.
Review any reference changes; do not regenerate them merely to hide a failure.
The tunnel input tape, Q32.32 vectors, state hash contract, real fixed-step reducer and committed
physics goldens are available under `ru.rkhamatyarov.tunnel`. Live integration is still absent.
ARM64 parity passed for the current 2D trace corpus; exact ARM runtime metadata remains to be
reviewed from its uploaded artifact. [ADR 0018](../../openspec/adr/0018-tunnel-numerical-substrate.md) selects a strict IEEE subset for future tunnel
physics, and [ADR 0019](../../openspec/adr/0019-tunnel-physics-mechanics.md) retains Q32.32 for the
implemented tunnel physics and opens gate 7.1; see
[`tunnel-tape.md`](tunnel-tape.md) and [`gate.md`](gate.md).

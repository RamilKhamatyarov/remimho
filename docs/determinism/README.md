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

Hash-vector checks block CI. The ARM64 2D comparison is report-only until the audit
is concluded. Download `determinism-arm64` from CI to inspect its traces and report.
No cross-architecture parity claim is valid until that evidence has been reviewed.

Reference regeneration is explicit: `./gradlew determinismUpdateReferences`.
Review any reference changes; do not regenerate them merely to hide a failure.
The tunnel input tape, provisional Q32.32 operation vectors, state hash contract and input-only
reducer scaffold are available under `ru.rkhamatyarov.tunnel`. No puck physics or live integration
is implemented. The Q32.32 decision and ARM64 parity are pending external verification; see
[`tunnel-tape.md`](tunnel-tape.md) and [`gate.md`](gate.md).

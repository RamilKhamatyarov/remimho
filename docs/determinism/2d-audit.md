# 2D audit (ARM64 parity observed; runtime metadata review pending)

Local verification: 2026-11-17, Windows amd64, OpenJDK 64-Bit Server VM
21.0.1+12-29. Two fresh JVM runs and one `-Xint` process agreed on every
step of all 12 recordings. Windows denied access to WMI processor metadata, so
the CPU model is unavailable; `amd64` is the JVM-reported architecture only.

On the same date, `./gradlew determinismParity determinismParity2d` passed locally:
hash vectors matched and all 12 current traces matched their committed references.

ARM64 evidence was produced by [CI run 44112987043, ARM64 parity job](https://github.com/RamilKhamatyarov/remimho/actions/runs/44112987043/job/134143336027),
triggered manually with `workflow_dispatch` on commit `041acd7f8bd40f4e2b312b4b1857b682df3099dc`.
GitHub reports the blocking `determinismParity` step, report-only `determinismParity2d` step,
manifest writer, and artifact upload all succeeded. The run uploaded the `determinism-arm64`
artifact. The manifest writer maps successful hash and trace steps to `hashVectors=pass` and
`traceParity=pass`; these values are derived from the successful steps because the artifact bytes
could not be downloaded in this workspace. The trace comparison task exits successfully only when
every recording has matching hashes and lengths. Thus all 12 recordings below matched the
committed x86 references on ARM64, with no divergent step or fields. Hash-vector success is
reported separately: it validates integer-only hashing and Q32.32 operation vectors, not
floating-point 2D physics.

The ARM runner was configured as `ubuntu-24.04-arm` with `actions/setup-java` Temurin 21. The
uploaded report contains the observed `os.arch` and JVM runtime version; those exact values and
the physical CPU model are not available locally. The x86 CPU model is also unavailable. Task 3.7
remains open until exact runtime metadata and durable local repeat/interpreter evidence are recorded.

Commands are in [the tool README](README.md). Local output directories are
`build/determinism/first`, `second`, and `interpreted`; these build artifacts
are not committed evidence. Reference traces are under
`src/test/resources/determinism/2d/`.

| Recording (committed x86 trace) | Steps | Final hash | Second JVM / `-Xint` | ARM64 comparison |
| --- | ---: | --- | --- | --- |
| [bounce-angles](../../src/test/resources/determinism/2d/bounce-angles.trace) | 2400 | `7287119c925582ef` | identical / identical | identical |
| [bounce-angles-v1](../../src/test/resources/determinism/2d/bounce-angles-v1.trace) | 2400 | `950145718a90821a` | identical / identical | identical |
| [bounce-angles-v2](../../src/test/resources/determinism/2d/bounce-angles-v2.trace) | 2400 | `7287119c925582ef` | identical / identical | identical |
| [line-collisions](../../src/test/resources/determinism/2d/line-collisions.trace) | 2400 | `270b0de03b735bf8` | identical / identical | identical |
| [rabbit-turns](../../src/test/resources/determinism/2d/rabbit-turns.trace) | 2400 | `4b3825ba32d2f82e` | identical / identical | identical |
| [power-ups](../../src/test/resources/determinism/2d/power-ups.trace) | 2400 | `903a081889c0a0dd` | identical / identical | identical |
| [spin-decay](../../src/test/resources/determinism/2d/spin-decay.trace) | 2400 | `6d66cab71288773c` | identical / identical | identical |
| [edge-hit-swipe](../../src/test/resources/determinism/2d/edge-hit-swipe.trace) | 600 | `95831e91247a94cd` | identical / identical | identical |
| [edge-hit-retreat](../../src/test/resources/determinism/2d/edge-hit-retreat.trace) | 600 | `74232809a10cf923` | identical / identical | identical |
| [edge-hit-spinning](../../src/test/resources/determinism/2d/edge-hit-spinning.trace) | 600 | `ddeb19020a771db3` | identical / identical | identical |
| [extreme-speed](../../src/test/resources/determinism/2d/extreme-speed.trace) | 2400 | `e47ebae8fb9c57eb` | identical / identical | identical |
| [demo](../../src/test/resources/determinism/2d/demo.trace) | 180 | `9d9dc96f9b1c31f8` | identical / identical | identical |

Remaining for task 3.7: review and record the exact ARM64 runtime string and runner metadata from
the uploaded report, and preserve durable same-machine and `-Xint` output evidence. Cross-architecture
trace parity itself has passed. [ADR 0018](../../openspec/adr/0018-tunnel-numerical-substrate.md) records the D5 substrate decision from that result. Tunnel physics and its goldens landed
separately in [ADR 0019](../../openspec/adr/0019-tunnel-physics-mechanics.md), which opens the gate.

### ARM64 emulation attempt

Attempted 2026-11-17 at repository HEAD `8b08df7dbd52699df81624f02efb2c5d60fc9b6c`.
Docker CLI 20.10.17 is installed and the Docker Desktop service reports `Running`, but
`docker version` cannot connect because `//./pipe/docker_engine` does not exist. Consequently
there was no Docker engine, no QEMU/binfmt installation, and no emulated execution. No emulated
hash result was produced; this attempt is not parity evidence.

## State-Affecting Numeric Inventory

Inventory reviewed from the reducer path and source imports. `+`, `-`, `*`, `/`, `%`, numeric
comparisons and numeric conversions are basic JVM arithmetic; they are not implemented through a
library math facade. The table identifies each source unit where such arithmetic reaches physics,
timing, collision or scoring. “Library calls” names the non-basic numeric operations to audit for
cross-architecture behavior.

| File / class | Basic arithmetic used | Library numeric calls |
| --- | --- | --- |
| `BoundaryPhysics.kt` / `BoundaryPhysics` | `+ - * /`, travel fractions and remaining-segment scaling | `kotlin.math.abs` |
| `PaddlePhysics.kt` / `PaddlePhysics` | `+ - * /`, normalization, interpolation, collision slabs and velocity scaling | `abs`, `cos`, `hypot`, `max`, `min`, `sin` from `kotlin.math` |
| `LineCollisionPhysics.kt` / `LineCollisionPhysics` | `+ - * /`, squared distances, reflection and portal rotation | `atan2`, `cos`, `hypot`, `sin` from `kotlin.math` |
| `PuckPhysics.kt` / `PuckPhysics` | `+ - * /`, elapsed-nanosecond conversion, decay and integration | `abs`, `hypot` from `kotlin.math`; `StrictMath.pow` |
| `RabbitPhysics.kt` / `RabbitPhysics` | `+ - * / %`, logical turn parity and velocity rotation | `cos`, `sin` from `kotlin.math` |
| `SpeedModel.kt` / `SpeedModel` | `+ - * /`, progression and speed growth | `abs`, `hypot`, `min` from `kotlin.math` |
| `OneTimerMechanic.kt` / `OneTimerMechanic` | `+ - * /`, multiplier interpolation and speed cap | `hypot` from `kotlin.math` |
| `TickReducer.kt` / `TickReducer` | `+ -`, elapsed-time and score updates, power-up geometry checks | `hypot` from `kotlin.math` |
| `MviReducer.kt` / `MviReducer` | `+ - /`, paddle travel, arena midpoint and serve initialization | None |
| `MviGameModels.kt` / `MviPuck`, `MviActivePowerUp`, `MviGameState` | `+ - * /`, time conversion and expiration/remaining-duration calculations | None |

Model declarations and rule/config objects also contain `Double` values but do not themselves
perform state-affecting arithmetic. `TouchLedger` stores speed values and timestamps; combo checks
perform logical `Long` subtraction/comparison, not floating-point operations.

## Transcendental and Magnitude Call Sites

Reproducible search: `rg -n 'sin\(|cos\(|atan2\(|hypot\(|pow\(' src/main/kotlin/ru/rkhamatyarov/service/mvi`.

| File / class | Calls | Classification |
| --- | --- | --- |
| `LineCollisionPhysics.kt` | `sin` x2, `cos` x2, `atan2` x1, `hypot` x1 | `kotlin.math`, platform `Math` candidates |
| `RabbitPhysics.kt` | `sin` x2, `cos` x2 | `kotlin.math`, platform `Math` candidates |
| `PaddlePhysics.kt` | `sin` x1, `cos` x1, `hypot` x2 | `kotlin.math`, platform `Math` candidates |
| `PuckPhysics.kt` | `hypot` x2, `StrictMath.pow` x1 | `kotlin.math.hypot`; `StrictMath` power |
| `SpeedModel.kt` | `hypot` x1 | `kotlin.math`, platform `Math` candidate |
| `OneTimerMechanic.kt` | `hypot` x1 | `kotlin.math`, platform `Math` candidate |
| `TickReducer.kt` | `hypot` x1 | `kotlin.math`, platform `Math` candidate |

The reviewed allowlist totals 20 calls: five `sin`, five `cos`, one `atan2`, eight `hypot`, and one
`StrictMath.pow`. The inventory is scoped to explicit state-affecting transcendental calls. Basic `+`, `-`,
`*`, `/`, comparisons and conversions also occur throughout `service/mvi`; they are ordinary
JVM floating-point operations, not individually enumerated here. A source search finds no
`Random`, wall-clock API or direct `java.lang.Math` import in these classes. Cross-architecture
behavior remains unknown until an ARM64 trace is available.

## Reproducibility Classification

- Basic Java `double` arithmetic follows strict IEEE-754 semantics on Java 17 and later; with the
  same operation sequence and inputs, these operations are the deterministic baseline.
- The `StrictMath.pow` call in `PuckPhysics` follows Java's specified `StrictMath` implementation
  and is the only current transcendental call with an explicit cross-platform reproducibility
  contract.
- The listed `kotlin.math` calls use JVM math-library implementations. Their cross-architecture
  bit identity is not assumed universally; the x86-64 and ARM64 runs matched for this committed
  corpus. Future tunnel code must follow the stricter `StrictMath`/specified-algorithm policy in
  [ADR 0018](../../openspec/adr/0018-tunnel-numerical-substrate.md) rather than treating this corpus as a general guarantee.
- This inventory guard pins the present transcendental/magnitude call counts. Any new call requires
  review, updated evidence, and an intentional allowlist change.

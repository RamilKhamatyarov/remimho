# ADR 0018: Strict IEEE Substrate for Future Tunnel Physics

- Status: Accepted
- Date: 2026-11-17
- Deciders: Game systems architecture

## Context

The tunnel mode needs reproducible state transitions. The 2D audit's inventory records 20
non-basic math invocations across ten state/physics units: five `sin`, five `cos`, one `atan2`,
eight `hypot`, and one `StrictMath.pow`. ARM64 CI run 44112987043 passed the hash-vector check and
the report-only per-step comparison of all 12 2D traces against committed x86-64 references. The
run was manually dispatched on commit `041acd7f8bd40f4e2b312b4b1857b682df3099dc`; see the
[audit report](../../docs/determinism/2d-audit.md) and
[ARM64 job](https://github.com/RamilKhamatyarov/remimho/actions/runs/44112987043/job/134143336027).

## Decision

Use a strict IEEE subset for future tunnel physics: basic `Double` `+`, `-`, `*`, `/`, and `sqrt`,
with `StrictMath` or specified integer algorithms for non-basic operations; do not use
architecture-dependent `Math.fma`. This is preferred over fixed-point because the ARM64 run's
`determinismParity2d` step succeeded. The workflow's manifest writer maps that successful step to
`traceParity=pass`; the artifact upload also succeeded. The report's trace comparison exits zero
only if all steps and lengths match. This is evidence for the tested corpus and JVMs, not a proof
for arbitrary math operations or hardware. Hash-vector status is separate: it validates the
integer-defined hasher and Q32.32 operation vectors, not floating-point 2D physics.

The existing Q32.32 scalar/facade and input-only reducer remain pre-physics scaffolding and
reference vectors; Q32.32 is not the selected future physics substrate. Replace or explicitly
retain that scaffold during implementation, before any tunnel physics work. Keep gate 7.1 closed
until the deferred-physics prerequisites are met.

`TunnelScalar` stores a signed raw `Long` scaled by 2^32. Its representable range is
`[-2^31, 2^31 - 2^-32]` world units, with resolution 2^-32 (approximately 2.3283e-10). The tape
limits dimensions to 10,000 units, comfortably within that range. `TunnelMath` uses integer
arithmetic, `BigInteger` intermediates for multiply/divide, truncation toward zero, and exact
conversion back to `Long`; unrepresentable results fail rather than wrap. No trigonometric or
platform math operation is provided by this initial facade. Physics requiring such operations must
extend it with specified integer algorithms and committed operation vectors before use.

## Scope and Evidence

The scalar, operation facade, x86-64 reference vectors, and input-only reducer scaffolding are
isolated under `ru.rkhamatyarov.tunnel`. The vectors are arithmetic references, not puck-physics
goldens. Run 44112987043 uploaded `determinism-arm64`; GitHub reports its blocking vectors,
2D-trace, manifest, and upload steps succeeded. The exact ARM runtime string and CPU model remain
to be copied from the artifact; the manifest-derived `traceParity=pass` is supported by the
successful comparison step and the workflow's status mapping.

## Consequences

- Tunnel scalar range and truncation semantics are explicit and tested.
- The retained Q32.32 scaffold uses `BigInteger` intermediates and costs more than primitive
  arithmetic; it is not the selected physics substrate.
- Collision and curve behavior remain unimplemented; this decision alone does not open the physics
  gate or authorize golden generation.
- Live rooms, WebSockets, protobuf live state, frontend behavior, and 2D physics are unchanged.

## 2D Migration and Replay Compatibility

Migrating the existing 2D simulation would require changing state and arithmetic in ten inventory
units (`MviGameModels`, `MviReducer`, `TickReducer`, `PuckPhysics`, `BoundaryPhysics`,
`PaddlePhysics`, `LineCollisionPhysics`, `RabbitPhysics`, `SpeedModel`, and `OneTimerMechanic`),
including 20 non-basic math invocations and ordinary arithmetic throughout those units. This ADR
does not authorize that migration.

If a later 2D migration is approved, it requires replay version 4 with explicit numeric semantics.
Replay versions 1-3 must continue using their existing arithmetic and fixtures without behavioral
changes.

## Revisit Criteria

Before tunnel physics begins, record the exact ARM runtime metadata from the uploaded report and
implement the selected strict IEEE subset with its own vectors. Any future cross-architecture
divergence blocks physics work and requires revisiting this decision; it never justifies changing
replay versions 1-3 or adding placeholder physics goldens.
## Follow-up

[ADR 0019](0019-tunnel-physics-mechanics.md), accepted 2026-11-19, explicitly retains the Q32.32
substrate for the implemented tunnel physics and opens gate 7.1. The scaffolding wording above
describes the pre-implementation state; the strict IEEE subset remains the direction for any future
2D migration, which still requires replay version 4.

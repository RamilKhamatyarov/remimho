# Tunnel physics mechanics and constants

Status: accepted, 2026-11-19. Implements the physics behind the `tunnel-physics` capability of
`add-3d-soccer-game-mode`.

## Context

ADR 0018 selected a strict IEEE subset for future tunnel physics and stated that the existing
Q32.32 facade was scaffolding that had to be either replaced or explicitly retained before physics
could start. The tape, state hash and golden harness already encode tunnel scalars and coordinates
as raw Q32.32 longs, and the substrate vectors proved Q32.32 identical on x86-64 and ARM64. This ADR
therefore explicitly retains Q32.32 for tunnel physics. 2D physics is untouched and keeps versions
1-3 on their current arithmetic; a future 2D migration would require replay version 4, as ADR 0018
records.

The gate in `docs/determinism/gate.md` is opened by this ADR together with the committed goldens:
the deferred-physics prerequisites were the real reducer and the goldens produced through it.

## Decision

Axes: `z` runs along the tunnel between the paddles, `x`/`y` are the cross-section. Paddle A guards
the `z = 0` plane, paddle B the `z = depth` plane, and each paddle is a rectangle centred on its
state position.

One fixed step performs, in this order:

1. apply the step's inputs in tape order (`PADDLE_TARGET`, `SERVE`, `RESET`);
2. move each paddle toward its target by at most `paddleTravelPerStep`, recording the displacement;
3. apply the spin curve as a sideways acceleration in the cross-section;
4. sweep the puck through the step, resolving the earliest contact first and repeating on the
   remaining fraction, up to `maxReflectionsPerStep`;
5. reflect at side walls, floor and ceiling and keep the puck inside the cross-section;
6. detect a goal when the puck crosses a paddle plane outside the paddle rectangle, then freeze it;
7. decay spin by `spinDecayPerStep`.

Paddle contact is a swept test: the puck is a sphere and the paddle rectangle is expanded on each
axis by the puck radius, the step's paddle travel and the absolute paddle displacement, so neither a
fast puck nor a fast paddle misses. A face contact reflects `vz` and preserves the cross-section
velocity. The imparted spin is the paddle displacement times `paddleSpinGain`, added to the incoming
spin and clamped to `maxSpin`.

All arithmetic is integer-only Q32.32 through `TunnelMath`, with `BigInteger` intermediates and
`longValueExact` conversion. Velocities and accelerations are expressed per step in tunnel units, so
physics never needs the step duration; the duration remains tape identity metadata. Times are step
fractions in `[0, 1]`; a crossing beyond the step is treated as infinite rather than computed, which
also keeps division inside the long range.

## Constants (`TunnelRules` defaults)

| Constant | Value | Meaning |
| --- | --- | --- |
| `stepDurationNs` | 8_333_333 | Tape identity only; not used by the fixed-step arithmetic |
| `width` x `height` x `depth` | 800 x 600 x 300 | Tunnel dimensions in units |
| `puck.radius` | 0.5 | Sphere radius from the initial state |
| `paddleTravelPerStep` | 8 | Maximum paddle displacement per step |
| `paddleHalfWidth` | 60 | Paddle half extent along `x` |
| `paddleHalfHeight` | 45 | Paddle half extent along `y` |
| `spinCurveAcceleration` | 0.05 | Cross-section velocity added per spin unit per step |
| `spinDecayPerStep` | 0.99 | Multiplicative spin decay per step |
| `paddleSpinGain` | 0.1 | Spin units added per unit of paddle displacement |
| `maxSpin` | 1.5 | Absolute spin clamp on both cross-section axes |
| `maxReflectionsPerStep` | 8 | Wall-reflection cap; a pathological puck is clamped and stopped |

Physics version: `tunnel-v1`. Substrate id: `q32.32-provisional`, retained so existing tape headers
stay readable; the facade itself is now the selected substrate, not scaffolding.

## Consequences

- Recorded tunnel states remain reproducible by construction; the source guard forbids
  `kotlin.math`, `java.lang.Math`, `Random` and clock access under `ru.rkhamatyarov.tunnel`.
- Goldens under `src/test/resources/tunnel/golden/` pin every physics constant: altering one makes
  the mutation runner fail, and the golden test names the first divergent step.
- Changing any constant, the step order or the contact model requires bumping `tunnel-v1`,
  regenerating the goldens through `updateTunnelGoldens --bootstrap` (only while the directory is
  empty) or through the ARM64-provenanced update path, and reviewing the trace diff.
- Tunnel physics remains standalone: no room, WebSocket, protobuf state or frontend integration is
  added by this change.

## Verification

- `TunnelPhysicsTest` covers straight zero-spin travel, cross-section containment across speeds,
  mirrored spin curves, stationary and moving paddle contact, spin clamping, covered and missed
  paddles, and the iteration cap.
- `TunnelGoldenTest` replays all three committed goldens and runs the constant mutation check.
- `TunnelGoldenFreshProcessTest` replays each golden in two fresh JVMs and compares with the
  committed trace.
- `determinismParity` makes `tunnel golden traces` a blocking check alongside the hash and substrate
  vectors.

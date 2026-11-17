# Paddle edges and motion-frame recovery

## Context

The face-plane properties in 0011 excluded top/bottom entries when the puck was
already beside the paddle. The room-only integration tests also bypassed
WebSocket delivery. Passing those tests did not reproduce either failure path.

A fixed regression starts at (15, 224), moving at (10, 2000), with paddle A at
y=250. After 16 ms the old reducer returns (15.16, 256), inside the paddle,
without a touch. Horizontal velocity points away from the goal, but vertical
velocity points into the paddle's top edge. Mirror X for paddle B and Y for
bottom-edge entry. The property test fails on the old code on its first case.

The WebSocket opening-snapshot test also fails: `onOpen` creates a cold
`sendBinary` operation without subscribing. Subsequent changed-only frames
cannot reconstruct unchanged initial fields. Moreover, the broadcast flow can
drop a movement frame under backpressure, leaving a stationary paddle at an
obsolete client position while puck coordinates continue updating.

## Decision

- Apply horizontal separation rejection only to face contacts after determining
  the collision axis. Top/bottom contacts use the existing swept rectangle and
  vertical reflection. Do not change the normal face bounce or edge reward rules.
- Subscribe to the opening full-snapshot send and log send failures.
- Include complete puck motion, both paddle positions, score and pause state in
  each live motion frame. Keep existing collection-delta behavior rather than
  repeating all drawn geometry at 60 Hz. Bounded buffering remains unchanged.
- Retain the original properties and add 5,000 edge cases for all four edges,
  room-actor regressions with human/bot control, and real WebSocket tests for
  initialization and recovery after discarding a movement update.

## Consequences

No new core state, replay intent, schema field or wall-clock dependency is added.
The corrected edge collisions can change playback of affected old recordings;
this is not a claim of physics-version compatibility. Motion frames are slightly
larger but no longer depend on delivery of earlier motion frames. Collection
recovery and client interpolation are outside this change.

These are concrete reproduced defects, not proof that every reported visual
overlap has the same cause. Keep the previous opening-serve and wall-sweep tests.

## Verification

Before the fix, the edge property, room edge regression, opening snapshot test,
and dropped-movement recovery test all failed. Afterward, the full JVM suite
passed 209 tests with no skips, the frontend passed 15 tests, and ktlint and
Spotless passed. Regenerating the demo GIF produced no artifact changes.
No native compilation was run.

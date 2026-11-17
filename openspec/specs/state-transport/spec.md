# State Transport Specification

## Purpose

Live clients receive state over WebSocket binary frames, and the browser decoder must tolerate additive protobuf fields while every motion frame remains self-contained after a dropped update. See `openspec/adr/0009-state-decoding-and-alternating-serves.md` and `0012-paddle-edges-and-motion-frame-recovery.md`.

## Requirements

### Requirement: Unknown length-delimited fields are skipped correctly

The browser decoder SHALL skip an unknown length-delimited protobuf field using the reader offset after reading that field's length, not the offset from before it.

#### Scenario: Delta carrying touch-ledger and combo fields

- **WHEN** the decoder receives a delta that includes additive combo or ledger fields it does not render
- **THEN** it decodes without exception and reports the correct score, puck and paddle values

### Requirement: The opening frame is a subscribed full snapshot

The WebSocket `onOpen` path SHALL subscribe to the opening full-snapshot send and SHALL log send failures. Subsequent changed-only frames SHALL be reconstructable from that snapshot, and a failed opening send SHALL be observable in logs.

#### Scenario: Opening snapshot establishes the baseline

- **WHEN** a client connects to a room
- **THEN** it receives a full snapshot whose unchanged fields remain available while later changed-only frames arrive

### Requirement: Every live motion frame is self-contained

Each live motion frame SHALL include complete puck motion, both paddle positions, score and pause state. Collection-delta behaviour for drawn geometry SHALL be retained rather than repeating all geometry at 60 Hz, and bounded buffering SHALL remain in force.

#### Scenario: Paddle position after a discarded movement update

- **WHEN** a movement update is dropped under backpressure and the next motion frame arrives
- **THEN** the client recovers the authoritative paddle position from that frame without depending on the dropped update

#### Scenario: Drawn geometry still arrives as collection deltas

- **WHEN** motion frames stream during a rally
- **THEN** they carry collection deltas rather than repeating every drawn object each frame

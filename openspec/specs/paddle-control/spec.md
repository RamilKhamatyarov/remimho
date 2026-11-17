# Paddle Control Specification

## Purpose

Player and bot paddle aiming share one movement contract: `MovePaddle.y` is the requested paddle centre, not its top edge, and the reducer keeps the paddle inside the playfield. See `openspec/adr/0008-paddle-rectangle-sweep-and-centre-aiming.md`.

## Requirements

### Requirement: MovePaddle y addresses the paddle centre

`GameAction.MovePaddle.y` SHALL be interpreted as the requested centre of the paddle. The reducer SHALL subtract half the paddle height before clamping the paddle to the playfield.

#### Scenario: Cursor points at the puck

- **WHEN** a client sends the cursor Y as a paddle move
- **THEN** the paddle centre lands on that Y and the bounce angle is computed from the centre, not the top edge

#### Scenario: Bot converts once at its emit boundary

- **WHEN** `BotController` computes a target with its internal top-edge arithmetic
- **THEN** it converts to centre semantics exactly once when it emits the `MovePaddle` intent

### Requirement: Paddle positions stay inside the playfield

After converting the requested centre, the reducer SHALL clamp the paddle so the full paddle rectangle remains within the playfield for both sides.

#### Scenario: Request near the top or bottom edge

- **WHEN** a requested centre would place part of the paddle outside the playfield
- **THEN** the paddle is clamped to the nearest position fully inside the field

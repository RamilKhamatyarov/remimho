# Bot Opponent Specification

## Purpose

The bot is a room-owned, replayable participant: it emits ordinary paddle intents from immutable state and logical time, and its difficulty is a four-parameter configuration that hardens over the match. See `openspec/adr/0002-room-owned-replayable-bot-controller.md` and `0006-bot-difficulty-scaling.md`.

## Requirements

### Requirement: The bot emits ordinary recorded intents

Each game room SHALL own a single `BotController` that reads immutable game state using logical `elapsedNs` and emits ordinary `GameAction.MovePaddle` intents through the same path as human input. Those intents SHALL be recorded in the replay log, and replay import SHALL consume the recorded intents without running the controller again. Human ownership of paddle A SHALL always take precedence over the bot.

#### Scenario: Replay does not rerun the bot

- **WHEN** a recorded match is imported headlessly
- **THEN** the bot's decisions come only from the recorded `MovePaddle` intents

#### Scenario: Human takes over paddle A

- **WHEN** a human player claims paddle A in a room configured with a bot
- **THEN** the bot stops controlling that paddle

### Requirement: Difficulty is a four-parameter configuration

Bot difficulty SHALL be described only by `reactionDelayMs` (delay before reacting to a new puck position), `aimError` (positioning inaccuracy), `predictionDepth` (projected bounces) and `aggression` (attacking activity) on `AiOpponentConfig`. Easy, Normal and Hard SHALL be expressed purely as parameter values settable through the existing REST payload, with no separate difficulty type.

#### Scenario: Named difficulty levels are plain parameter points

- **WHEN** an operator selects Easy, Normal or Hard
- **THEN** the config uses `320/28.0/0/0.15`, `180/10.0/1/0.35` or `90/4.0/2/0.60` respectively

### Requirement: Difficulty scales with match time

`AiOpponentConfig.scaledForElapsed` SHALL derive an effective configuration from the logical clock: one stage per minute of `elapsedNs` capped at three stages, with `reactionDelayMs` and `aimError` multiplied by `1.0 - stages * 0.1`, `predictionDepth` gaining one bounce from the second stage onward and capped at four, and `aggression` rising by `0.05` per stage clamped to `0.0..1.0`. Scaling SHALL be a pure function of `elapsedNs` and the configured baseline.

#### Scenario: Two minutes into the match

- **WHEN** a match reaches two minutes of logical time
- **THEN** the effective config has a lower reaction delay and aim error, one extra prediction bounce, and slightly higher aggression compared with the baseline

#### Scenario: Replay produces the same bot behaviour

- **WHEN** the same `elapsedNs` values and baseline config are replayed
- **THEN** the derived effective configuration and emitted intents are identical to the live match

### Requirement: Bot paddle speed follows skill and the puck

The bot's paddle speed SHALL combine `BASE_PADDLE_SPEED` (260) with an aggression bonus, be raised to at least 1.15 times the puck's on-screen vertical speed, and be capped at the match's `maxEffectiveSpeed` rather than a fixed constant. Difficulty SHALL remain carried by reaction delay, aim error and prediction depth.

#### Scenario: Fast puck is tracked within the effective cap

- **WHEN** the puck's vertical speed exceeds a fixed 260 px/s but stays within `maxEffectiveSpeed`
- **THEN** the bot paddle can still arrive at the predicted contact point

### Requirement: Bot configuration is replayable state

The bot configuration SHALL be serialized in replay snapshots and restored on import. Existing replay protobuf field numbers SHALL remain reserved for backward import, while new bot configuration fields SHALL use new field numbers.

#### Scenario: Replay restores the bot config

- **WHEN** a replay recorded with a custom `AiOpponentConfig` is imported
- **THEN** the final state carries that same configuration

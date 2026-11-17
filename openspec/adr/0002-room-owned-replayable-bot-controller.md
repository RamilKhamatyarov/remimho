# Use a room-owned controller for replayable bot input

The bot is a built-in match participant controlled by a single `BotController` owned by each game room, not a plugin and not reducer logic. The controller reads immutable game state using logical `elapsedNs` and emits ordinary reliable paddle intents, so live decisions are deterministic, room-scoped, and recorded exactly like human input; replay import consumes those recorded intents without running the controller again. Bot configuration remains replayable game state, human ownership of paddle A always takes precedence, and difficulty changes decision quality through reaction delay, aim error, bounded wall-bounce prediction, aggression, and capped time-based scaling while every level shares the same physical paddle speed limit.

## Consequences

Existing replay protobuf field numbers remain reserved for backward import, while new bot configuration fields use new numbers. Visual controls remain in the current Workshop AI section, expanded through Impossible and Custom, and API contracts are separated into `request` and `response` packages instead of a mixed `dto` package.

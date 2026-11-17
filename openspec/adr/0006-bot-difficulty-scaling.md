# Bot difficulty scaling

The bot is not a separate module or service. It lives inside the application as a single component, `BotController`, owned by the room. It reads the current match state, decides where to move the paddle, and emits an ordinary `GameAction.MovePaddle` through the same intent path a human player uses, so nothing downstream needs to know whether a move came from a person or the bot. This keeps the bot inside the deterministic replay described in [0002](0002-room-owned-replayable-bot-controller.md).

Difficulty is described by four parameters on `AiOpponentConfig`: `reactionDelayMs` is the delay before the bot reacts to a new puck position, `aimError` is its positioning inaccuracy, `predictionDepth` is how many bounces it projects ahead, and `aggression` is how actively it attacks. No other knobs are needed; every difficulty level is a point in this four-value space.

Three difficulty levels are expressed purely as parameter values rather than as code, so an operator can set them through the existing `AiOpponentConfig` REST payload without a new type:

| Level  | reactionDelayMs | aimError | predictionDepth | aggression |
| ------ | --------------- | -------- | --------------- | ---------- |
| Easy   | 320             | 28.0     | 0               | 0.15       |
| Normal | 180             | 10.0     | 1               | 0.35       |
| Hard   | 90              | 4.0      | 2               | 0.60       |

Easy reacts slowly, misjudges position widely, and barely predicts. Normal reacts at a middling delay, predicts a single bounce, and carries a small error. Hard reacts quickly, positions accurately, predicts further, and attacks more often.

Difficulty also scales with match time. `AiOpponentConfig.scaledForElapsed` derives an effective config from the logical clock: one stage per minute of `elapsedNs`, capped at three stages. Each stage multiplies `reactionDelayMs` and `aimError` by an accuracy factor of `1.0 - stages * 0.1`, so both shrink together; `predictionDepth` gains one bounce from the second stage onward and is capped at four; `aggression` rises by `0.05` per stage and is clamped to `0.0..1.0`. Scaling is a pure function of `elapsedNs` and the configured baseline, so a replayed match produces the same bot behaviour as the live one, and no additional system is required to make the opponent harden over a match.

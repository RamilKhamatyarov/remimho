# Ghost mode paddle pass-through

Superseded for live gameplay by ADR 0016 (2026-11-03). The behavior below is retained
only for legacy replay compatibility; new matches use rabbit mode.

Accepted: 2026-10-27. Records current behavior and the evidence that explains reported
"puck through paddle" incidents. Gameplay changes are listed as open options, not decisions.

## Context

After ADRs 0007, 0008, 0011 and 0012 fixed several real collision defects, players still
reported the puck passing through paddles. Three capture dossiers (ADR 0014) were recorded
while the problem was visible. Together they hold 2,812 ticks and 7 goals.

| Ticks ending with the puck inside a paddle | Ghost mode off | Ghost mode on |
| ------------------------------------------ | -------------- | ------------- |
| Capture 1                                  | 0              | 3             |
| Capture 2                                  | 0              | 8             |
| Capture 3                                  | 0              | 90            |

With ghost mode off, the puck never ended a tick inside a paddle. Four of the seven goals went
through a paddle that was in the puck's path, all with ghost mode on: two against the human
paddle and two against the bot. The other three were genuine misses, 14, 22 and 63 px outside
the paddle's reach. A fourth capture was partial (server unavailable) and cannot be judged.

The reported pass-through is therefore ghost mode working as implemented, not a collision defect.

## Current behavior

- The `GHOST_MODE` power-up lasts 6 s (`PowerUpType.getDuration`). The automatic escalation
  schedule (`PowerUpEscalationStrategy`) spawns it among its eligible types.
- Any puck contact collects it. A power-up has no owner, so the effect applies to the whole
  match, not to the player who touched it last.
- While it is active, `PaddlePhysics.resolve` returns at its first line
  (`if (state.ghostMode) return frame`). **Both** paddles are ignored, faces and edges alike, and
  so is the paddle shield, which is resolved after that early return. Walls and drawn lines still
  collide.
- No paddle touch is recorded, so the goal veto from ADR 0007 never applies and a puck that
  crosses a goal line during the effect scores normally. One-timers and combos cannot trigger off
  a paddle that is ignored.
- A second pickup while the effect is active adds an independent 6 s entry
  (`active + collected`), so the effects overlap and ghost mode lasts until the last one expires.
- `TickReducer.finalize` derives `ghostMode` from the active list every tick, and `Reset` clears
  it. Replays and snapshots carry it (`FullGameSnapshot.ghost_mode`), so captured and replayed
  matches show the same pass-throughs.

## Problems

- **It looks like a bug.** The only cue is a small "👻" badge, and the puck is drawn as usual,
  so a player cannot tell a ghost pass-through from a collision failure.
- **The countdown under-reports.** `MviActivePowerUp.remainingSeconds` divides nanoseconds by a
  whole second, so the badge reads "0.0s" for the last second while the effect is still active.
- **It hurts the collector as much as the opponent.** The effect cancels both paddles, so picking
  it up carries no advantage, and players read the resulting goals as unfair.
- **Frequent in long matches.** Escalation keeps spawning it, and overlapping pickups extend it.
  In one capture ghost mode covered most of the 15 s window, partly because ticks recorded while
  paused do not advance logical time.

## Options

Not yet decided. Each option is independent of the others.

1. **Make it visible (rendering only).** While `ghostMode` is set, draw the puck translucent with
   a ghost outline, and show remaining time with one decimal place. No physics or replay change.
2. **Fix the countdown.** Report remaining time in milliseconds or fractional seconds. The
   truncated value is also what `GameStateMapper` writes into the delta's `remaining_seconds`
   and reads back on rewind, so a restored effect can come back up to almost a second shorter.
   Fixing it means a finer-grained delta field.
3. **Give it an owner.** Ghost mode lets the puck pass only the opponent of the side that last
   touched it, using `TouchLedger`. That makes collecting it an advantage. It changes physics, so
   recorded replays diverge, as noted in ADRs 0007 and 0013.
4. **Change what it ghosts.** Let the puck pass through drawn lines instead of paddles, so it
   stops undermining the core save mechanic.
5. **Do not stack.** Refresh the existing 6 s entry instead of adding a second one.

## Consequences of this record

Diagnosing future pass-through reports must check `ghostMode` first: in a capture, every frame's
`before.ghostMode` shows whether the paddles were active. Collision regression tests (ADRs 0007,
0008, 0011, 0012) remain valid only with ghost mode off, and they already run with it off by
default. Any option above that changes physics needs its own ADR and replay-divergence note.

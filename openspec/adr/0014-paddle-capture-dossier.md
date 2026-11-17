# Paddle Capture Dossier

Accepted: 2026-10-25. ADRs 0011-0013 show that a perceived paddle pass-through can
originate in physics, frame delivery, rendering, or changed speed rules. A goal-only
debug log cannot distinguish these cases.

## Decision

- `remimho.capture.enabled` defaults to `true` in every profile, including production.
  There is no `%dev` override. Set it explicitly to `false` to disable the recorder,
  browser buffering, participant tokens, and Capture button through `/client-config`.
- When enabled, retain the preceding 15 seconds of room diagnostics and browser
  evidence in bounded buffers outside the reducer, core state, and replay log.
- A Capture button downloads one ZIP without pausing or changing the match.
  Include replay segments, authoritative before/after motion and contacts, controller
  attribution, received/rendered browser positions, sent inputs, settings, and the
  canvas screenshot taken at the click. Never upload browser evidence automatically.
- Preserve reset and snapshot-restoration boundaries. Each replay segment has an
  exact starting snapshot; never assume a truncated replay starts from default state.
- Capture a consistent prefix of completed reductions. Serialization and packaging
  use that detached prefix, not independently sampled live state and replay log.
- If the server is unavailable, download browser evidence and mark the dossier
  partial. Exclude unrelated logs, cookies, authentication tokens, and connection IDs.
- Diagnostic retention uses monotonic observation time so reset/rewind cannot erase
  evidence. Logical elapsed time remains the only clock used by gameplay physics.

## Limits

Server buffers have a count and encoded-byte cap; client buffers have a count and
byte cap. Report truncation instead of promising a full 15 seconds under overload.
Reset/restore records are retained as boundaries, not new gameplay intents. Replays
reproduce physics using recorded effective actions, including bot moves and turbo
multipliers; they do not reconstruct the bot decision process or historical HUD charge.
Browser/server observation clocks are not assumed synchronized. Captures are evidence,
not automatic declarations that a collision was missed. Playback requires the same
physics version; this feature does not introduce historical reducer versioning.

The diagnostic endpoint requires a token issued to a currently connected participant
in that room, does not create unknown rooms, and disables HTTP caching. Dossiers contain room gameplay data and
should be reviewed before sharing. ZIP creation uses the small fflate client library
so disconnected captures do not require the server.

## Safety and Availability (Revised 2026-10-27)

- The initial dev-only default was superseded so players can capture intermittent
  bugs in packaged applications too. Enabled rooms pay the bounded recording cost;
  operators can opt out with `remimho.capture.enabled=false`. Disabled rooms use
  a no-op recorder, and the frontend waits for `/client-config` before buffering.
- Commit reliable state before guarded diagnostics. A diagnostic exception disables
  further diagnostics in that room, not its actor; subsequent intents still run.
  This isolates ordinary recording/logging failures, not unrecoverable JVM resource exhaustion.
- Retain encoded byte arrays rather than protobuf graphs: one baseline per segment,
  then one after-state per entry. Eviction promotes the predecessor as a new baseline.
  Oversized entries are skipped, earlier history stays intact, and the next retained
  entry starts a gap segment. Count and encoded-payload byte caps remain bounded.
- A random per-connection token is delivered only over that connection's WebSocket,
  sent in an HTTP header, never recorded in browser evidence, and revoked on close.
  A room-wide ten-second cooldown applies before decoding/export, including across
  participants and reconnections. This is participant access, not account authentication;
  the existing game still permits joining a known room ID.
- The browser retains detached objects in a circular buffer, samples rendering at
  most 60 Hz, and serializes JSON only at capture time. Oversized/deep objects are
  skipped with a limited-window marker. The byte budget is a conservative payload
  estimate, not a claim about JavaScript engine heap overhead.
- Schema 2 uses explicit payload DTOs and boundary values, with mapping outside the
  API model. Replay boundaries use `BoundaryKind`, never JVM class names.
- The Capture button has its own class. The unrelated app/header width changes
  were removed from this change rather than committed together.

## Verification

Recorder and room tests verify capped retention, baseline replay equivalence, reset
and rewind segments, human/bot attribution, and unchanged reliable state/replay logs.
HTTP tests cover capture enabled by default, explicit opt-out, participant access,
cooldowns, unknown rooms, and non-cacheable downloads. Browser tests cover
bounded immutable evidence and both complete and disconnected ZIPs. Desktop/mobile
checks exercise the real Capture button, canvas PNG, and lack of gameplay commands.

The full JVM run exposed a live-clock race in the existing speed-rule rewind test:
it waited for exact coordinates after restoring an unpaused snapshot. Its fixture
now restores a paused snapshot, leaving the speed-rule assertion intact.

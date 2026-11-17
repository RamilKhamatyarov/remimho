# Paddle Capture Dossier Specification

## Purpose

Players can capture an evidence bundle of the preceding 15 seconds of a match plus a screenshot without pausing gameplay, so reported paddle pass-throughs can be diagnosed across physics, delivery and rendering. See `openspec/adr/0014-paddle-capture-dossier.md`.

## Requirements

### Requirement: Capture is enabled by default and switchable

`remimho.capture.enabled` SHALL default to `true` in every profile, including production, with no `%dev` override. Setting it explicitly to `false` SHALL disable the recorder, browser buffering, participant tokens and the Capture button through `/client-config`. Disabled rooms SHALL use a no-op recorder, and the frontend SHALL wait for `/client-config` before buffering.

#### Scenario: Production defaults to enabled

- **WHEN** a production process starts without an explicit capture setting
- **THEN** capture recording is available

#### Scenario: Operationally disabled

- **WHEN** an operator sets `remimho.capture.enabled=false`
- **THEN** `/client-config` reports capture disabled and the client neither buffers nor shows the Capture button

### Requirement: Evidence is retained in bounded buffers outside the reducer

When enabled, the system SHALL retain the preceding 15 seconds of room diagnostics and browser evidence in bounded buffers outside the reducer, core state and replay log. Diagnostic retention SHALL use monotonic observation time so reset and rewind cannot erase evidence, while logical elapsed time remains the only clock used by gameplay physics.

#### Scenario: Rewind does not clear the evidence window

- **WHEN** a match is rewound during a capture
- **THEN** the preceding diagnostics remain available in the evidence buffer

### Requirement: Capture downloads one ZIP without changing the match

A Capture button SHALL download one ZIP without pausing or changing the match. The ZIP SHALL include replay segments, authoritative before/after motion and contacts, controller attribution, received and rendered browser positions, sent inputs, settings and the canvas screenshot taken at the click. Browser evidence SHALL never be uploaded automatically.

#### Scenario: Clicking Capture does not pause

- **WHEN** a player clicks Capture during a live rally
- **THEN** the match continues undisturbed while the ZIP is produced

#### Scenario: Server unavailable

- **WHEN** the capture endpoint cannot be reached
- **THEN** the client downloads browser evidence only and marks the dossier partial

### Requirement: Segments preserve reset and restoration boundaries

Reset and snapshot-restoration records SHALL be preserved as boundaries, each replay segment SHALL have an exact starting snapshot, and packaging SHALL use a consistent detached prefix of completed reductions rather than independently sampled live state. Replays SHALL reproduce physics from recorded effective actions but SHALL NOT reconstruct the bot decision process or historical HUD charge.

#### Scenario: Rewind creates a new segment

- **WHEN** a time-travel restoration occurs during capture
- **THEN** the following replay segment starts from the exact restored snapshot instead of default state

### Requirement: Capture access is participant-scoped

The diagnostic endpoint SHALL require a token issued to a currently connected participant in that room, SHALL not create unknown rooms and SHALL disable HTTP caching. The token SHALL be delivered only over that connection's WebSocket, sent in an HTTP header, never recorded in browser evidence and revoked on close. A room-wide ten-second cooldown SHALL apply before decoding and export, including across participants and reconnections.

#### Scenario: Unknown room is rejected

- **WHEN** a capture request names a room that does not exist
- **THEN** the request fails and no room is created

#### Scenario: Cooldown across participants

- **WHEN** one participant exports a dossier and another requests one within ten seconds
- **THEN** the second request is refused by the room-wide cooldown

### Requirement: Buffers are bounded and failures are isolated

Server and client buffers SHALL enforce count and byte caps. Retention SHALL store encoded byte arrays as one baseline per segment plus one after-state per entry, promoting the predecessor on eviction, skipping oversized entries and starting a gap segment for the next retained entry. Truncation SHALL be reported rather than promising a full 15 seconds. A diagnostic exception SHALL disable further diagnostics in that room without stopping its actor.

#### Scenario: Overload reports truncation

- **WHEN** the evidence window exceeds its byte or count cap
- **THEN** the dossier reports truncation rather than claiming a complete 15 seconds

#### Scenario: Diagnostic failure does not stop the room

- **WHEN** the recorder or diagnostics throw during a tick
- **THEN** further diagnostics in that room are disabled and subsequent intents still reduce normally

### Requirement: Capture payloads use explicit schema-2 DTOs

Schema 2 SHALL use explicit payload DTOs and boundary values with mapping outside the API model, and replay boundaries SHALL be identified with `BoundaryKind` rather than JVM class names.

#### Scenario: Boundary decoding is stable across builds

- **WHEN** a dossier recorded on one build is decoded on another
- **THEN** boundaries resolve through `BoundaryKind` values rather than class names

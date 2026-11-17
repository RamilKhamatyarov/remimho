# Passing combination presets

Provide five arrangements of short deflectors for player-aimed, live passing
combinations. Lines belong to the activating paddle side and mirror toward the
opponent. Applying or disabling a layout atomically pauses the match and replaces
only that side's previous preset lines; freehand and opponent lines remain.
The player resumes explicitly, and normal physics and existing combo rules apply.

Record resolved geometry, stable line IDs, ownership, and the combination ID in a
reliable action and in replay/history snapshots. Playback must not consult the
preset catalog or browser storage. Layout changes are unavailable during history
preview. No trajectory guidance, highlight, or browser storage belongs in physics.

Custom combinations capture the player's current lines, including preset lines,
in a canonical attacking direction. Store them locally with versioned JSON
import/export. Validate finite, bounded coordinates and sizes at both boundaries;
the server assigns ownership and line IDs. Saved arrangements do not include
teleport links or opponent lines. Avoid adding duplicate freehand geometry.

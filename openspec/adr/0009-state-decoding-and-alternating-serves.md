# State decoding and alternating serves

ADR 0008 did not resolve the reported missed saves. Investigation also found a
browser protocol defect: skipping an unknown length-delimited protobuf field
used the reader offset from before reading its length. Touch-ledger and combo
fields can therefore corrupt decoding and leave the rendered state stale. Fix
the decoder and cover opening rallies through the room actor instead of assuming
every rendered overlap proves a server collision failure. Retain the existing
paddle geometry unless a failing physics regression demonstrates another defect.

Regression tests reproduce both decoding exceptions and false decoded scores.
Stationary and moving opening-save tests pass on both sides with the current
server geometry, as does an opening rally through the room actor with its bot.
The demonstrated defect is packet decoding; these tests do not establish that
all possible high-speed or wall-contact collision cases are correct.

Serves alternate right/down and left/up after both a goal and an explicit restart,
as agreed with the user. Store the last serve side in immutable state, independent
of the puck's current velocity and the number of points awarded. Serialize it in
replay snapshots and history snapshots so restoring a rally also restores the
next serve. There is no wall-clock input, random choice, or presentation event
that determines the direction. A new room still starts right/down.

The additive snapshot fields default to side B for older files. They preserve
readability, not historical physics equivalence: older recordings containing
goals or restarts will use the new serve rule when reduced by this version.
Full historical simulation versioning is outside this fix.

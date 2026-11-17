# Deterministic Redirect And Spin Mechanics

Status: accepted

Redirect and spin mechanics are valid for this Pong-like whiteboard game, but any mechanic that changes puck velocity, trajectory, collision outcome, or scoring threat must be reliable MVI state and replayed through deterministic actions. Visual-only explanations such as trails, tint, glow, or swirl indicators may be ephemeral, because they explain reliable physics state without changing replay outcomes.

We will stage the design: first add readable redirection through paddle contact position, capped paddle movement influence, and static drawn-line reflection; later add short-lived arcade spin caused by player paddle movement, with future power-ups allowed to enhance it. This avoids realistic Magnus-style complexity while preserving skill expression, readability, deterministic replay, and time-travel compatibility.

Consequences: reducers must not depend on randomness or wall-clock time for these mechanics, committed drawn obstacles remain static in the first version, and safe angle limits plus minimum horizontal threat should prevent vertical loops, wall-hugging, and unreadable rallies.

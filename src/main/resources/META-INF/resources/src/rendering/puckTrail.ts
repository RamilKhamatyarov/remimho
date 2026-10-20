/**
 * Speed-scaled motion trail behind the puck.
 *
 * At rally speeds the puck moves ~50 px per frame and reads as teleporting. The trail is derived
 * from the puck's current velocity only, so it needs no position history and cannot smear across
 * a serve reset or a time-travel jump. Coordinates are in game space; the canvas applies scale.
 */
export interface TrailPuck {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

export interface PuckTrail {
  tailX: number;
  tailY: number;
  headX: number;
  headY: number;
  width: number;
  /** 0..1: how strongly to draw the trail. */
  intensity: number;
}

/** Below this raw speed (px/s) the puck is easy to follow and draws no trail. */
export const TRAIL_START_SPEED = 700;
/** At or above this raw speed the trail reaches full length and intensity. */
export const TRAIL_FULL_SPEED = 3200;
/** Full-strength trail length, in puck radii. */
export const MAX_TRAIL_RADII = 12;

export function puckTrail(puck: TrailPuck): PuckTrail | null {
  const speed = Math.hypot(puck.vx, puck.vy);
  if (!Number.isFinite(speed) || !Number.isFinite(puck.radius) || puck.radius <= 0) return null;

  const intensity = Math.min(1, (speed - TRAIL_START_SPEED) / (TRAIL_FULL_SPEED - TRAIL_START_SPEED));
  if (intensity <= 0) return null;

  const length = intensity * MAX_TRAIL_RADII * puck.radius;
  return {
    tailX: puck.x - (puck.vx / speed) * length,
    tailY: puck.y - (puck.vy / speed) * length,
    headX: puck.x,
    headY: puck.y,
    width: puck.radius * 2,
    intensity,
  };
}

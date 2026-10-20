import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/rendering/puckTrail.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
new Function('exports', compiled.outputText)(exports);
const { puckTrail, TRAIL_START_SPEED, TRAIL_FULL_SPEED, MAX_TRAIL_RADII } = exports;

const puck = (vx, vy, extra = {}) => ({ x: 400, y: 300, vx, vy, radius: 10, ...extra });

test('a puck slow enough to follow draws no trail', () => {
  assert.equal(puckTrail(puck(TRAIL_START_SPEED, 0)), null);
  assert.equal(puckTrail(puck(300, 200)), null);
  assert.equal(puckTrail(puck(0, 0)), null);
});

test('the trail points opposite the direction of travel', () => {
  const right = puckTrail(puck(2000, 0));
  assert.ok(right.tailX < right.headX, 'moving right, the trail extends to the left');
  assert.equal(right.tailY, right.headY);

  const up = puckTrail(puck(0, -2000));
  assert.ok(up.tailY > up.headY, 'moving up, the trail extends downward');
});

test('the trail starts at the puck centre', () => {
  const trail = puckTrail(puck(2500, 900));
  assert.equal(trail.headX, 400);
  assert.equal(trail.headY, 300);
  assert.equal(trail.width, 20);
});

test('length and intensity grow with speed and saturate at full speed', () => {
  const length = (t) => Math.hypot(t.headX - t.tailX, t.headY - t.tailY);
  const slow = puckTrail(puck(1200, 0));
  const fast = puckTrail(puck(2400, 0));
  const full = puckTrail(puck(TRAIL_FULL_SPEED, 0));
  const beyond = puckTrail(puck(TRAIL_FULL_SPEED * 2, 0));

  assert.ok(length(fast) > length(slow));
  assert.ok(fast.intensity > slow.intensity);
  assert.equal(full.intensity, 1);
  assert.equal(length(full), MAX_TRAIL_RADII * 10);
  assert.deepEqual(beyond, full, 'past full speed the trail stops growing');
});

test('diagonal speed counts, not just one axis', () => {
  const diagonal = puckTrail(puck(1400, 1400));
  assert.ok(diagonal, 'hypot(1400, 1400) is about 1980 px/s, above the start threshold');
});

test('malformed pucks never produce a trail', () => {
  assert.equal(puckTrail(puck(Number.NaN, 0)), null);
  assert.equal(puckTrail(puck(Infinity, 0)), null);
  assert.equal(puckTrail(puck(2000, 0, { radius: 0 })), null);
  assert.equal(puckTrail(puck(2000, 0, { radius: Number.NaN })), null);
});

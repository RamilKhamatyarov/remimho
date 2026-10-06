import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/combinations.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
new Function('exports', compiled.outputText)(exports);
const { DEFAULT_COMBINATIONS, parseCombination, captureCombination, readSavedCombinations, saveCombinations } = exports;

test('five distinct short-line presets have bounded valid geometry', () => {
  assert.equal(DEFAULT_COMBINATIONS.length, 5);
  assert.equal(new Set(DEFAULT_COMBINATIONS.map(item => item.id)).size, 5);
  for (const layout of DEFAULT_COMBINATIONS) {
    assert.deepEqual(parseCombination(layout), layout);
    assert.ok(layout.lines.length >= 3);
    for (const { points } of layout.lines) {
      assert.equal(points.length, 2);
      assert.ok(Math.hypot((points[1].x - points[0].x) * 800, (points[1].y - points[0].y) * 600) < 81);
    }
  }
});

test('capture includes own freehand and preset lines and mirrors side B toward the opponent', () => {
  const base = { controlPoints: [{ x: 160, y: 120 }, { x: 240, y: 180 }], flattenedPoints: null, width: 5 };
  const layout = captureCombination([
    { ...base, id: 'manual', ownerSide: 'B' },
    { ...base, id: 'preset', ownerSide: 'B', combinationId: 'triangle' },
    { ...base, id: 'opponent', ownerSide: 'A' },
    { ...base, id: 'legacy' },
  ], 'B', 800, 600, 'Saved play', 'custom-test');
  assert.equal(layout.lines.length, 2);
  assert.deepEqual(layout.lines[0].points, [{ x: .8, y: .2 }, { x: .7, y: .3 }]);
  assert.equal('ownerSide' in layout.lines[0], false);
});

test('local save and JSON export import preserve the normalized document', () => {
  const data = new Map();
  const storage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
  assert.deepEqual(readSavedCombinations(storage), []);
  saveCombinations(storage, [DEFAULT_COMBINATIONS[0]]);
  assert.deepEqual(readSavedCombinations(storage), [DEFAULT_COMBINATIONS[0]]);
  assert.deepEqual(parseCombination(JSON.parse(JSON.stringify(DEFAULT_COMBINATIONS[0]))), DEFAULT_COMBINATIONS[0]);
  assert.throws(() => saveCombinations(storage, Array(21).fill(DEFAULT_COMBINATIONS[0])));
});

test('invalid imports cannot introduce nonfinite coordinates or excessive geometry', () => {
  const base = DEFAULT_COMBINATIONS[0];
  for (const changed of [
    { version: 2 }, { name: '' }, { id: '../x' }, { lines: [] }, { lines: Array(33).fill(base.lines[0]) },
    { lines: [{ width: 5, points: [{ x: NaN, y: .5 }, { x: .6, y: .5 }] }] },
    { lines: [{ width: 5, points: [{ x: -1, y: .5 }, { x: .6, y: .5 }] }] },
    { lines: [{ width: 5, points: [{ x: .5, y: .5 }, { x: .5, y: .5 }] }] },
  ]) assert.throws(() => parseCombination({ ...base, ...changed }));
});

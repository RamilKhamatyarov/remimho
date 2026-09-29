import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/proto/game_state.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
new Function('exports', compiled.outputText)(exports);
const { decode } = exports.GameStateDelta;

function varint(value) {
  const bytes = [];
  do {
    const next = value % 128;
    value = Math.floor(value / 128);
    bytes.push(next | (value > 0 ? 128 : 0));
  } while (value > 0);
  return Buffer.from(bytes);
}

function double(field, value) {
  const payload = Buffer.alloc(8);
  payload.writeDoubleLE(value);
  return Buffer.concat([varint(field * 8 + 1), payload]);
}

function message(field, payload) {
  return Buffer.concat([varint(field * 8 + 2), varint(payload.length), payload]);
}

for (const field of [17, 18, 19]) {
  for (const size of [0, 3, 130]) {
    test(`skips field ${field} of length ${size} without corrupting paddle positions`, () => {
      const packet = Buffer.concat([
        double(1, 770),
        message(field, Buffer.alloc(size)),
        double(5, 440),
        double(6, 500),
        double(2, 546),
      ]);
      assert.deepEqual(decode(packet), { puckX: 770, paddle1Y: 440, paddle2Y: 500, puckY: 546 });
    });
  }
}

test('decodes an opening snapshot followed by a paddle contact snapshot', () => {
  for (const x of [400, 770]) {
    const packet = Buffer.concat([
      double(1, x), double(2, 546), double(5, 440), double(6, 500),
      message(17, Buffer.from([8, 1])),
      message(18, double(1, 500)),
      message(19, Buffer.from([8, 1])),
    ]);
    assert.deepEqual(decode(packet), { puckX: x, puckY: 546, paddle1Y: 440, paddle2Y: 500 });
  }
});

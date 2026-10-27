import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
import ts from 'typescript';
import { unzipSync, strFromU8 } from 'fflate';

const require = createRequire(import.meta.url);
function load(name, dependencies = {}) {
  const source = readFileSync(new URL(`../src/diagnostics/${name}.ts`, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const exports = {};
  new Function('exports', 'require', compiled.outputText)(exports, (id) => dependencies[id] ?? require(id));
  return exports;
}
const bufferModule = load('captureBuffer');
const { CaptureBuffer, captureBuffer } = bufferModule;
const { captureDossier } = load('captureDossier', { './captureBuffer': bufferModule });

test('buffer keeps immutable evidence across reset and expires by observation time', () => {
  let now = 0;
  const buffer = new CaptureBuffer(() => now);
  buffer.configure(true);
  const puck = { x: 15 };
  buffer.record('rendered', puck);
  puck.x = 400;
  buffer.record('sent-input', { type: 'RESET' });
  assert.equal(buffer.snapshot().entries[0].data.x, 15);
  now = 15000;
  assert.equal(buffer.snapshot().entries.length, 2);
  now++;
  assert.equal(buffer.snapshot().entries.length, 0);
});

test('count and byte limits report truncation and room switch clears data', () => {
  const buffer = new CaptureBuffer(() => 0, 2, 200);
  buffer.configure(true);
  for (let i = 0; i < 3; i++) buffer.record('input', i);
  assert.equal(buffer.snapshot().entries.length, 2);
  assert.equal(buffer.snapshot().capacityLimited, true);
  buffer.record('oversized', 'x'.repeat(1000));
  assert.equal(buffer.snapshot().entries.length, 2);
  buffer.clear();
  assert.deepEqual(buffer.snapshot(), { atMs: 0, capacityLimited: false, entries: [] });
});

test('disconnected download remains a valid partial ZIP with browser and screenshot', async () => {
  captureBuffer.clear();
  captureBuffer.configure(true);
  captureBuffer.record('rendered', { puck: { x: 15, y: 256 }, paddle1Y: 250 });
  const { blob, partial } = await captureDossier('test', 'B', Promise.resolve(new Blob(['png'])), {}, null, async () => {
    throw new Error('offline');
  });
  const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
  assert.equal(partial, true);
  assert.equal(strFromU8(files['canvas.png']), 'png');
  assert.equal(JSON.parse(strFromU8(files['manifest.json'])).serverStatus, 'unavailable');
  assert.equal(JSON.parse(strFromU8(files['browser.json'])).entries[0].data.puck.x, 15);
});

test('complete dossier keeps replay segments and snapshot bytes without uploading browser data', async () => {
  let request;
  const fetcher = async (url, options) => {
    request = { url, options };
    return { ok: true, json: async () => ({ schemaVersion: 2, capacityLimited: false, segments: [{
      firstSequence: 1, lastSequence: 3, reason: 'RestoreSnapshot',
      replayBase64: btoa('replay'), expectedSnapshotBase64: btoa('snapshot'),
    }] }) };
  };
  const { blob, partial } = await captureDossier('room/one', 'A', Promise.resolve(new Blob(['png'])), {}, 'secret', fetcher);
  const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
  assert.equal(partial, false);
  assert.equal(request.url, '/api/v1/capture/room%2Fone');
  assert.equal(request.options.body, undefined);
  assert.equal(request.options.headers['X-Capture-Token'], 'secret');
  assert.equal(Object.values(files).some(bytes => strFromU8(bytes).includes('secret')), false);
  assert.equal(strFromU8(files['segment-1.replay']), 'replay');
  assert.equal(strFromU8(files['segment-1.expected.snapshot']), 'snapshot');
  assert.equal(JSON.parse(strFromU8(files['server.json'])).segments[0].reason, 'RestoreSnapshot');
});

test('disabled recorder does not inspect or clone inputs', () => {
  const buffer = new CaptureBuffer();
  buffer.record('rendered', { get puck() { throw new Error('Must not read'); } });
  assert.equal(buffer.snapshot().entries.length, 0);
});

test('entry overhead exceeding the byte limit preserves existing evidence', () => {
  const buffer = new CaptureBuffer(() => 0, 10, 100);
  buffer.configure(true);
  buffer.record('input', 1);
  buffer.record('x'.repeat(30), 2);
  const snapshot = buffer.snapshot();
  assert.equal(snapshot.capacityLimited, true);
  assert.deepEqual(snapshot.entries.map(entry => entry.data), [1]);
});

test('missing participant token skips the server request', async () => {
  let requests = 0;
  const { partial } = await captureDossier('room', 'A', Promise.resolve(null), {}, null, async () => {
    requests++;
  });
  assert.equal(requests, 0);
  assert.equal(partial, true);
});

test('unavailable or invalid server evidence still produces a browser dossier', async (t) => {
  const cases = {
    forbidden: async () => ({ ok: false }),
    unknownSchema: async () => ({ ok: true, json: async () => ({ schemaVersion: 1, segments: [] }) }),
    invalidSegments: async () => ({ ok: true, json: async () => ({ schemaVersion: 2, segments: null }) }),
    nullBody: async () => ({ ok: true, json: async () => null }),
    invalidJson: async () => ({ ok: true, json: async () => { throw new SyntaxError('Invalid JSON'); } }),
    offline: async () => { throw new Error('Offline'); },
  };
  for (const [name, fetcher] of Object.entries(cases)) {
    await t.test(name, async () => {
      const { blob, partial } = await captureDossier('room', 'A', Promise.resolve(null), {}, 'token', fetcher);
      const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
      assert.equal(partial, true);
      assert.ok(files['browser.json']);
      assert.equal(files['server.json'], undefined);
      assert.equal(JSON.parse(strFromU8(files['manifest.json'])).serverStatus, 'unavailable');
    });
  }
});

test('144 Hz rendering plus network and inputs retains a full bounded window', () => {
  let now = 0;
  const buffer = new CaptureBuffer(() => now);
  buffer.configure(true);
  for (let index = 0; index < 2160; index++) {
    now = index * 1000 / 144;
    buffer.record('rendered', { x: index, y: 300, paddle1Y: 250, paddle2Y: 250 });
    if (index % 2 === 0) buffer.record('received', { puckX: index });
    buffer.record('sent-input', { type: 'MOVE_PADDLE', data: { y: index } });
  }
  const snapshot = buffer.snapshot();
  assert.equal(snapshot.capacityLimited, false);
  assert.equal(snapshot.entries[0].atMs, 0);
  assert.ok(snapshot.entries.filter(entry => entry.kind === 'rendered').length <= 900);
  buffer.configure(false);
  assert.equal(buffer.snapshot().entries.length, 0);
});

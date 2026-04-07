// src/composables/useGameSocket.ts
import { ref } from 'vue';
import type { Ref } from 'vue';
import type { GameState } from '../types/game';
import { GameStateDelta as _protoStatic } from '../proto/game_state';

export const gameStateRef: Ref<GameState | null> = ref<GameState | null>(null);
export const connectedRef: Ref<boolean> = ref(false);

// ── Feature flag — set false once JSON fallback is no longer needed ───────────
const USE_JSON_FALLBACK = true;

let _ws: WebSocket | null = null;
let _reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let _initialized = false;

// ── Protobuf decoder — synchronous static import ─────────────────────────────
// Uses the hand-written decoder in src/proto/game_state.ts.
// No protobufjs npm package required, no async loading gap.
// (import is at the top of the file — ES modules require top-level imports)
const _proto = _protoStatic;

// ── Delta application ─────────────────────────────────────────────────────────
// proto3 optional fields: only update local state when field is present in delta.
// protobuf.js leaves absent optional scalars as `undefined` on the decoded object.

function applyDelta(delta: Record<string, unknown>): void {
  const prev = gameStateRef.value ?? makeInitialState();
  const next: GameState = { ...prev };

  if (delta['puckX'] !== undefined) next.puck = { ...next.puck, x: delta['puckX'] as number };
  if (delta['puckY'] !== undefined) next.puck = { ...next.puck, y: delta['puckY'] as number };
  if (delta['puckVx'] !== undefined) next.puck = { ...next.puck, vx: delta['puckVx'] as number };
  if (delta['puckVy'] !== undefined) next.puck = { ...next.puck, vy: delta['puckVy'] as number };
  if (delta['paddle1Y'] !== undefined) next.paddle1Y = delta['paddle1Y'] as number;
  if (delta['paddle2Y'] !== undefined) next.paddle2Y = delta['paddle2Y'] as number;
  if (delta['scoreA'] !== undefined) next.score = { ...next.score, playerA: delta['scoreA'] as number };
  if (delta['scoreB'] !== undefined) next.score = { ...next.score, playerB: delta['scoreB'] as number };
  if (delta['paused'] !== undefined) next.paused = delta['paused'] as boolean;

  // Repeated fields — replace whole array when present in delta
  if (Array.isArray(delta['lines'])) next.lines = delta['lines'] as GameState['lines'];
  if (Array.isArray(delta['powerUps'])) next.powerUps = delta['powerUps'] as GameState['powerUps'];
  if (Array.isArray(delta['activePowerUps'])) {
    next.activePowerUpEffects = (delta['activePowerUps'] as Record<string, unknown>[]).map((e) => ({
      type: String(e['type'] ?? ''),
      emoji: String(e['emoji'] ?? ''),
      remainingMs: Number(e['remainingSeconds'] ?? 0) * 1000,
    }));
  }

  gameStateRef.value = next;
}

function makeInitialState(): GameState {
  return {
    puck: { x: 400, y: 300, vx: 0, vy: 0, radius: 10 },
    score: { playerA: 0, playerB: 0 },
    canvasWidth: 800,
    canvasHeight: 600,
    paddleHeight: 100,
    paddle1Y: 250,
    paddle2Y: 250,
    paused: false,
    lines: [],
    powerUps: [],
    activePowerUpEffects: [],
  };
}

// ── Socket init ───────────────────────────────────────────────────────────────

function initSocket(): void {
  if (_initialized) return;
  _initialized = true;

  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const wsUrl = `${proto}://${window.location.host}/game`;

  function connect(): void {
    if (_reconnectTimer) {
      clearTimeout(_reconnectTimer);
      _reconnectTimer = null;
    }

    _ws = new WebSocket(wsUrl);
    _ws.binaryType = 'arraybuffer'; // receive Protobuf frames as ArrayBuffer

    _ws.onopen = () => {
      connectedRef.value = true;
      console.log('[WS] Connected to', wsUrl);
    };

    _ws.onmessage = (event: MessageEvent) => {
      // ── Binary path (Protobuf) ──────────────────────────────────────────
      if (event.data instanceof ArrayBuffer) {
        try {
          applyDelta(_proto.decode(new Uint8Array(event.data)));
        } catch (e) {
          console.error('[WS] Protobuf decode error', e);
        }
        return;
      }

      // ── JSON fallback ───────────────────────────────────────────────────
      if (!USE_JSON_FALLBACK) return;
      try {
        const data = JSON.parse(event.data as string) as Record<string, unknown>;
        if (data['type'] === 'ERROR') {
          console.warn('[WS] Server error:', data['message']);
          return;
        }
        if (data && 'puck' in data && 'score' in data) {
          // Nested format: { puck: {x,y,...}, score: {playerA,playerB}, ... }
          gameStateRef.value = { ...data } as unknown as GameState;
        } else if (data && ('puckX' in data || 'paddle1Y' in data)) {
          // Flat format: { puckX, puckY, puckVX, puckVY, paddle1Y, paddle2Y, scoreA, scoreB, ... }
          const prev = gameStateRef.value ?? makeInitialState();
          gameStateRef.value = {
            ...prev,
            puck: {
              x:      (data['puckX']  as number) ?? prev.puck.x,
              y:      (data['puckY']  as number) ?? prev.puck.y,
              vx:     (data['puckVX'] as number) ?? prev.puck.vx,
              vy:     (data['puckVY'] as number) ?? prev.puck.vy,
              radius: prev.puck.radius,
            },
            score: {
              playerA: (data['scoreA'] as number) ?? prev.score.playerA,
              playerB: (data['scoreB'] as number) ?? prev.score.playerB,
            },
            paddle1Y:    (data['paddle1Y']    as number)  ?? prev.paddle1Y,
            paddle2Y:    (data['paddle2Y']    as number)  ?? prev.paddle2Y,
            paddleHeight:(data['paddleHeight'] as number) ?? prev.paddleHeight,
            canvasWidth: (data['canvasWidth']  as number) ?? prev.canvasWidth,
            canvasHeight:(data['canvasHeight'] as number) ?? prev.canvasHeight,
            paused:      (data['paused']       as boolean)?? prev.paused,
            lines:       (data['lines']        as GameState['lines'])       ?? prev.lines,
            powerUps:    (data['powerUps']     as GameState['powerUps'])    ?? prev.powerUps,
            activePowerUpEffects: (data['activePowerUps'] as GameState['activePowerUpEffects']) ?? prev.activePowerUpEffects,
          };
        }
      } catch (e) {
        console.error('[WS] JSON parse error', e);
      }
    };

    _ws.onclose = () => {
      connectedRef.value = false;
      _reconnectTimer = setTimeout(connect, 1000);
    };

    _ws.onerror = (e) => {
      console.error('[WS] Error', e);
    };
  }

  connect();
}

// ── Composable ────────────────────────────────────────────────────────────────

export function useGameSocket() {
  initSocket();

  function send(type: string, data: Record<string, unknown> = {}): void {
    if (_ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type, data }));
    }
  }

  const movePaddle = (y: number): void => send('MOVE_PADDLE', { y });
  const togglePause = (): void => send('TOGGLE_PAUSE');
  const reset = (): void => send('RESET');
  const clearLines = (): void => {
    // Optimistic local update: Protobuf delta cannot distinguish an empty
    // repeated field from a missing field, so the server's "lines: []" delta
    // is indistinguishable from a delta with no lines field at all.
    // Clear locally immediately so the canvas updates without waiting.
    if (gameStateRef.value) {
      gameStateRef.value = { ...gameStateRef.value, lines: [], powerUps: [] };
    }
    send('CLEAR_LINES');
  };

  return { gameState: gameStateRef, connected: connectedRef, movePaddle, togglePause, reset, clearLines, send };
}

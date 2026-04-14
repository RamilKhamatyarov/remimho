import { ref } from 'vue';
import type { Ref } from 'vue';
import type { GameState } from '../types/game';
import { GameStateDelta as _protoStatic } from '../proto/game_state';

export const gameStateRef: Ref<GameState | null> = ref<GameState | null>(null);
export const connectedRef: Ref<boolean> = ref(false);

const USE_JSON_FALLBACK = true;

let _ws: WebSocket | null = null;
let _reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let _initialized = false;

const _proto = _protoStatic;

function applyDelta(delta: Record<string, unknown>): void {
  const prev = gameStateRef.value ?? makeInitialState();
  const next: GameState = { ...prev };

  if (delta['puckX']    !== undefined) next.puck  = { ...next.puck,  x:  delta['puckX']  as number };
  if (delta['puckY']    !== undefined) next.puck  = { ...next.puck,  y:  delta['puckY']  as number };
  if (delta['puckVx']   !== undefined) next.puck  = { ...next.puck,  vx: delta['puckVx'] as number };
  if (delta['puckVy']   !== undefined) next.puck  = { ...next.puck,  vy: delta['puckVy'] as number };
  if (delta['paddle1Y'] !== undefined) next.paddle1Y = delta['paddle1Y'] as number;
  if (delta['paddle2Y'] !== undefined) next.paddle2Y = delta['paddle2Y'] as number;
  if (delta['scoreA']   !== undefined) next.score = { ...next.score, playerA: delta['scoreA'] as number };
  if (delta['scoreB']   !== undefined) next.score = { ...next.score, playerB: delta['scoreB'] as number };
  if (delta['paused']   !== undefined) next.paused = delta['paused'] as boolean;

  if (Array.isArray(delta['lines']))        next.lines   = delta['lines']   as GameState['lines'];
  if (Array.isArray(delta['powerUps']))     next.powerUps = delta['powerUps'] as GameState['powerUps'];
  if (Array.isArray(delta['activePowerUps'])) {
    next.activePowerUpEffects = (delta['activePowerUps'] as Record<string, unknown>[]).map(e => ({
      type:        String(e['type']  ?? ''),
      emoji:       String(e['emoji'] ?? ''),
      remainingMs: Number(e['remainingSeconds'] ?? 0) * 1000,
    }));
  }

  gameStateRef.value = next;
}

function makeInitialState(): GameState {
  return {
    puck:   { x: 400, y: 300, vx: 0, vy: 0, radius: 10 },
    score:  { playerA: 0, playerB: 0 },
    canvasWidth: 800, canvasHeight: 600,
    paddleHeight: 100, paddle1Y: 250, paddle2Y: 250,
    paused: false,
    lines: [], powerUps: [], activePowerUpEffects: [],
  };
}


function initSocket(): void {
  if (_initialized) return;
  _initialized = true;

  const proto  = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const wsUrl  = `${proto}://${window.location.host}/game`;

  function connect(): void {
    if (_reconnectTimer) { clearTimeout(_reconnectTimer); _reconnectTimer = null; }

    _ws = new WebSocket(wsUrl);
    _ws.binaryType = 'arraybuffer';

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
          gameStateRef.value = { ...data } as unknown as GameState;
        } else if (data && ('puckX' in data || 'paddle1Y' in data)) {
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
            paddle1Y:    (data['paddle1Y']     as number)  ?? prev.paddle1Y,
            paddle2Y:    (data['paddle2Y']     as number)  ?? prev.paddle2Y,
            paddleHeight:(data['paddleHeight'] as number)  ?? prev.paddleHeight,
            canvasWidth: (data['canvasWidth']  as number)  ?? prev.canvasWidth,
            canvasHeight:(data['canvasHeight'] as number)  ?? prev.canvasHeight,
            paused:      (data['paused']       as boolean) ?? prev.paused,
            lines:       (data['lines']        as GameState['lines'])       ?? prev.lines,
            powerUps:    (data['powerUps']     as GameState['powerUps'])    ?? prev.powerUps,
            activePowerUpEffects: (data['activePowerUps'] as GameState['activePowerUpEffects']) ?? prev.activePowerUpEffects,
          };
        }
      } catch (e) {
        console.error('[WS] JSON parse error', e);
      }
    };

    _ws.onclose  = () => { connectedRef.value = false; _reconnectTimer = setTimeout(connect, 1000); };
    _ws.onerror  = (e) => { console.error('[WS] Error', e); };
  }

  connect();
}

// ── Throttle helper for slider scrubbing ─────────────────────────────────────

function makeThrottle<T extends (...args: Parameters<T>) => void>(fn: T, ms: number): T {
  let last = 0;
  return ((...args: Parameters<T>) => {
    const now = Date.now();
    if (now - last >= ms) { last = now; fn(...args); }
  }) as T;
}


export function useGameSocket() {
  initSocket();

  function send(type: string, data: Record<string, unknown> = {}): void {
    if (_ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type, data }));
    }
  }

  const movePaddle  = (y: number): void  => send('MOVE_PADDLE',  { y });
  const togglePause = ():    void         => send('TOGGLE_PAUSE');
  const reset       = ():    void         => send('RESET');
  const clearLines  = ():    void => {
    if (gameStateRef.value) {
      gameStateRef.value = { ...gameStateRef.value, lines: [], powerUps: [] };
    }
    send('CLEAR_LINES');
  };

  /**
   * Request a historical snapshot at `offsetSeconds` seconds in the past.
   * Throttled to one message per 80 ms so rapid slider drags don't flood the server.
   */
  const timeshift = makeThrottle((offsetSeconds: number): void => {
    send('TIMESHIFT', { offset: offsetSeconds });
  }, 80);

  /** Exit timeshift mode and rejoin the live broadcast. */
  const resume = (): void => send('RESUME');

  return {
    gameState: gameStateRef,
    connected: connectedRef,
    movePaddle, togglePause, reset, clearLines,
    timeshift, resume, send,
  };
}

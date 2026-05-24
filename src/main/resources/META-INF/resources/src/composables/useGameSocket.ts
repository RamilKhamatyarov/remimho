import { ref } from 'vue'
import type { Ref } from 'vue'
import type { GameState } from '../types/game'
import { GameStateDelta as protoGameStateDelta } from '../proto/game_state'
import { configureGameLoop, enqueueDelta } from './useGameLoop'

export const gameStateRef: Ref<GameState | null> = ref<GameState | null>(null)
export const connectedRef: Ref<boolean> = ref(false)

const JSON_FALLBACK_ENABLED = true
const DEFAULT_ROOM_ID = 'default'
const RECONNECT_DELAY_MS = 1000
const TIMESHIFT_THROTTLE_MS = 40

let socket: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let initialized = false
let currentRoomId = roomIdFromLocation()

function roomIdFromLocation(): string {
  const url = new URL(window.location.href)
  return url.searchParams.get('roomId') ?? window.location.pathname.split('/').filter(Boolean)[0] ?? DEFAULT_ROOM_ID
}

function mergeDelta(delta: Record<string, unknown>, base?: GameState | null): GameState {
  const previous = delta['fullState'] === true ? makeInitialState() : (base ?? gameStateRef.value ?? makeInitialState())
  const next: GameState = { ...previous }

  applyPuckDelta(next, delta)
  applyScoreDelta(next, delta)
  applyFieldDelta(next, delta)
  applyCollectionDelta(next, delta)

  return next
}

function applyPuckDelta(state: GameState, delta: Record<string, unknown>) {
  if (delta['puckX'] !== undefined) state.puck = { ...state.puck, x: delta['puckX'] as number }
  if (delta['puckY'] !== undefined) state.puck = { ...state.puck, y: delta['puckY'] as number }
  if (delta['puckVx'] !== undefined) state.puck = { ...state.puck, vx: delta['puckVx'] as number }
  if (delta['puckVy'] !== undefined) state.puck = { ...state.puck, vy: delta['puckVy'] as number }
}

function applyScoreDelta(state: GameState, delta: Record<string, unknown>) {
  if (delta['scoreA'] !== undefined) state.score = { ...state.score, playerA: delta['scoreA'] as number }
  if (delta['scoreB'] !== undefined) state.score = { ...state.score, playerB: delta['scoreB'] as number }
}

function applyFieldDelta(state: GameState, delta: Record<string, unknown>) {
  if (delta['paddle1Y'] !== undefined) state.paddle1Y = delta['paddle1Y'] as number
  if (delta['paddle2Y'] !== undefined) state.paddle2Y = delta['paddle2Y'] as number
  if (delta['paused'] !== undefined) state.paused = delta['paused'] as boolean
}

function applyCollectionDelta(state: GameState, delta: Record<string, unknown>) {
  if (Array.isArray(delta['lines'])) state.lines = delta['lines'] as GameState['lines']
  if (Array.isArray(delta['powerUps'])) state.powerUps = delta['powerUps'] as GameState['powerUps']
  if (Array.isArray(delta['activePowerUps'])) {
    state.activePowerUpEffects = (delta['activePowerUps'] as Record<string, unknown>[]).map(toActivePowerUpEffect)
  }
}

function toActivePowerUpEffect(effect: Record<string, unknown>): GameState['activePowerUpEffects'][number] {
  return {
    type: String(effect['type'] ?? ''),
    emoji: String(effect['emoji'] ?? ''),
    remainingMs: Number(effect['remainingSeconds'] ?? 0) * 1000,
  }
}

function applyDelta(delta: Record<string, unknown>) {
  gameStateRef.value = mergeDelta(delta)
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
  }
}

function initSocket() {
  if (initialized) return
  initialized = true

  loadClientConfig()
  connect()
}

function connect() {
  clearReconnectTimer()

  const url = socketUrl()
  socket = new WebSocket(url)
  socket.binaryType = 'arraybuffer'
  socket.onopen = () => onSocketOpen(url)
  socket.onmessage = onSocketMessage
  socket.onclose = onSocketClose
  socket.onerror = onSocketError
}

function socketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/game?roomId=${encodeURIComponent(currentRoomId)}`
}

function onSocketOpen(url: string) {
  connectedRef.value = true
  console.log('[WS] Connected to', url)
}

function onSocketMessage(event: MessageEvent) {
  if (event.data instanceof ArrayBuffer) {
    applyBinaryMessage(event.data)
    return
  }

  if (JSON_FALLBACK_ENABLED) applyJsonMessage(event.data as string)
}

function applyBinaryMessage(data: ArrayBuffer) {
  try {
    const delta = protoGameStateDelta.decode(new Uint8Array(data))
    if (!enqueueDelta(delta)) applyDelta(delta)
  } catch (error) {
    console.error('[WS] Protobuf decode error', error)
  }
}

function applyJsonMessage(message: string) {
  try {
    const data = JSON.parse(message) as Record<string, unknown>
    if (isServerError(data)) {
      console.warn('[WS] Server error:', data['message'])
      return
    }
    applyJsonStateMessage(data)
  } catch (error) {
    console.error('[WS] JSON parse error', error)
  }
}

function isServerError(data: Record<string, unknown>): boolean {
  return data['type'] === 'ERROR'
}

function applyJsonStateMessage(data: Record<string, unknown>) {
  if ('puck' in data && 'score' in data) {
    gameStateRef.value = { ...data } as unknown as GameState
    return
  }

  if ('puckX' in data || 'paddle1Y' in data) {
    gameStateRef.value = mergeLegacyJsonDelta(data)
  }
}

function mergeLegacyJsonDelta(data: Record<string, unknown>): GameState {
  const previous = gameStateRef.value ?? makeInitialState()
  return {
    ...previous,
    puck: {
      x: (data['puckX'] as number) ?? previous.puck.x,
      y: (data['puckY'] as number) ?? previous.puck.y,
      vx: (data['puckVX'] as number) ?? previous.puck.vx,
      vy: (data['puckVY'] as number) ?? previous.puck.vy,
      radius: previous.puck.radius,
    },
    score: {
      playerA: (data['scoreA'] as number) ?? previous.score.playerA,
      playerB: (data['scoreB'] as number) ?? previous.score.playerB,
    },
    paddle1Y: (data['paddle1Y'] as number) ?? previous.paddle1Y,
    paddle2Y: (data['paddle2Y'] as number) ?? previous.paddle2Y,
    paddleHeight: (data['paddleHeight'] as number) ?? previous.paddleHeight,
    canvasWidth: (data['canvasWidth'] as number) ?? previous.canvasWidth,
    canvasHeight: (data['canvasHeight'] as number) ?? previous.canvasHeight,
    paused: (data['paused'] as boolean) ?? previous.paused,
    lines: (data['lines'] as GameState['lines']) ?? previous.lines,
    powerUps: (data['powerUps'] as GameState['powerUps']) ?? previous.powerUps,
    activePowerUpEffects: (data['activePowerUps'] as GameState['activePowerUpEffects']) ?? previous.activePowerUpEffects,
  }
}

function onSocketClose() {
  connectedRef.value = false
  reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
}

function onSocketError(error: Event) {
  console.error('[WS] Error', error)
}

async function loadClientConfig(): Promise<void> {
  try {
    const response = await fetch('/api/v1/game/client-config', { headers: { Accept: 'application/json' } })
    if (!response.ok) return

    const config = await response.json() as { clientInterpolation?: boolean }
    configureGameLoop({
      clientInterpolation: config.clientInterpolation === true,
      gameState: gameStateRef,
      mergeDelta,
    })
  } catch (error) {
    console.warn('[WS] Client config unavailable', error)
  }
}

export function setSocketRoom(roomId: string) {
  const nextRoomId = roomId.trim() || DEFAULT_ROOM_ID
  if (currentRoomId === nextRoomId) return

  currentRoomId = nextRoomId
  initialized = false
  clearReconnectTimer()
  closeSocketWithoutReconnect()
  gameStateRef.value = null
  initSocket()
}

function clearReconnectTimer() {
  if (!reconnectTimer) return
  clearTimeout(reconnectTimer)
  reconnectTimer = null
}

function closeSocketWithoutReconnect() {
  if (!socket) return
  socket.onclose = null
  socket.close()
  socket = null
}

function throttle<T extends (...args: Parameters<T>) => void>(fn: T, delayMs: number): T {
  let lastCallAt = 0
  return ((...args: Parameters<T>) => {
    const now = Date.now()
    if (now - lastCallAt < delayMs) return
    lastCallAt = now
    fn(...args)
  }) as T
}

export function useGameSocket() {
  initSocket()

  function send(type: string, data: Record<string, unknown> = {}) {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type, data }))
    }
  }

  const movePaddle = (y: number) => send('MOVE_PADDLE', { y })
  const togglePause = () => send('TOGGLE_PAUSE')
  const reset = () => send('RESET')
  const clearLines = () => {
    if (gameStateRef.value) {
      gameStateRef.value = { ...gameStateRef.value, lines: [], powerUps: [] }
    }
    send('CLEAR_LINES')
  }
  const eraseLine = (lineId: string) => {
    if (!lineId) return
    if (gameStateRef.value) {
      gameStateRef.value = {
        ...gameStateRef.value,
        lines: gameStateRef.value.lines.filter((line) => line.id !== lineId),
      }
    }
    send('ERASE_LINE', { lineId })
  }
  const timeshift = throttle((offsetSeconds: number) => {
    send('TIMESHIFT', { offset: offsetSeconds })
  }, TIMESHIFT_THROTTLE_MS)
  const resume = () => send('RESUME')
  const commitTimeshift = (offsetSeconds: number) => send('COMMIT_TIMESHIFT', { offset: offsetSeconds })

  return {
    gameState: gameStateRef,
    connected: connectedRef,
    movePaddle,
    togglePause,
    reset,
    clearLines,
    eraseLine,
    timeshift,
    resume,
    commitTimeshift,
    send,
  }
}

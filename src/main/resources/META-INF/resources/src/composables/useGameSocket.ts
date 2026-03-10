import { ref, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import type { GameState } from '../types/game'

export const gameStateRef: Ref<GameState | null> = ref<GameState | null>(null)
export const connectedRef: Ref<boolean> = ref(false)

let _ws: WebSocket | null = null
let _reconnectTimer: ReturnType<typeof setTimeout> | null = null
let _initialized = false

function initSocket() {
  if (_initialized) return
  _initialized = true

  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const wsUrl = `${proto}://${window.location.host}/game`

  function connect() {
    if (_reconnectTimer) { clearTimeout(_reconnectTimer); _reconnectTimer = null }

    _ws = new WebSocket(wsUrl)

    _ws.onopen = () => {
      connectedRef.value = true
      console.log('[WS] Connected to', wsUrl)
    }

    _ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string)
        if (data.type === 'ERROR') { console.warn('[WS] Server error:', data.message); return }
        if (data && typeof data === 'object' && 'puck' in data && 'score' in data) {
          gameStateRef.value = { ...data } as GameState
        }
      } catch (e) { console.error('[WS] Parse error', e) }
    }

    _ws.onclose = () => {
      connectedRef.value = false
      _reconnectTimer = setTimeout(connect, 1000)
    }

    _ws.onerror = (e) => { console.error('[WS] Error', e) }
  }

  connect()
}

export function useGameSocket() {
  initSocket()

  function send(type: string, data: Record<string, unknown> = {}) {
    if (_ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type, data }))
    }
  }

  function movePaddle(y: number)  { send('MOVE_PADDLE', { y }) }
  function togglePause()           { send('TOGGLE_PAUSE') }
  function reset()                 { send('RESET') }
  function clearLines()            { send('CLEAR_LINES') }

  return {
    gameState: gameStateRef,
    connected: connectedRef,
    movePaddle,
    togglePause,
    reset,
    clearLines,
    send,
  }
}

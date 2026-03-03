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
    if (_reconnectTimer) {
      clearTimeout(_reconnectTimer)
      _reconnectTimer = null
    }

    console.log('[WS] Connecting to', wsUrl)
    _ws = new WebSocket(wsUrl)

    _ws.onopen = () => {
      connectedRef.value = true
      console.log('[WS] Connected')
    }

    _ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string)

        console.log('[WS] Message received:', data)

        if (data.type === 'ERROR') {
          console.warn('[WS] Server error:', data.message)
          return
        }

        if (
          data &&
          typeof data === 'object' &&
          'puck' in data &&
          'score' in data
        ) {
          gameStateRef.value = { ...data } as GameState
        } else {
          console.warn('[WS] Received message is not a GameState:', data)
        }
      } catch (e) {
        console.error('[WS] Parse error', e)
      }
    }

    _ws.onclose = () => {
      connectedRef.value = false
      console.log('[WS] Closed – reconnecting in 1s')
      _reconnectTimer = setTimeout(connect, 1000)
    }

    _ws.onerror = (e) => {
      console.error('[WS] Error', e)
    }
  }

  connect()
}

export function useGameSocket() {
  initSocket()

  function send(type: string, data: Record<string, unknown> = {}) {
    if (_ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type, data }))
    } else {
      console.warn('[WS] Cannot send, socket not open')
    }
  }

  function movePaddle(y: number) {
    send('MOVE_PADDLE', { y })
  }
  function togglePause() {
    send('TOGGLE_PAUSE')
  }
  function reset() {
    send('RESET')
  }

  return {
    gameState: gameStateRef,
    connected: connectedRef,
    movePaddle,
    togglePause,
    reset,
  }
}

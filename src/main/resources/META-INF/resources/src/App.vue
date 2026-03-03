<template>
  <div class="app">
    <header>
      <h1>Whiteboard Pong</h1>
      <div class="status" :class="{ connected }">
        {{ connected ? '● Connected' : '○ Connecting…' }}
      </div>
    </header>

    <main>
      <GameCanvas
        :width="800"
        :height="600"
        @paddle-move="movePaddle"
      />
    </main>

    <footer>
      <button @click="togglePause">{{ gameState?.paused ? 'Resume' : 'Pause' }}</button>
      <button @click="reset">Reset</button>
      <span v-if="gameState">
        {{ gameState.score.playerA }} – {{ gameState.score.playerB }}
      </span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import GameCanvas from './components/GameCanvas.vue'
import { useGameSocket } from './composables/useGameSocket'

const { gameState, connected, movePaddle, togglePause, reset } = useGameSocket()
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  background: #0d0d1a;
  color: #fff;
  font-family: monospace;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
}

.app {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

header {
  display: flex;
  gap: 16px;
  align-items: center;
}

h1 { font-size: 1.5rem; letter-spacing: 2px; }

.status { font-size: 0.85rem; color: #e94560; }
.status.connected { color: #4ecca3; }

footer {
  display: flex;
  gap: 12px;
  align-items: center;
}

button {
  background: #0f3460;
  color: #fff;
  border: 1px solid #e94560;
  padding: 8px 20px;
  cursor: pointer;
  font-family: monospace;
  font-size: 0.9rem;
  border-radius: 4px;
  transition: background 0.2s;
}

button:hover { background: #e94560; }

canvas { border: 1px solid rgba(255,255,255,0.1); border-radius: 4px; cursor: none; }
</style>

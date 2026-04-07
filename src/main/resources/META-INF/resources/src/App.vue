<template>
  <div class="app">
    <header>
      <h1>Whiteboard Pong</h1>
      <div class="status" :class="{ connected }">
        {{ connected ? '● Connected' : '○ Connecting…' }}
      </div>
    </header>

    <main>
      <!--
        No :width / :height on GameCanvas — the canvas sizes itself from
        state.canvasWidth/Height inside the RAF loop. Passing reactive props
        causes Vue's diff to rewrite canvas attributes on every re-render,
        which clears the bitmap and makes the puck appear frozen.
      -->
      <GameCanvas @paddle-move="movePaddle" />
    </main>

    <footer>
      <button @click="togglePause">{{ gameState?.paused ? 'Resume' : 'Pause' }}</button>
      <button @click="reset">Reset</button>
      <button class="btn-clear" @click="clearLines">Clear Lines</button>
      <button class="btn-workshop" @click="publishLevel">Publish Level</button>
      <span v-if="gameState">
        {{ gameState.score.playerA }} – {{ gameState.score.playerB }}
      </span>
      <span v-if="workshopMsg" class="workshop-msg">{{ workshopMsg }}</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import GameCanvas from './components/GameCanvas.vue';
import { useGameSocket } from './composables/useGameSocket';
import { useWorkshopApi, ContentType } from './api/workshop';

const { gameState, connected, movePaddle, togglePause, reset, clearLines } = useGameSocket();
const { publishContent } = useWorkshopApi();

// ── Workshop publish ──────────────────────────────────────────────────────────

const workshopMsg = ref('');

async function publishLevel(): Promise<void> {
  const result = await publishContent(
    ContentType.LEVEL,
    { lines: gameState.value?.lines ?? [] },
    { name: 'My Barrier Layout', author: 'player' },
  );
  workshopMsg.value = result.error ? `✗ ${result.error}` : '✓ Published!';
  setTimeout(() => { workshopMsg.value = ''; }, 3000);
}
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

.app { display: flex; flex-direction: column; align-items: center; gap: 16px; }

header { display: flex; gap: 16px; align-items: center; }

h1 { font-size: 1.5rem; letter-spacing: 2px; }

.status { font-size: 0.85rem; color: #e94560; }
.status.connected { color: #4ecca3; }

footer { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; justify-content: center; }

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

.btn-clear { border-color: #f0a500; }
.btn-clear:hover { background: #f0a500; color: #000; }

.btn-workshop { border-color: #4ecca3; }
.btn-workshop:hover { background: #4ecca3; color: #000; }

.workshop-msg { font-size: 0.85rem; color: #4ecca3; }

canvas { border: 1px solid rgba(255,255,255,0.1); border-radius: 4px; cursor: none; }
</style>

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
        Move the mouse over the dark canvas to control the right (green) paddle.
        Hold left-click and drag to draw a barrier line.
      -->
      <GameCanvas :timeshift-active="isRewinding" @paddle-move="movePaddleIfLive" />
    </main>

    <!-- ── Timeshift slider ───────────────────────────────────────────────── -->
    <div class="timeshift-bar" :class="{ rewinding: isRewinding }">
      <div class="ts-meta">
        <span class="ts-badge" :class="{ rewinding: isRewinding }">
          {{ sliderStatusLabel }}
        </span>
        <span class="ts-hint">Drag or use [ ] / ← →, Enter branches</span>
      </div>

      <input
        type="range"
        class="ts-slider"
        min="0"
        :max="MAX_HISTORY_S"
        step="0.25"
        :value="sliderOffset"
        :style="sliderStyle"
        @mousedown="onSliderGrab"
        @touchstart.prevent="onSliderGrab"
        @input="onSliderMove"
        @mouseup="onSliderDrop"
        @touchend="onSliderDrop"
        @mouseleave="onSliderLeave"
      />

      <div class="ts-ticks">
        <span>now</span>
        <span>{{ Math.round(MAX_HISTORY_S / 2) }}s ago</span>
        <span>{{ MAX_HISTORY_S }}s ago</span>
      </div>

      <div v-if="isRewinding" class="ts-actions">
        <button class="btn-travel" @click="commitTimeTravel">Change Future</button>
        <button class="btn-live" @click="goLive">Go Live</button>
      </div>
    </div>

    <footer>
      <button @click="togglePause">{{ gameState?.paused ? 'Resume' : 'Pause' }}</button>
      <button @click="reset">Reset</button>
      <button class="btn-clear"    @click="clearLines">Clear Lines</button>
      <button class="btn-workshop" @click="publishLevel">Publish Level</button>
      <span v-if="gameState">{{ gameState.score.playerA }} – {{ gameState.score.playerB }}</span>
      <span class="hotkeys">Space pause · R reset · Esc live</span>
      <span v-if="workshopMsg" class="workshop-msg">{{ workshopMsg }}</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import GameCanvas from './components/GameCanvas.vue';
import { useGameSocket } from './composables/useGameSocket';
import { useWorkshopApi, ContentType } from './api/workshop';

const MAX_HISTORY_S = 15;

const {
  gameState, connected,
  movePaddle, togglePause, reset, clearLines,
  timeshift, resume, commitTimeshift,
} = useGameSocket();
const { publishContent } = useWorkshopApi();

// ── Timeshift state ──────────────────────────────────────────────────────────

const sliderOffset = ref(0);
const isRewinding  = ref(false);

const offsetLabel = computed(() => {
  const s = sliderOffset.value;
  return s === 0 ? 'now' : `${s.toFixed(2)} s ago`;
});

const sliderStatusLabel = computed(() => {
  if (isRewinding.value) return `⏪  ${offsetLabel.value}`;
  return gameState.value?.paused ? '⏸ PAUSED' : '🔴 LIVE';
});

const sliderStyle = computed(() => ({
  '--pct': `${(sliderOffset.value / MAX_HISTORY_S) * 100}%`,
}));

function setSliderOffset(offset: number) {
  const next = Math.max(0, Math.min(MAX_HISTORY_S, offset));
  sliderOffset.value = next;
  if (next === 0) {
    goLive();
    return;
  }
  isRewinding.value = true;
  timeshift(next);
}

function onSliderGrab() {
  isRewinding.value = true;
}

function onSliderMove(e: Event) {
  setSliderOffset(parseFloat((e.target as HTMLInputElement).value));
}

function onSliderDrop() {
  if (sliderOffset.value === 0) { goLive(); return; }
}

function onSliderLeave() {
  if (sliderOffset.value === 0) goLive();
}

function goLive() {
  isRewinding.value  = false;
  sliderOffset.value = 0;
  resume();
}

function commitTimeTravel() {
  const offset = sliderOffset.value;
  if (offset <= 0) { goLive(); return; }
  isRewinding.value = false;
  sliderOffset.value = 0;
  commitTimeshift(offset);
}

function movePaddleIfLive(y: number) {
  if (!isRewinding.value) movePaddle(y);
}

function handleHotkey(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null;
  const tag = target?.tagName?.toLowerCase();
  if (tag === 'input' || tag === 'textarea' || target?.isContentEditable) return;

  switch (e.key) {
    case ' ':
    case 'p':
    case 'P':
      e.preventDefault();
      togglePause();
      break;
    case 'r':
    case 'R':
      e.preventDefault();
      reset();
      goLive();
      break;
    case 'ArrowLeft':
    case '[':
      e.preventDefault();
      setSliderOffset(sliderOffset.value + 0.25);
      break;
    case 'ArrowRight':
    case ']':
      e.preventDefault();
      setSliderOffset(sliderOffset.value - 0.25);
      break;
    case 'Home':
    case 'Escape':
      if (isRewinding.value || sliderOffset.value !== 0) {
        e.preventDefault();
        goLive();
      }
      break;
    case 'Enter':
      if (isRewinding.value) {
        e.preventDefault();
        commitTimeTravel();
      }
      break;
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleHotkey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleHotkey);
  if (isRewinding.value) resume();
});

// ── Workshop publish ─────────────────────────────────────────────────────────

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
/* ── Reset ─────────────────────────────────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  background: #0d0d1a;
  color: #fff;
  font-family: monospace;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 100vh;
  padding: 16px 8px;
}

/* ── App shell ─────────────────────────────────────────────────────────────── */
.app {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  width: 100%;
  max-width: 820px;
}

header { display: flex; gap: 16px; align-items: center; }
h1 { font-size: 1.5rem; letter-spacing: 2px; }

.status { font-size: 0.85rem; color: #e94560; }
.status.connected { color: #4ecca3; }

canvas {
  display: block;
  max-width: 100%;        /* ← prevents viewport overflow */
  height: auto;           /* ← maintains aspect ratio when width shrinks */
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 4px;
  cursor: none;
  touch-action: none;     /* ← prevents scroll on touch devices */
}

/* ── Buttons ───────────────────────────────────────────────────────────────── */
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

.btn-clear        { border-color: #f0a500; }
.btn-clear:hover  { background: #f0a500; color: #000; }

.btn-workshop       { border-color: #4ecca3; }
.btn-workshop:hover { background: #4ecca3; color: #000; }

footer {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
}

.workshop-msg { font-size: 0.85rem; color: #4ecca3; }
.hotkeys { font-size: 0.72rem; color: rgba(255,255,255,0.38); }

/* ── Timeshift bar ─────────────────────────────────────────────────────────── */
.timeshift-bar {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 14px 8px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
  border-radius: 8px;
  transition: border-color 0.25s, background 0.25s;
}
.timeshift-bar.rewinding {
  background: rgba(233, 69, 96, 0.07);
  border-color: rgba(233, 69, 96, 0.55);
}

.ts-meta { display: flex; justify-content: space-between; align-items: center; }

.ts-badge {
  font-size: 0.78rem;
  font-weight: 700;
  color: #4ecca3;
  letter-spacing: 0.04em;
  transition: color 0.2s;
  min-width: 90px;
}
.ts-badge.rewinding { color: #e94560; }

.ts-hint { font-size: 0.70rem; color: rgba(255,255,255,0.28); }

.ts-slider {
  width: 100%;
  -webkit-appearance: none;
  appearance: none;
  height: 4px;
  border-radius: 2px;
  outline: none;
  cursor: pointer;
  background: linear-gradient(
    to right,
    #e94560 0%,
    #e94560 var(--pct, 0%),
    rgba(255,255,255,0.14) var(--pct, 0%),
    rgba(255,255,255,0.14) 100%
  );
}
.ts-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px; height: 16px;
  border-radius: 50%;
  background: #e94560;
  border: 2px solid #fff;
  cursor: grab;
  transition: transform 0.12s, box-shadow 0.12s;
}
.ts-slider:active::-webkit-slider-thumb {
  cursor: grabbing;
  transform: scale(1.35);
  box-shadow: 0 0 0 4px rgba(233, 69, 96, 0.3);
}
.ts-slider::-moz-range-thumb {
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #e94560;
  border: 2px solid #fff;
  cursor: grab;
}

.ts-ticks {
  display: flex;
  justify-content: space-between;
  font-size: 0.66rem;
  color: rgba(255,255,255,0.25);
  user-select: none;
}

.ts-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
  margin-top: 4px;
}

.btn-travel {
  border-color: #e94560;
  background: #e94560;
}

.btn-live {
  border-color: rgba(255,255,255,0.35);
  background: transparent;
}
</style>

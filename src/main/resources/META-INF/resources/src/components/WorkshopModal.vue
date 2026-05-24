<template>
  <Teleport to="body">
    <div class="ws-backdrop" @click.self="emit('close')">
      <div class="ws-modal" role="dialog" aria-modal="true" aria-label="Workshop">

        <header class="ws-header">
          <h2 class="ws-title">🔧 Workshop</h2>
          <button class="ws-close" @click="emit('close')" aria-label="Close">✕</button>
        </header>

        <section class="ws-section">
          <h3 class="ws-section-title">Config File</h3>
          <div class="file-row">
            <button class="btn-load-config" @click="openConfigFile('json')">Load JSON</button>
            <button class="btn-load-config" @click="openConfigFile('yaml')">Load YAML</button>
            <button class="btn-load-config" @click="openConfigFile('toml')">Load TOML</button>
            <input
              ref="configFileInput"
              class="file-input hidden-file-input"
              type="file"
              :accept="configAccept"
              @change="onConfigFile"
            />
            <span v-once class="ws-hint">JSON, YAML, or TOML</span>
          </div>
          <div v-if="previewOverlay" class="preview-overlay" v-memo="[previewOverlay.version]">
            <span class="pv-label">Checksum</span>
            <span class="pv-val">{{ previewOverlay.checksum?.slice(0, 12) ?? 'pending' }}</span>
            <span class="pv-label">Collisions</span>
            <span class="pv-val">{{ previewOverlay.collisionCount }}</span>
            <span class="pv-label">Frame time</span>
            <span class="pv-val">{{ previewOverlay.frameTimeMs.toFixed(3) }}ms</span>
            <span class="pv-label">Memory</span>
            <span class="pv-val">{{ previewOverlay.memoryBytes }}B</span>
          </div>
        </section>

        <section class="ws-section">
          <h3 class="ws-section-title">Speed Mode</h3>
          <div class="ws-card-row">
            <button
              v-for="mode in SPEED_MODES"
              :key="mode.id"
              class="ws-card"
              :class="{ active: selectedMode === mode.id }"
              @click="selectedMode = mode.id"
            >
              <span class="ws-card-icon">{{ mode.icon }}</span>
              <span class="ws-card-name">{{ mode.name }}</span>
              <span class="ws-card-sub">{{ mode.base }}× base · max {{ mode.max }}×</span>
            </button>
          </div>

          <Transition name="slide">
            <div v-if="selectedMode === 'custom'" class="ws-custom-sliders">
              <div v-for="s in CUSTOM_SLIDERS" :key="s.key" class="slider-row">
                <span class="slider-label">{{ s.label }}</span>
                <span class="slider-val">{{ s.display(customCfg) }}</span>
                <input
                  type="range"
                  :min="s.min" :max="s.max" :step="s.step"
                  v-model.number="(customCfg as Record<string, number>)[s.key]"
                  class="ws-slider"
                />
              </div>
            </div>
          </Transition>
        </section>

        <section class="ws-section">
          <h3 class="ws-section-title">Difficulty Level</h3>
          <div class="ws-level-row">
            <button
              v-for="level in GAME_LEVELS"
              :key="level.id"
              class="ws-level-btn"
              :class="[{ active: selectedLevel === level.id }, `tier-${level.tier}`]"
              @click="selectedLevel = level.id"
            >
              <span class="level-badge">{{ level.num }}</span>
              <span class="level-name">{{ level.name }}</span>
              <span class="level-stats">
                +{{ (level.timeRate * 100).toFixed(0) }}%/min
                · +{{ (level.lineRate * 100).toFixed(0) }}%/line
              </span>
            </button>
          </div>
        </section>

        <section class="ws-section">
          <h3 class="ws-section-title">AI Opponent</h3>
          <div class="ws-card-row">
            <button
              v-for="preset in AI_PRESETS"
              :key="preset.id"
              class="ws-card"
              :class="{ active: selectedAiPreset === preset.id }"
              @click="selectedAiPreset = preset.id"
            >
              <span class="ws-card-icon">{{ preset.icon }}</span>
              <span class="ws-card-name">{{ preset.name }}</span>
              <span class="ws-card-sub">
                {{ preset.enabled ? `${preset.reactionDelayMs}ms delay` : 'Human/off' }}
              </span>
            </button>
          </div>

          <Transition name="slide">
            <div v-if="selectedAiPreset === 'custom'" class="ws-custom-sliders">
              <label class="toggle-row">
                <input type="checkbox" v-model="customAi.enabled" />
                <span>Server bot enabled</span>
              </label>
              <div v-for="s in AI_SLIDERS" :key="s.key" class="slider-row">
                <span class="slider-label">{{ s.label }}</span>
                <span class="slider-val">{{ s.display(customAi) }}</span>
                <input
                  type="range"
                  :min="s.min" :max="s.max" :step="s.step"
                  v-model.number="(customAi as unknown as Record<string, number>)[s.key]"
                  class="ws-slider"
                />
              </div>
            </div>
          </Transition>
        </section>

        <section class="ws-section ws-preview">
          <h3 class="ws-section-title">Config Preview</h3>
          <div class="preview-grid">
            <span class="pv-label">Base speed</span>
            <span class="pv-val">{{ preview.baseMultiplier.toFixed(2) }}×</span>
            <span class="pv-label">Max speed</span>
            <span class="pv-val">{{ preview.maxMultiplier.toFixed(1) }}×</span>
            <span class="pv-label">Time accel</span>
            <span class="pv-val">+{{ (preview.timeAccelerationRate * 100).toFixed(1) }}%/min</span>
            <span class="pv-label">Level accel</span>
            <span class="pv-val">+{{ (preview.levelAccelerationPerLine * 100).toFixed(1) }}%/line</span>
            <span class="pv-label">Bot mode</span>
            <span class="pv-val">{{ aiPreview.enabled ? 'On' : 'Off' }}</span>
            <span class="pv-label">Bot delay</span>
            <span class="pv-val">{{ aiPreview.reactionDelayMs }}ms</span>
            <span class="pv-label">Bot speed</span>
            <span class="pv-val">{{ aiPreview.maxSpeed.toFixed(0) }}px/s</span>
            <span class="pv-label">Bot error</span>
            <span class="pv-val">{{ aiPreview.trackingError.toFixed(0) }}px</span>
          </div>
        </section>

        <section class="ws-section ws-publish-row">
          <div class="publish-info">
            <h3 class="ws-section-title">Publish Layout</h3>
            <p class="ws-hint">
              Save the current barrier layout to the workshop
              ({{ lineCount }} line{{ lineCount !== 1 ? 's' : '' }} drawn).
            </p>
          </div>
          <button class="btn-publish" @click="onPublish" :disabled="publishing">
            {{ publishing ? 'Publishing…' : 'Publish' }}
          </button>
        </section>

        <footer class="ws-footer">
          <span v-if="statusMsg" class="ws-status" :class="{ error: statusMsg.startsWith('✗') }">
            {{ statusMsg }}
          </span>
          <div class="ws-footer-btns">
            <button class="btn-ws-cancel" @click="emit('close')">Cancel</button>
            <button class="btn-ws-apply" @click="onApply">Apply &amp; Play</button>
          </div>
        </footer>

      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, reactive, computed, markRaw, shallowRef } from 'vue';
import { useWorkshopApi, ContentType } from '../api/workshop';
import type { AiOpponentConfig, PreviewResponse, SpeedConfig } from '../api/workshop';

const props = defineProps<{ lineCount: number; gameLines: unknown[] }>();
const emit  = defineEmits<{ close: [] }>();

const { setSpeedConfig, setAiOpponentConfig, publishContent } = useWorkshopApi();

interface SpeedMode {
  id: string;
  icon: string;
  name: string;
  base: number;
  max: number;
}

const SPEED_MODES: SpeedMode[] = [
  { id: 'slow',   icon: '🐢', name: 'Slow',   base: 0.7, max: 1.5 },
  { id: 'normal', icon: '⚡', name: 'Normal', base: 1.0, max: 3.0 },
  { id: 'fast',   icon: '🚀', name: 'Fast',   base: 1.5, max: 4.0 },
  { id: 'turbo',  icon: '💥', name: 'Turbo',  base: 2.2, max: 5.0 },
  { id: 'custom', icon: '⚙',  name: 'Custom', base: 0,   max: 0   },
];

interface GameLevel {
  id: string;
  num: string;
  name: string;
  tier: number;
  timeRate: number;
  lineRate: number;
}

const GAME_LEVELS: GameLevel[] = [
  { id: 'l1', num: 'I',   name: 'Chill',    tier: 1, timeRate: 0.00, lineRate: 0.00 },
  { id: 'l2', num: 'II',  name: 'Casual',   tier: 2, timeRate: 0.02, lineRate: 0.01 },
  { id: 'l3', num: 'III', name: 'Sport',    tier: 3, timeRate: 0.05, lineRate: 0.02 },
  { id: 'l4', num: 'IV',  name: 'Hardcore', tier: 4, timeRate: 0.10, lineRate: 0.04 },
  { id: 'l5', num: 'V',   name: 'Insane',   tier: 5, timeRate: 0.20, lineRate: 0.05 },
];

interface AiPreset extends AiOpponentConfig {
  id: string;
  icon: string;
  name: string;
}

const AI_PRESETS: AiPreset[] = [
  { id: 'off',      icon: '○', name: 'Off',      enabled: false, reactionDelayMs: 0,   maxSpeed: 160, trackingError: 0,  reactZoneRatio: 0.7 },
  { id: 'training', icon: 'I', name: 'Training', enabled: true,  reactionDelayMs: 360, maxSpeed: 110, trackingError: 24, reactZoneRatio: 0.6 },
  { id: 'normal',   icon: 'II',name: 'Normal',   enabled: true,  reactionDelayMs: 180, maxSpeed: 180, trackingError: 10, reactZoneRatio: 0.7 },
  { id: 'hard',     icon: 'III',name: 'Hard',    enabled: true,  reactionDelayMs: 80,  maxSpeed: 260, trackingError: 2,  reactZoneRatio: 0.85 },
  { id: 'custom',   icon: '⚙', name: 'Custom',   enabled: true,  reactionDelayMs: 180, maxSpeed: 180, trackingError: 10, reactZoneRatio: 0.7 },
];

interface SliderDef {
  key: keyof SpeedConfig;
  label: string;
  min: number;
  max: number;
  step: number;
  display: (c: SpeedConfig) => string;
}

const CUSTOM_SLIDERS: SliderDef[] = [
  { key: 'baseMultiplier',          label: 'Base Speed',        min: 0.5, max: 3,    step: 0.05,  display: c => `${c.baseMultiplier.toFixed(2)}×` },
  { key: 'maxMultiplier',           label: 'Max Speed',         min: 1,   max: 5,    step: 0.1,   display: c => `${c.maxMultiplier.toFixed(1)}×` },
  { key: 'timeAccelerationRate',    label: 'Time Acceleration', min: 0,   max: 0.2,  step: 0.005, display: c => `+${(c.timeAccelerationRate * 100).toFixed(1)}%/min` },
  { key: 'levelAccelerationPerLine',label: 'Level Accel/line',  min: 0,   max: 0.05, step: 0.001, display: c => `+${(c.levelAccelerationPerLine * 100).toFixed(1)}%` },
];

interface AiSliderDef {
  key: keyof Pick<AiOpponentConfig, 'reactionDelayMs' | 'maxSpeed' | 'trackingError' | 'reactZoneRatio'>;
  label: string;
  min: number;
  max: number;
  step: number;
  display: (c: AiOpponentConfig) => string;
}

const AI_SLIDERS: AiSliderDef[] = [
  { key: 'reactionDelayMs', label: 'Reaction Delay', min: 0,    max: 900, step: 10,   display: c => `${c.reactionDelayMs.toFixed(0)}ms` },
  { key: 'maxSpeed',        label: 'Paddle Speed',   min: 60,   max: 420, step: 5,    display: c => `${c.maxSpeed.toFixed(0)}px/s` },
  { key: 'trackingError',   label: 'Tracking Error', min: -40,  max: 60,  step: 1,    display: c => `${c.trackingError.toFixed(0)}px` },
  { key: 'reactZoneRatio',  label: 'React Zone',     min: 0.35, max: 1,   step: 0.01, display: c => `${(c.reactZoneRatio * 100).toFixed(0)}%` },
];

const selectedMode  = ref<string>('normal');
const selectedLevel = ref<string>('l3');
const selectedAiPreset = ref<string>('normal');
const customCfg     = reactive<SpeedConfig>({ baseMultiplier: 1.0, maxMultiplier: 3.0, timeAccelerationRate: 0.05, levelAccelerationPerLine: 0.02 });
const customAi      = reactive<AiOpponentConfig>({ enabled: true, reactionDelayMs: 180, maxSpeed: 180, trackingError: 10, reactZoneRatio: 0.7 });

const statusMsg = ref('');
const publishing = ref(false);
const previewOverlay = shallowRef<(PreviewResponse & { version: number }) | null>(null);
const configFileInput = ref<HTMLInputElement | null>(null);
const requestedConfigFormat = ref<'json' | 'yaml' | 'toml'>('yaml');
const worker = markRaw(new Worker(new URL('../workers/config-worker.ts', import.meta.url), { type: 'module' }));

const configAccept = computed(() => {
  if (requestedConfigFormat.value === 'json') return '.json,application/json';
  if (requestedConfigFormat.value === 'toml') return '.toml';
  return '.yaml,.yml';
});

worker.onmessage = (event: MessageEvent<{ ok: boolean; preview?: PreviewResponse; error?: string }>) => {
  if (!event.data.ok || !event.data.preview) {
    showStatus(`✗ ${event.data.error ?? 'Config preview failed'}`);
    return;
  }
  previewOverlay.value = markRaw({ ...event.data.preview, version: Date.now() });
  showStatus('✓ Preview sandbox passed');
};

const preview = computed<SpeedConfig>(() => {
  if (selectedMode.value === 'custom') return { ...customCfg };
  const mode  = SPEED_MODES.find(m => m.id === selectedMode.value)!;
  const level = GAME_LEVELS.find(l => l.id === selectedLevel.value)!;
  return {
    baseMultiplier:           mode.base,
    maxMultiplier:            mode.max,
    timeAccelerationRate:     level.timeRate,
    levelAccelerationPerLine: level.lineRate,
  };
});

const aiPreview = computed<AiOpponentConfig>(() => {
  if (selectedAiPreset.value === 'custom') return { ...customAi };
  const preset = AI_PRESETS.find(p => p.id === selectedAiPreset.value)!;
  return {
    enabled: preset.enabled,
    reactionDelayMs: preset.reactionDelayMs,
    maxSpeed: preset.maxSpeed,
    trackingError: preset.trackingError,
    reactZoneRatio: preset.reactZoneRatio,
  };
});

function showStatus(msg: string, ms = 3000) {
  statusMsg.value = msg;
  setTimeout(() => { statusMsg.value = ''; }, ms);
}

function openConfigFile(format: 'json' | 'yaml' | 'toml'): void {
  requestedConfigFormat.value = format;
  if (configFileInput.value) {
    configFileInput.value.value = '';
    configFileInput.value.click();
  }
}

function onConfigFile(e: Event): void {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const ext = file.name.split('.').pop()?.toLowerCase();
  const format = ext === 'json' || ext === 'toml' || ext === 'yaml' || ext === 'yml' ? ext : requestedConfigFormat.value;
  file.text().then(source => {
    requestIdle(() => {
      worker.postMessage({ id: crypto.randomUUID(), source, format });
      showStatus('Previewing config…', 1500);
    });
  }).catch(error => showStatus(`✗ ${error instanceof Error ? error.message : String(error)}`));
}

function requestIdle(callback: () => void): void {
  const idle = window.requestIdleCallback ?? ((cb: IdleRequestCallback) => window.setTimeout(() => cb({ didTimeout: false, timeRemaining: () => 0 }), 0));
  idle(() => callback());
}

async function onApply(): Promise<void> {
  const speedResult = await setSpeedConfig(preview.value);
  if (speedResult.error) { showStatus(`✗ ${speedResult.error}`); return; }
  const aiResult = await setAiOpponentConfig(aiPreview.value);
  if (aiResult.error) { showStatus(`✗ ${aiResult.error}`); return; }
  emit('close');
}

async function onPublish(): Promise<void> {
  publishing.value = true;
  const result = await publishContent(
    ContentType.LEVEL,
    { lines: props.gameLines },
    { name: 'My Barrier Layout', author: 'player' },
  );
  publishing.value = false;
  showStatus(result.error ? `✗ ${result.error}` : '✓ Published!');
}
</script>

<style scoped>

.ws-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 12px;
}

.ws-modal {
  background: #12122a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.ws-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.ws-title {
  font-size: 1.1rem;
  letter-spacing: 2px;
  color: #ffd740;
}

.ws-close {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.6);
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.ws-close:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }

.ws-section {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.ws-section:last-of-type { border-bottom: none; }

.ws-section-title {
  font-size: 0.72rem;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 12px;
}

.ws-hint {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 4px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.file-input {
  color: rgba(255, 255, 255, 0.72);
  font-family: monospace;
  font-size: 0.78rem;
}

.hidden-file-input {
  display: none;
}

.btn-load-config {
  border-color: #40c4ff;
  padding: 7px 12px;
  font-size: 0.78rem;
}

.btn-load-config:hover {
  background: #40c4ff;
  color: #000;
}

.preview-overlay {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
  padding: 10px;
  border: 1px solid rgba(78, 204, 163, 0.28);
  border-radius: 6px;
  background: rgba(78, 204, 163, 0.06);
}

.ws-card-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ws-card {
  flex: 1 1 80px;
  min-width: 70px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  font-family: monospace;
  color: rgba(255, 255, 255, 0.7);
}
.ws-card:hover { background: rgba(255, 215, 64, 0.08); border-color: rgba(255, 215, 64, 0.35); }
.ws-card.active { background: rgba(255, 215, 64, 0.12); border-color: #ffd740; color: #fff; }

.ws-card-icon { font-size: 1.4rem; }
.ws-card-name { font-size: 0.8rem; font-weight: 700; }
.ws-card-sub  { font-size: 0.65rem; color: rgba(255, 255, 255, 0.4); text-align: center; }
.ws-card.active .ws-card-sub { color: rgba(255, 215, 64, 0.7); }

.ws-custom-sliders {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slider-row {
  display: grid;
  grid-template-columns: 1fr 90px 1fr;
  align-items: center;
  gap: 10px;
}

.slider-label { font-size: 0.78rem; color: rgba(255, 255, 255, 0.65); }
.slider-val   { font-size: 0.78rem; color: #ffd740; text-align: right; font-variant-numeric: tabular-nums; }

.toggle-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.68);
}

.toggle-row input {
  width: 16px;
  height: 16px;
  accent-color: #ffd740;
}

.ws-slider {
  width: 100%;
  -webkit-appearance: none;
  appearance: none;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.15);
  outline: none;
  cursor: pointer;
}
.ws-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px; height: 14px;
  border-radius: 50%;
  background: #ffd740;
  border: 2px solid #fff;
  cursor: grab;
  transition: transform 0.1s;
}
.ws-slider:active::-webkit-slider-thumb { cursor: grabbing; transform: scale(1.3); }
.ws-slider::-moz-range-thumb {
  width: 12px; height: 12px; border-radius: 50%;
  background: #ffd740; border: 2px solid #fff;
}

.slide-enter-active, .slide-leave-active { transition: opacity 0.2s, transform 0.2s; }
.slide-enter-from, .slide-leave-to { opacity: 0; transform: translateY(-6px); }

.ws-level-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ws-level-btn {
  flex: 1 1 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 12px 6px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  cursor: pointer;
  font-family: monospace;
  transition: background 0.15s, border-color 0.15s;
}

.level-badge {
  font-size: 1.1rem;
  font-weight: 700;
  line-height: 1;
}
.level-name  { font-size: 0.75rem; color: rgba(255, 255, 255, 0.7); }
.level-stats { font-size: 0.6rem;  color: rgba(255, 255, 255, 0.35); text-align: center; }

.ws-level-btn.tier-1 .level-badge { color: #4ecca3; }
.ws-level-btn.tier-2 .level-badge { color: #40c4ff; }
.ws-level-btn.tier-3 .level-badge { color: #ffd740; }
.ws-level-btn.tier-4 .level-badge { color: #ff9100; }
.ws-level-btn.tier-5 .level-badge { color: #e94560; }

.ws-level-btn:hover { background: rgba(255, 255, 255, 0.07); border-color: rgba(255, 255, 255, 0.25); }
.ws-level-btn.active.tier-1 { background: rgba(78, 204, 163, 0.12); border-color: #4ecca3; }
.ws-level-btn.active.tier-2 { background: rgba(64, 196, 255, 0.12); border-color: #40c4ff; }
.ws-level-btn.active.tier-3 { background: rgba(255, 215, 64, 0.12); border-color: #ffd740; }
.ws-level-btn.active.tier-4 { background: rgba(255, 145, 0,  0.12); border-color: #ff9100; }
.ws-level-btn.active.tier-5 { background: rgba(233, 69, 96,  0.12); border-color: #e94560; }
.ws-level-btn.active .level-name  { color: #fff; }
.ws-level-btn.active .level-stats { color: rgba(255, 255, 255, 0.55); }

.preview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 8px 0;
}
.pv-label { font-size: 0.7rem; color: rgba(255, 255, 255, 0.4); }
.pv-val   { font-size: 0.82rem; color: #ffd740; font-variant-numeric: tabular-nums; font-weight: 700; }

.ws-publish-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-publish {
  background: transparent;
  border: 1px solid #4ecca3;
  color: #4ecca3;
  padding: 8px 18px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 0.85rem;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}
.btn-publish:hover:not(:disabled) { background: #4ecca3; color: #000; }
.btn-publish:disabled { opacity: 0.5; cursor: default; }

.ws-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-wrap: wrap;
}

.ws-status {
  font-size: 0.82rem;
  color: #4ecca3;
  flex: 1;
}
.ws-status.error { color: #e94560; }

.ws-footer-btns { display: flex; gap: 8px; }

.btn-ws-cancel {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.55);
  padding: 8px 18px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.btn-ws-cancel:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }

.btn-ws-apply {
  background: #ffd740;
  border: 1px solid #ffd740;
  color: #000;
  padding: 8px 22px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.btn-ws-apply:hover { background: #ffe57a; box-shadow: 0 0 12px rgba(255, 215, 64, 0.4); }
</style>

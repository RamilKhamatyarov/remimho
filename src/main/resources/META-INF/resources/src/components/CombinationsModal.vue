<template>
  <Teleport to="body">
    <div class="combination-backdrop" @click.self="close">
      <section ref="dialog" class="combination-dialog" role="dialog" aria-modal="true"
        aria-labelledby="combination-title" @keydown="onKeydown">
        <header>
          <h2 id="combination-title">Passing Combinations</h2>
          <button class="icon-button" title="Close" aria-label="Close" @click="close"><X :size="20" /></button>
        </header>
        <div class="layout-grid">
          <button v-for="layout in layouts" :key="layout.id" class="layout-item"
            :class="{ selected: selectedId === layout.id }" :aria-pressed="selectedId === layout.id"
            :disabled="pending" @click="selectedId = layout.id">
            <CombinationPreview :layout="layout" :side="side" />
            <span>{{ layout.name }}</span>
            <Check v-if="activeId === layout.id" :size="16" class="active-mark" aria-label="Active" />
          </button>
        </div>
        <form class="save-row" @submit.prevent="saveCurrent">
          <label for="combination-name">Save current lines</label>
          <input id="combination-name" v-model="name" maxlength="60" placeholder="Combination name" :disabled="pending" />
          <button type="submit" class="icon-button" title="Save combination" aria-label="Save combination"
            :disabled="!name.trim() || !hasOwnLines || pending"><Save :size="18" /></button>
        </form>
        <div class="file-row">
          <button @click="fileInput?.click()" :disabled="pending"><Upload :size="17" /> Import</button>
          <button @click="exportSelected" :disabled="!selected || pending"><Download :size="17" /> Export</button>
          <button v-if="isCustom" class="icon-button" title="Delete saved combination" aria-label="Delete saved combination"
            @click="deleteSelected" :disabled="pending"><Trash2 :size="17" /></button>
          <input ref="fileInput" type="file" accept=".json,application/json" hidden @change="importFile" />
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-else-if="notice" role="status" class="notice">{{ notice }}</p>
        <footer>
          <button :disabled="!activeId || pending || !connected" @click="apply(null)">Disable preset</button>
          <button class="apply-button" :disabled="!selected || pending || !connected" @click="apply(selected)">
            <Check :size="18" /> {{ pending ? 'Applying...' : 'Pause & Apply' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Check, Download, Save, Trash2, Upload, X } from '@lucide/vue'
import CombinationPreview from './CombinationPreview.vue'
import {
  captureCombination, DEFAULT_COMBINATIONS, parseCombination, readSavedCombinations, saveCombinations,
} from '../combinations'
import type { PassingCombination } from '../combinations'
import { combinationResultRef, useGameSocket } from '../composables/useGameSocket'

const emit = defineEmits<{ close: [] }>()
const { gameState, currentSide: side, connected, send } = useGameSocket()
const custom = ref<PassingCombination[]>([])
const selectedId = ref(DEFAULT_COMBINATIONS[0].id)
const name = ref('')
const error = ref('')
const notice = ref('')
const pending = ref(false)
const dialog = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
let timeout: ReturnType<typeof setTimeout> | undefined
let returnFocus: HTMLElement | null = null
const layouts = computed(() => [...DEFAULT_COMBINATIONS, ...custom.value])
const selected = computed(() => layouts.value.find(layout => layout.id === selectedId.value) ?? null)
const isCustom = computed(() => custom.value.some(layout => layout.id === selectedId.value))
const hasOwnLines = computed(() => gameState.value?.lines.some(line => line.ownerSide === side))
const activeId = computed(() => gameState.value?.lines.find(line => line.ownerSide === side && line.combinationId)?.combinationId)

onMounted(() => {
  returnFocus = document.activeElement as HTMLElement | null
  try { custom.value = readSavedCombinations(localStorage) }
  catch { error.value = 'Saved combinations could not be loaded. Export a backup before replacing them.' }
  if (activeId.value && layouts.value.some(layout => layout.id === activeId.value)) selectedId.value = activeId.value
  void nextTick(() => dialog.value?.querySelector<HTMLButtonElement>('button')?.focus())
})

onUnmounted(() => {
  clearTimeout(timeout)
  returnFocus?.focus()
})

watch(combinationResultRef, result => {
  if (!pending.value || !result) return
  pending.value = false
  clearTimeout(timeout)
  if (result.ok) emit('close')
  else error.value = result.message
})

watch(connected, value => {
  if (!value && pending.value) {
    pending.value = false
    clearTimeout(timeout)
    error.value = 'Connection lost. Check the board before retrying.'
  }
})

function close() { if (!pending.value) emit('close') }

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close() }
  if (event.key !== 'Tab') return
  const nodes = dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled):not([hidden])')
  if (!nodes?.length) return
  const first = nodes[0], last = nodes[nodes.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

function report(failure: unknown) { error.value = failure instanceof Error ? failure.message : String(failure) }

function store(layouts: PassingCombination[]) {
  saveCombinations(localStorage, layouts)
  custom.value = layouts
}

function saveCurrent() {
  error.value = ''; notice.value = ''
  const state = gameState.value
  if (!state) return
  try {
    const layout = captureCombination(
      state.lines, side, state.canvasWidth, state.canvasHeight, name.value, `custom-${crypto.randomUUID()}`,
    )
    store([...custom.value, layout])
    selectedId.value = layout.id
    name.value = ''
    notice.value = 'Combination saved'
  } catch (failure) { report(failure) }
}

async function importFile(event: Event) {
  error.value = ''; notice.value = ''
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    if (file.size > 512_000) throw new Error('Combination file exceeds 500 KB')
    const layout = parseCombination(JSON.parse(await file.text()))
    layout.id = `custom-${crypto.randomUUID()}`
    store([...custom.value, layout])
    selectedId.value = layout.id
    notice.value = 'Combination imported'
  } catch (failure) { report(failure) }
}

function exportSelected() {
  if (!selected.value) return
  const url = URL.createObjectURL(new Blob([JSON.stringify(selected.value, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${selected.value.id}.json`
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function deleteSelected() {
  error.value = ''; notice.value = ''
  try {
    store(custom.value.filter(layout => layout.id !== selectedId.value))
    selectedId.value = DEFAULT_COMBINATIONS[0].id
    notice.value = 'Saved combination deleted'
  } catch (failure) { report(failure) }
}

function apply(layout: PassingCombination | null) {
  error.value = ''; notice.value = ''
  combinationResultRef.value = null
  pending.value = true
  if (!send('APPLY_COMBINATION', { id: layout?.id ?? '', lines: layout?.lines ?? [] })) {
    pending.value = false
    error.value = 'Not connected'
    return
  }
  timeout = setTimeout(() => {
    pending.value = false
    error.value = 'No acknowledgement received. Check the board before retrying.'
  }, 5000)
}
</script>

<style scoped>
.combination-backdrop { position: fixed; inset: 0; z-index: 110; display: grid; place-items: center;
  background: #000a; padding: 12px; }
.combination-dialog { width: min(700px, 100%); max-height: 92dvh; overflow-y: auto; padding: 20px;
  background: #202329; color: #edf0f4; border: 1px solid #4a505b; border-radius: 8px; letter-spacing: 0; }
header, footer, .file-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
header { justify-content: space-between; margin-bottom: 18px; }
h2 { font-size: 18px; font-weight: 600; }
button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; background: transparent;
  color: inherit; border: 1px solid #616671; border-radius: 4px; padding: 8px 12px; font: inherit; cursor: pointer; }
button:hover:not(:disabled) { background: #ffffff12; }
button:disabled { opacity: .45; cursor: default; }
button:focus-visible, input:focus-visible { outline: 2px solid #64d9b0; outline-offset: 3px; }
.icon-button { flex: 0 0 36px; width: 36px; height: 36px; padding: 6px; }
.layout-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.layout-item { position: relative; display: flex; flex-direction: column; padding: 7px; gap: 9px; min-width: 0; }
.layout-item span { font-size: 13px; overflow-wrap: anywhere; width: 100%; text-align: left; padding-right: 18px; }
.layout-item.selected { border-color: #64d9b0; background: #64d9b00a; }
.active-mark { position: absolute; right: 8px; bottom: 10px; color: #64d9b0; }
.save-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 20px 0 12px;
  padding-top: 16px; border-top: 1px solid #424752; }
.save-row label { flex-basis: 100%; font-size: 13px; color: #c1c7d1; }
input:not([hidden]) { flex: 1; min-width: 0; background: #171a20; color: inherit; border: 1px solid #616671;
  padding: 9px; border-radius: 4px; font: inherit; }
footer { justify-content: space-between; margin-top: 20px; padding-top: 16px; border-top: 1px solid #424752; }
.apply-button { border-color: #64d9b0; color: #64d9b0; }
.error, .notice { margin-top: 12px; font-size: 13px; overflow-wrap: anywhere; }
.error { color: #ffa4ad; } .notice { color: #64d9b0; }
@media (max-width: 520px) {
  .combination-dialog { padding: 14px; } .layout-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  h2 { font-size: 16px; } footer button { flex: 1 1 150px; }
}
</style>

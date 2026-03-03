<template>
  <canvas
    ref="canvasRef"
    :width="props.width"
    :height="props.height"
    @mousemove="onMouseMove"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { gameStateRef } from '../composables/useGameSocket'
import type { GameState } from '../types/game'

const props = defineProps<{
  width: number
  height: number
}>()

const emit = defineEmits<{
  paddleMove: [y: number]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const PADDLE_WIDTH = 20

let rafId = 0
let frameCount = 0
let lastLog = performance.now()

function loop(now: number) {
  try {
    frameCount++
    if (now - lastLog > 1000) {
      console.log(`[Canvas] FPS: ${frameCount}`)
      frameCount = 0
      lastLog = now
    }

    const state = gameStateRef.value
    if (state) draw(state)
  } catch (err) {
    console.error('[Canvas] Fatal error in draw():', err)
  } finally {
    rafId = requestAnimationFrame(loop)
  }
}

onMounted(() => {
  console.log('[Canvas] Mounted, starting RAF loop')
  rafId = requestAnimationFrame(loop)
})

onUnmounted(() => {
  console.log('[Canvas] Unmounted, cancelling RAF')
  cancelAnimationFrame(rafId)
})

function draw(state: GameState) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  if (
    !state.puck ||
    typeof state.puck.x !== 'number' ||
    typeof state.puck.y !== 'number' ||
    typeof state.puck.radius !== 'number' ||
    !state.score ||
    typeof state.score.playerA !== 'number' ||
    typeof state.score.playerB !== 'number' ||
    typeof state.canvasWidth !== 'number' ||
    typeof state.canvasHeight !== 'number' ||
    typeof state.paddleHeight !== 'number' ||
    typeof state.paddle1Y !== 'number' ||
    typeof state.paddle2Y !== 'number' ||
    typeof state.paused !== 'boolean'
  ) {
    console.warn('[Canvas] Incomplete game state, skipping draw', state)
    return
  }

  const scaleX = canvas.width / state.canvasWidth
  const scaleY = canvas.height / state.canvasHeight

  ctx.fillStyle = '#1a1a2e'
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  ctx.save()
  ctx.setLineDash([10, 10])
  ctx.strokeStyle = 'rgba(255,255,255,0.2)'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(canvas.width / 2, 0)
  ctx.lineTo(canvas.width / 2, canvas.height)
  ctx.stroke()
  ctx.restore()

  ctx.fillStyle = '#e94560'
  ctx.fillRect(
    0,
    state.paddle1Y * scaleY,
    PADDLE_WIDTH * scaleX,
    state.paddleHeight * scaleY
  )

  ctx.fillStyle = '#4ecca3'
  ctx.fillRect(
    canvas.width - PADDLE_WIDTH * scaleX,
    state.paddle2Y * scaleY,
    PADDLE_WIDTH * scaleX,
    state.paddleHeight * scaleY
  )

  ctx.beginPath()
  ctx.arc(
    state.puck.x * scaleX,
    state.puck.y * scaleY,
    state.puck.radius * Math.min(scaleX, scaleY),
    0,
    Math.PI * 2
  )
  ctx.fillStyle = '#ffffff'
  ctx.fill()

  ctx.font = `bold ${Math.round(32 * scaleX)}px monospace`
  ctx.fillStyle = 'rgba(255,255,255,0.7)'
  ctx.textAlign = 'center'
  ctx.fillText(String(state.score.playerA), canvas.width * 0.25, 50 * scaleY)
  ctx.fillText(String(state.score.playerB), canvas.width * 0.75, 50 * scaleY)

  if (state.paused) {
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = '#ffffff'
    ctx.font = `bold ${Math.round(48 * scaleX)}px monospace`
    ctx.textAlign = 'center'
    ctx.fillText('PAUSED', canvas.width / 2, canvas.height / 2)
  }
}

function onMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value
  const state = gameStateRef.value
  if (!canvas || !state) return
  const rect = canvas.getBoundingClientRect()
  const relY = e.clientY - rect.top
  const scaleY = state.canvasHeight / canvas.height
  emit('paddleMove', relY * scaleY)
}
</script>

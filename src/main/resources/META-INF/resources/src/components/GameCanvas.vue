<template>
  <canvas
    ref="canvasRef"
    :width="props.width"
    :height="props.height"
    :style="{ cursor: isDrawing ? 'crosshair' : 'none' }"
    @mousemove="onMouseMove"
    @mousedown="onMouseDown"
    @mouseup="onMouseUp"
    @mouseleave="onMouseLeave"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { gameStateRef, useGameSocket } from '../composables/useGameSocket'
import type { GameState, Point } from '../types/game'

const props = defineProps<{ width: number; height: number }>()
const emit = defineEmits<{ paddleMove: [y: number] }>()
const { send } = useGameSocket()
const isDrawing = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const PADDLE_WIDTH = 20

let rafId = 0
function loop() {
  const state = gameStateRef.value
  if (state) draw(state)
  rafId = requestAnimationFrame(loop)
}
onMounted(() => { rafId = requestAnimationFrame(loop) })
onUnmounted(() => { cancelAnimationFrame(rafId) })

function draw(state: GameState) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx || !state.puck || typeof state.canvasWidth !== 'number') return

  const sx = canvas.width  / state.canvasWidth
  const sy = canvas.height / state.canvasHeight

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

  if (state.lines && state.lines.length > 0) {
    ctx.save()
    ctx.strokeStyle = '#f0a500'
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    for (const line of state.lines) {
      const pts: Point[] = (line.flattenedPoints && line.flattenedPoints.length > 1)
        ? line.flattenedPoints : line.controlPoints
      if (pts.length < 2) continue
      ctx.lineWidth = (line.width ?? 5) * Math.min(sx, sy)
      ctx.beginPath()
      ctx.moveTo(pts[0].x * sx, pts[0].y * sy)
      for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i].x * sx, pts[i].y * sy)
      ctx.stroke()
    }
    ctx.restore()
  }

  if (state.powerUps && state.powerUps.length > 0) {
    const t = performance.now() / 600
    for (const pu of state.powerUps) {
      const pulse = 1 + 0.12 * Math.sin(t + pu.x)
      const r     = pu.radius * Math.min(sx, sy) * pulse
      const cx    = pu.x * sx
      const cy    = pu.y * sy

      ctx.save()
      ctx.shadowBlur  = 18
      ctx.shadowColor = pu.color
      ctx.beginPath()
      ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.fillStyle = pu.color + '44'
      ctx.fill()
      ctx.strokeStyle = pu.color
      ctx.lineWidth   = 2 * Math.min(sx, sy)
      ctx.stroke()
      ctx.restore()

      ctx.font      = `${Math.round(r * 1.3)}px serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(pu.emoji, cx, cy)
      ctx.textBaseline = 'alphabetic'
    }
  }

  ctx.fillStyle = '#e94560'
  ctx.fillRect(0, state.paddle1Y * sy, PADDLE_WIDTH * sx, state.paddleHeight * sy)
  ctx.fillStyle = '#4ecca3'
  ctx.fillRect(canvas.width - PADDLE_WIDTH * sx, state.paddle2Y * sy, PADDLE_WIDTH * sx, state.paddleHeight * sy)

  ctx.beginPath()
  ctx.arc(state.puck.x * sx, state.puck.y * sy, state.puck.radius * Math.min(sx, sy), 0, Math.PI * 2)
  ctx.fillStyle = '#ffffff'
  ctx.fill()

  ctx.font = `bold ${Math.round(32 * sx)}px monospace`
  ctx.fillStyle = 'rgba(255,255,255,0.7)'
  ctx.textAlign = 'center'
  ctx.fillText(String(state.score.playerA), canvas.width * 0.25, 50 * sy)
  ctx.fillText(String(state.score.playerB), canvas.width * 0.75, 50 * sy)

  if (state.activePowerUpEffects && state.activePowerUpEffects.length > 0) {
    const badgeW = 54 * sx
    const badgeH = 28 * sy
    const pad    = 6 * sx
    let bx       = canvas.width / 2 - (state.activePowerUpEffects.length * (badgeW + pad)) / 2

    for (const eff of state.activePowerUpEffects) {
      const secs = (eff.remainingMs / 1000).toFixed(1)
      ctx.fillStyle = 'rgba(0,0,0,0.55)'
      ctx.beginPath()
      ctx.roundRect(bx, canvas.height - badgeH - 8 * sy, badgeW, badgeH, 6)
      ctx.fill()
      ctx.font = `${Math.round(14 * sx)}px serif`
      ctx.textAlign = 'center'
      ctx.fillText(eff.emoji, bx + badgeW * 0.3, canvas.height - badgeH / 2 - 8 * sy + 5 * sy)
      ctx.font = `${Math.round(10 * sx)}px monospace`
      ctx.fillStyle = '#fff'
      ctx.fillText(secs + 's', bx + badgeW * 0.7, canvas.height - badgeH / 2 - 8 * sy + 5 * sy)
      bx += badgeW + pad
    }
  }

  if (!isDrawing.value && (!state.activePowerUpEffects || state.activePowerUpEffects.length === 0)) {
    ctx.font = `${Math.round(11 * sx)}px monospace`
    ctx.fillStyle = 'rgba(255,255,255,0.25)'
    ctx.textAlign = 'center'
    ctx.fillText('Hold & drag to draw a barrier line', canvas.width / 2, canvas.height - 8)
  }

  if (state.paused) {
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = '#ffffff'
    ctx.font = `bold ${Math.round(48 * sx)}px monospace`
    ctx.textAlign = 'center'
    ctx.fillText('PAUSED', canvas.width / 2, canvas.height / 2)
  }
}

function toGamePt(e: MouseEvent): Point | null {
  const canvas = canvasRef.value
  const state  = gameStateRef.value
  if (!canvas || !state) return null
  const r = canvas.getBoundingClientRect()
  return {
    x: (e.clientX - r.left) * (state.canvasWidth  / canvas.width),
    y: (e.clientY - r.top)  * (state.canvasHeight / canvas.height),
  }
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  const pt = toGamePt(e); if (!pt) return
  isDrawing.value = true
  send('START_LINE', { x: pt.x, y: pt.y })
}

function onMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value
  const state  = gameStateRef.value
  if (!canvas || !state) return
  const r = canvas.getBoundingClientRect()
  emit('paddleMove', (e.clientY - r.top) * (state.canvasHeight / canvas.height))
  if (isDrawing.value) {
    const pt = toGamePt(e); if (pt) send('UPDATE_LINE', { x: pt.x, y: pt.y })
  }
}

function onMouseUp()    { if (!isDrawing.value) return; isDrawing.value = false; send('FINISH_LINE') }
function onMouseLeave() { if (!isDrawing.value) return; isDrawing.value = false; send('FINISH_LINE') }
</script>

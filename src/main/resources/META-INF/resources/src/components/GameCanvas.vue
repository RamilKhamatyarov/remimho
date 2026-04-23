<template>
  <!--
    No :width/:height reactive props — the canvas sizes itself imperatively from
    state.canvasWidth/Height inside draw(). Reactive props would clear the bitmap
    on every re-render.

    CSS max-width:100% is applied in App.vue so the canvas shrinks on narrow
    viewports instead of overflowing.  All coordinate calculations use
    getBoundingClientRect() to account for CSS scaling.
  -->
  <canvas
    ref="canvasRef"
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

const emit = defineEmits<{ paddleMove: [y: number] }>()
const props = defineProps<{ timeshiftActive?: boolean }>()
const { send } = useGameSocket()
const isDrawing = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const PADDLE_WIDTH = 20

// ── Smooth puck interpolation ─────────────────────────────────────────────────
// Lerp toward the server-reported position each RAF frame.
// When drift > SNAP_THRESHOLD (goal reset / teleport), snap immediately.
let smoothPuckX = 400
let smoothPuckY = 300
const LERP        = 0.35   // interpolation factor per frame (~60fps)
const SNAP_THRESH = 60     // px: snap instead of lerp on large jumps

// ── RAF loop ──────────────────────────────────────────────────────────────────

let rafId = 0
function loop() {
  const state = gameStateRef.value
  if (state) {
    const dx = state.puck.x - smoothPuckX
    const dy = state.puck.y - smoothPuckY
    if (Math.abs(dx) > SNAP_THRESH || Math.abs(dy) > SNAP_THRESH) {
      // Puck teleported (goal reset) — snap to new position immediately
      smoothPuckX = state.puck.x
      smoothPuckY = state.puck.y
    } else {
      smoothPuckX += dx * LERP
      smoothPuckY += dy * LERP
    }
    draw(state)
  }
  rafId = requestAnimationFrame(loop)
}
onMounted(() => { rafId = requestAnimationFrame(loop) })
onUnmounted(() => { cancelAnimationFrame(rafId) })

// ── Drawing ───────────────────────────────────────────────────────────────────

function draw(state: GameState) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx || !state.puck || typeof state.canvasWidth !== 'number') return

  // Size the canvas pixel buffer from server dimensions.
  // Only reassign when they actually differ — assigning the same value still
  // clears the bitmap.
  if (canvas.width !== state.canvasWidth || canvas.height !== state.canvasHeight) {
    canvas.width  = state.canvasWidth
    canvas.height = state.canvasHeight
  }

  const W  = canvas.width
  const H  = canvas.height
  // sx/sy are always 1.0 because canvas pixel dims = game dims.
  // Kept as variables so the drawing code is explicit.
  const sx = W / state.canvasWidth
  const sy = H / state.canvasHeight

  // ── Background ──────────────────────────────────────────────────────────────
  ctx.fillStyle = '#1a1a2e'
  ctx.fillRect(0, 0, W, H)

  // ── Centre line ─────────────────────────────────────────────────────────────
  ctx.save()
  ctx.setLineDash([10, 10])
  ctx.strokeStyle = 'rgba(255,255,255,0.2)'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(W / 2, 0); ctx.lineTo(W / 2, H)
  ctx.stroke()
  ctx.restore()

  // ── Barrier lines ───────────────────────────────────────────────────────────
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

  // ── Power-up pickups ─────────────────────────────────────────────────────────
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
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.fillStyle = pu.color + '44'; ctx.fill()
      ctx.strokeStyle = pu.color; ctx.lineWidth = 2 * Math.min(sx, sy); ctx.stroke()
      ctx.restore()
      ctx.font = `${Math.round(r * 1.3)}px serif`
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
      ctx.fillText(pu.emoji, cx, cy)
      ctx.textBaseline = 'alphabetic'
    }
  }

  ctx.fillStyle = '#e94560'
  ctx.fillRect(0, state.paddle1Y * sy, PADDLE_WIDTH * sx, state.paddleHeight * sy)
  ctx.fillStyle = '#4ecca3'
  ctx.fillRect(W - PADDLE_WIDTH * sx, state.paddle2Y * sy, PADDLE_WIDTH * sx, state.paddleHeight * sy)

  ctx.beginPath()
  ctx.arc(
    smoothPuckX * sx,
    smoothPuckY * sy,
    state.puck.radius * Math.min(sx, sy),
    0, Math.PI * 2,
  )
  ctx.fillStyle = '#ffffff'
  ctx.fill()

  // ── Score ────────────────────────────────────────────────────────────────────
  ctx.font = `bold ${Math.round(32 * sx)}px monospace`
  ctx.fillStyle = 'rgba(255,255,255,0.7)'
  ctx.textAlign = 'center'
  ctx.fillText(String(state.score.playerA), W * 0.25, 50 * sy)
  ctx.fillText(String(state.score.playerB), W * 0.75, 50 * sy)

  // ── Active power-up effect badges ────────────────────────────────────────────
  if (state.activePowerUpEffects && state.activePowerUpEffects.length > 0) {
    const badgeW = 54 * sx
    const badgeH = 28 * sy
    const pad    = 6  * sx
    let bx = W / 2 - (state.activePowerUpEffects.length * (badgeW + pad)) / 2
    for (const eff of state.activePowerUpEffects) {
      const secs = (eff.remainingMs / 1000).toFixed(1)
      ctx.fillStyle = 'rgba(0,0,0,0.55)'
      ctx.beginPath(); ctx.roundRect(bx, H - badgeH - 8 * sy, badgeW, badgeH, 6); ctx.fill()
      ctx.font = `${Math.round(14 * sx)}px serif`; ctx.textAlign = 'center'
      ctx.fillText(eff.emoji, bx + badgeW * 0.3, H - badgeH / 2 - 8 * sy + 5 * sy)
      ctx.font = `${Math.round(10 * sx)}px monospace`; ctx.fillStyle = '#fff'
      ctx.fillText(secs + 's', bx + badgeW * 0.7, H - badgeH / 2 - 8 * sy + 5 * sy)
      bx += badgeW + pad
    }
  }

  // ── Drawing hint ─────────────────────────────────────────────────────────────
  if (!isDrawing.value && (!state.activePowerUpEffects || state.activePowerUpEffects.length === 0)) {
    ctx.font = `${Math.round(11 * sx)}px monospace`
    ctx.fillStyle = 'rgba(255,255,255,0.25)'
    ctx.textAlign = 'center'
    ctx.fillText('Hold & drag to draw a barrier line', W / 2, H - 8)
  }

  // ── Pause overlay ─────────────────────────────────────────────────────────────
  if (state.paused) {
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.fillRect(0, 0, W, H)
    ctx.fillStyle = '#ffffff'
    ctx.font = `bold ${Math.round(48 * sx)}px monospace`
    ctx.textAlign = 'center'
    ctx.fillText('PAUSED', W / 2, H / 2)
  }
}

// ── Coordinate helpers ────────────────────────────────────────────────────────
//
// CRITICAL: use getBoundingClientRect().width/.height (CSS-rendered dimensions)
// NOT canvas.width/canvas.height (pixel-buffer dimensions).
//
// When CSS scales the canvas (max-width:100% makes it narrower than 800px),
// canvas.width stays 800 but r.width shrinks. Using canvas.width for scaling
// would map mouse coordinates incorrectly, causing the paddle to jump to the
// wrong position and effectively making it uncontrollable.

function getCanvasScale(): { sx: number; sy: number; rect: DOMRect } | null {
  const canvas = canvasRef.value
  const state  = gameStateRef.value
  if (!canvas || !state) return null
  const rect = canvas.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return null
  return {
    sx: state.canvasWidth  / rect.width,
    sy: state.canvasHeight / rect.height,
    rect,
  }
}

function toGamePt(e: MouseEvent): Point | null {
  const sc = getCanvasScale()
  if (!sc) return null
  return {
    x: (e.clientX - sc.rect.left) * sc.sx,
    y: (e.clientY - sc.rect.top)  * sc.sy,
  }
}

// ── Event handlers ────────────────────────────────────────────────────────────

function onMouseMove(e: MouseEvent) {
  const sc = getCanvasScale()
  if (!sc) return
  // Send the mouse y in game space to move the right paddle
  const gameY = (e.clientY - sc.rect.top) * sc.sy
  if (!props.timeshiftActive) emit('paddleMove', gameY)
  // If drawing, send line point
  if (isDrawing.value) {
    const pt = toGamePt(e)
    if (pt) send('UPDATE_LINE', { x: pt.x, y: pt.y })
  }
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  const pt = toGamePt(e)
  if (!pt) return
  isDrawing.value = true
  send('START_LINE', { x: pt.x, y: pt.y })
}

function onMouseUp() {
  if (!isDrawing.value) return
  isDrawing.value = false
  send('FINISH_LINE')
}

function onMouseLeave() {
  if (!isDrawing.value) return
  isDrawing.value = false
  send('FINISH_LINE')
}
</script>

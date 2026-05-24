<template>
  <canvas
    ref="canvasRef"
    :style="{ cursor: cursorStyle }"
    @mousemove="onMouseMove"
    @mousedown="onMouseDown"
    @mouseup="finishDrawing"
    @mouseleave="finishDrawing"
    @contextmenu.prevent="onContextMenu"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { gameStateRef, useGameSocket } from '../composables/useGameSocket'
import type { GameState, Line, Point } from '../types/game'

const emit = defineEmits<{ paddleMove: [y: number] }>()
const props = defineProps<{ timeshiftActive?: boolean; eraserMode?: boolean }>()
const { eraseLine, send } = useGameSocket()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const isDrawing = ref(false)

const PADDLE_WIDTH = 20
const ERASE_HIT_RADIUS = 12
const PUCK_LERP = 0.35
const PUCK_SNAP_DISTANCE = 60
const DEFAULT_PUCK_X = 400
const DEFAULT_PUCK_Y = 300

let smoothPuckX = DEFAULT_PUCK_X
let smoothPuckY = DEFAULT_PUCK_Y
let animationFrameId = 0

const cursorStyle = computed(() => {
  if (props.eraserMode) return 'crosshair'
  return isDrawing.value ? 'crosshair' : 'none'
})

onMounted(() => {
  animationFrameId = requestAnimationFrame(renderLoop)
})

onUnmounted(() => {
  cancelAnimationFrame(animationFrameId)
})

function renderLoop() {
  const state = gameStateRef.value
  if (state) {
    updateSmoothPuck(state)
    draw(state)
  }
  animationFrameId = requestAnimationFrame(renderLoop)
}

function updateSmoothPuck(state: GameState) {
  const dx = state.puck.x - smoothPuckX
  const dy = state.puck.y - smoothPuckY
  if (Math.abs(dx) > PUCK_SNAP_DISTANCE || Math.abs(dy) > PUCK_SNAP_DISTANCE) {
    smoothPuckX = state.puck.x
    smoothPuckY = state.puck.y
    return
  }

  smoothPuckX += dx * PUCK_LERP
  smoothPuckY += dy * PUCK_LERP
}

function draw(state: GameState) {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx || !state.puck || typeof state.canvasWidth !== 'number') return

  resizeCanvasToGameState(canvas, state)

  const scale = getPixelScale(canvas, state)
  drawBackground(ctx, canvas)
  drawCenterLine(ctx, canvas)
  drawBarrierLines(ctx, state.lines, scale)
  drawPowerUps(ctx, state, scale)
  drawPaddles(ctx, state, canvas, scale)
  drawPuck(ctx, state, scale)
  drawScore(ctx, state, canvas, scale)
  drawActivePowerUpEffects(ctx, state, canvas, scale)
  drawHint(ctx, state, canvas, scale)
  drawPauseOverlay(ctx, state, canvas, scale)
}

function resizeCanvasToGameState(canvas: HTMLCanvasElement, state: GameState) {
  if (canvas.width === state.canvasWidth && canvas.height === state.canvasHeight) return
  canvas.width = state.canvasWidth
  canvas.height = state.canvasHeight
}

function getPixelScale(canvas: HTMLCanvasElement, state: GameState): Point {
  return {
    x: canvas.width / state.canvasWidth,
    y: canvas.height / state.canvasHeight,
  }
}

function drawBackground(ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement) {
  ctx.fillStyle = '#1a1a2e'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
}

function drawCenterLine(ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement) {
  ctx.save()
  ctx.setLineDash([10, 10])
  ctx.strokeStyle = 'rgba(255,255,255,0.2)'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(canvas.width / 2, 0)
  ctx.lineTo(canvas.width / 2, canvas.height)
  ctx.stroke()
  ctx.restore()
}

function drawBarrierLines(ctx: CanvasRenderingContext2D, lines: Line[], scale: Point) {
  if (!lines.length) return

  ctx.save()
  ctx.strokeStyle = '#f0a500'
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  for (const line of lines) {
    const points = drawableLinePoints(line)
    if (points.length < 2) continue

    ctx.lineWidth = (line.width ?? 5) * Math.min(scale.x, scale.y)
    ctx.beginPath()
    ctx.moveTo(points[0].x * scale.x, points[0].y * scale.y)
    for (let index = 1; index < points.length; index++) {
      ctx.lineTo(points[index].x * scale.x, points[index].y * scale.y)
    }
    ctx.stroke()
  }

  ctx.restore()
}

function drawPowerUps(ctx: CanvasRenderingContext2D, state: GameState, scale: Point) {
  if (!state.powerUps.length) return

  const time = performance.now() / 600
  for (const powerUp of state.powerUps) {
    const radius = powerUp.radius * Math.min(scale.x, scale.y) * (1 + 0.12 * Math.sin(time + powerUp.x))
    const x = powerUp.x * scale.x
    const y = powerUp.y * scale.y

    ctx.save()
    ctx.shadowBlur = 18
    ctx.shadowColor = powerUp.color
    ctx.beginPath()
    ctx.arc(x, y, radius, 0, Math.PI * 2)
    ctx.fillStyle = `${powerUp.color}44`
    ctx.fill()
    ctx.strokeStyle = powerUp.color
    ctx.lineWidth = 2 * Math.min(scale.x, scale.y)
    ctx.stroke()
    ctx.restore()

    ctx.font = `${Math.round(radius * 1.3)}px serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(powerUp.emoji, x, y)
    ctx.textBaseline = 'alphabetic'
  }
}

function drawPaddles(ctx: CanvasRenderingContext2D, state: GameState, canvas: HTMLCanvasElement, scale: Point) {
  ctx.fillStyle = '#e94560'
  ctx.fillRect(0, state.paddle1Y * scale.y, PADDLE_WIDTH * scale.x, state.paddleHeight * scale.y)
  ctx.fillStyle = '#4ecca3'
  ctx.fillRect(canvas.width - PADDLE_WIDTH * scale.x, state.paddle2Y * scale.y, PADDLE_WIDTH * scale.x, state.paddleHeight * scale.y)
}

function drawPuck(ctx: CanvasRenderingContext2D, state: GameState, scale: Point) {
  ctx.beginPath()
  ctx.arc(
    smoothPuckX * scale.x,
    smoothPuckY * scale.y,
    state.puck.radius * Math.min(scale.x, scale.y),
    0,
    Math.PI * 2,
  )
  ctx.fillStyle = '#ffffff'
  ctx.fill()
}

function drawScore(ctx: CanvasRenderingContext2D, state: GameState, canvas: HTMLCanvasElement, scale: Point) {
  ctx.font = `bold ${Math.round(32 * scale.x)}px monospace`
  ctx.fillStyle = 'rgba(255,255,255,0.7)'
  ctx.textAlign = 'center'
  ctx.fillText(String(state.score.playerA), canvas.width * 0.25, 50 * scale.y)
  ctx.fillText(String(state.score.playerB), canvas.width * 0.75, 50 * scale.y)
}

function drawActivePowerUpEffects(ctx: CanvasRenderingContext2D, state: GameState, canvas: HTMLCanvasElement, scale: Point) {
  if (!state.activePowerUpEffects.length) return

  const badgeWidth = 54 * scale.x
  const badgeHeight = 28 * scale.y
  const badgeGap = 6 * scale.x
  let x = canvas.width / 2 - (state.activePowerUpEffects.length * (badgeWidth + badgeGap)) / 2

  for (const effect of state.activePowerUpEffects) {
    const y = canvas.height - badgeHeight - 8 * scale.y
    ctx.fillStyle = 'rgba(0,0,0,0.55)'
    ctx.beginPath()
    ctx.roundRect(x, y, badgeWidth, badgeHeight, 6)
    ctx.fill()
    ctx.font = `${Math.round(14 * scale.x)}px serif`
    ctx.textAlign = 'center'
    ctx.fillText(effect.emoji, x + badgeWidth * 0.3, y + badgeHeight / 2 + 5 * scale.y)
    ctx.font = `${Math.round(10 * scale.x)}px monospace`
    ctx.fillStyle = '#fff'
    ctx.fillText(`${(effect.remainingMs / 1000).toFixed(1)}s`, x + badgeWidth * 0.7, y + badgeHeight / 2 + 5 * scale.y)
    x += badgeWidth + badgeGap
  }
}

function drawHint(ctx: CanvasRenderingContext2D, state: GameState, canvas: HTMLCanvasElement, scale: Point) {
  if (isDrawing.value || state.activePowerUpEffects.length > 0) return
  ctx.font = `${Math.round(11 * scale.x)}px monospace`
  ctx.fillStyle = 'rgba(255,255,255,0.25)'
  ctx.textAlign = 'center'
  ctx.fillText('Hold & drag to draw a barrier line', canvas.width / 2, canvas.height - 8)
}

function drawPauseOverlay(ctx: CanvasRenderingContext2D, state: GameState, canvas: HTMLCanvasElement, scale: Point) {
  if (!state.paused) return
  ctx.fillStyle = 'rgba(0,0,0,0.55)'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.fillStyle = '#ffffff'
  ctx.font = `bold ${Math.round(48 * scale.x)}px monospace`
  ctx.textAlign = 'center'
  ctx.fillText('PAUSED', canvas.width / 2, canvas.height / 2)
}

function getCanvasCoordinateSpace(): { scale: Point; rect: DOMRect } | null {
  const canvas = canvasRef.value
  const state = gameStateRef.value
  if (!canvas || !state) return null

  const rect = canvas.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return null

  return {
    scale: {
      x: state.canvasWidth / rect.width,
      y: state.canvasHeight / rect.height,
    },
    rect,
  }
}

function eventToGamePoint(event: MouseEvent): Point | null {
  const coordinateSpace = getCanvasCoordinateSpace()
  if (!coordinateSpace) return null

  return {
    x: (event.clientX - coordinateSpace.rect.left) * coordinateSpace.scale.x,
    y: (event.clientY - coordinateSpace.rect.top) * coordinateSpace.scale.y,
  }
}

function onMouseMove(event: MouseEvent) {
  const point = eventToGamePoint(event)
  if (!point) return

  if (!props.timeshiftActive) emit('paddleMove', point.y)
  if (isDrawing.value) sendLinePoint('UPDATE_LINE', point)
}

function onMouseDown(event: MouseEvent) {
  if (event.button !== 0) return

  const point = eventToGamePoint(event)
  if (!point) return

  if (props.eraserMode) {
    eraseLineAt(point)
    return
  }

  isDrawing.value = true
  sendLinePoint('START_LINE', point)
}

function finishDrawing() {
  if (!isDrawing.value) return
  isDrawing.value = false
  send('FINISH_LINE')
}

function onContextMenu(event: MouseEvent) {
  const point = eventToGamePoint(event)
  if (!point) return

  finishDrawing()
  eraseLineAt(point)
}

function sendLinePoint(type: 'START_LINE' | 'UPDATE_LINE', point: Point) {
  send(type, { x: point.x, y: point.y })
}

function eraseLineAt(point: Point) {
  const state = gameStateRef.value
  if (!state) return

  const lineId = nearestLineId(point, state.lines)
  if (lineId) eraseLine(lineId)
}

function nearestLineId(point: Point, lines: Line[]): string | null {
  let nearestId: string | null = null
  let nearestDistance = ERASE_HIT_RADIUS

  for (const line of lines) {
    const points = drawableLinePoints(line)
    const distance = nearestDistanceToLine(point, points)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearestId = line.id
    }
  }

  return nearestId
}

function nearestDistanceToLine(point: Point, points: Point[]): number {
  if (points.length === 0) return Number.POSITIVE_INFINITY
  if (points.length === 1) return Math.hypot(points[0].x - point.x, points[0].y - point.y)

  let nearestDistance = Number.POSITIVE_INFINITY
  for (let index = 0; index < points.length - 1; index++) {
    nearestDistance = Math.min(nearestDistance, pointToSegmentDistance(point, points[index], points[index + 1]))
  }
  return nearestDistance
}

function drawableLinePoints(line: Line): Point[] {
  return line.flattenedPoints && line.flattenedPoints.length > 1 ? line.flattenedPoints : line.controlPoints
}

function pointToSegmentDistance(point: Point, start: Point, end: Point): number {
  const dx = end.x - start.x
  const dy = end.y - start.y
  const lengthSquared = dx * dx + dy * dy
  if (lengthSquared < 1e-9) return Math.hypot(point.x - start.x, point.y - start.y)

  const t = Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared))
  const closestX = start.x + t * dx
  const closestY = start.y + t * dy
  return Math.hypot(point.x - closestX, point.y - closestY)
}
</script>

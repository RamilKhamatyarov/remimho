<template>
  <canvas ref="preview" width="320" height="180" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { PassingCombination } from '../combinations'
import type { PaddleSide } from '../types/game'

const props = defineProps<{ layout: PassingCombination; side: PaddleSide }>()
const preview = ref<HTMLCanvasElement | null>(null)

function draw() {
  const ctx = preview.value?.getContext('2d')
  if (!ctx) return
  ctx.fillStyle = '#171a20'
  ctx.fillRect(0, 0, 320, 180)
  ctx.setLineDash([3, 5])
  ctx.strokeStyle = '#454a53'
  ctx.beginPath()
  ctx.moveTo(160, 0)
  ctx.lineTo(160, 180)
  ctx.stroke()
  ctx.setLineDash([])
  for (const [side, x] of [['A', 4], ['B', 310]] as const) {
    ctx.fillStyle = side === props.side ? '#64d9b0' : '#ef7c8d'
    ctx.fillRect(x, 75, 6, 30)
  }
  ctx.strokeStyle = '#64d9b0'
  ctx.lineWidth = 3
  ctx.lineCap = 'round'
  for (const line of props.layout.lines) {
    ctx.beginPath()
    line.points.forEach((point, index) => {
      const x = (props.side === 'A' ? point.x : 1 - point.x) * 320
      if (index === 0) ctx.moveTo(x, point.y * 180)
      else ctx.lineTo(x, point.y * 180)
    })
    ctx.stroke()
  }
}

onMounted(draw)
watch(() => [props.layout, props.side], draw)
</script>

<style scoped>
canvas { width: 100%; aspect-ratio: 16 / 9; height: auto; border: 0; border-radius: 4px; cursor: inherit; }
</style>

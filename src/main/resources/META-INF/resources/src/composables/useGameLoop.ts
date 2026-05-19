import type { Ref } from 'vue';
import type { GameState } from '../types/game';

type Delta = Record<string, unknown>;

class Channel<T> {
  private readonly queue: T[] = [];

  constructor(private readonly capacity = 6) {}

  send(value: T): void {
    if (this.queue.length >= this.capacity) this.queue.shift();
    this.queue.push(value);
  }

  receive(): T | undefined {
    return this.queue.shift();
  }

  get size(): number {
    return this.queue.length;
  }
}

let enabled = false;
let rafId: number | null = null;
let stateRef: Ref<GameState | null> | null = null;
let mergeDelta: ((delta: Delta, base?: GameState | null) => GameState) | null = null;
const channel = new Channel<Delta>();

export function configureGameLoop(options: {
  clientInterpolation: boolean;
  gameState: Ref<GameState | null>;
  mergeDelta: (delta: Delta, base?: GameState | null) => GameState;
}): void {
  enabled = options.clientInterpolation;
  stateRef = options.gameState;
  mergeDelta = options.mergeDelta;
  if (enabled) startLoop();
}

export function enqueueDelta(delta: Delta): boolean {
  if (!enabled) return false;
  channel.send(delta);
  startLoop();
  return true;
}

function startLoop(): void {
  if (rafId !== null) return;
  rafId = requestAnimationFrame(loop);
}

function loop(): void {
  rafId = null;
  if (!stateRef || !mergeDelta) return;

  let target = stateRef.value;
  while (channel.size > 0) {
    const delta = channel.receive();
    if (delta) target = mergeDelta(delta, target);
  }

  if (target) {
    stateRef.value = interpolateState(stateRef.value ?? target, target, 0.35);
  }

  if (channel.size > 0) startLoop();
}

function interpolateState(
  from: GameState,
  to: GameState,
  alpha: number,
): GameState {
  return {
    ...to,
    puck: {
      ...to.puck,
      x: lerp(from.puck.x, to.puck.x, alpha),
      y: lerp(from.puck.y, to.puck.y, alpha),
      vx: to.puck.vx,
      vy: to.puck.vy,
    },
    paddle1Y: lerp(from.paddle1Y, to.paddle1Y, alpha),
    paddle2Y: lerp(from.paddle2Y, to.paddle2Y, alpha),
  };
}

function lerp(
  a: number,
  b: number,
  alpha: number,
): number {
  return a + (b - a) * alpha;
}

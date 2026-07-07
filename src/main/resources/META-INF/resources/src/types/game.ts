export interface Puck {
  x: number; y: number; vx: number; vy: number; radius: number
}
export interface Score { playerA: number; playerB: number }
export interface Point { x: number; y: number }
export interface Line {
  id: string
  controlPoints: Point[]
  flattenedPoints: Point[] | null
  width: number
}
export interface PowerUpDTO {
  x: number; y: number; radius: number
  type: string; emoji: string; color: string
}
export interface ActivePowerUpEffectDTO {
  type: string; emoji: string; remainingMs: number
}
export interface GameState {
  puck: Puck; score: Score
  canvasWidth: number; canvasHeight: number
  paddleHeight: number; paddle1Y: number; paddle2Y: number
  paused: boolean
  lines: Line[]
  powerUps: PowerUpDTO[]
  activePowerUpEffects: ActivePowerUpEffectDTO[]
}

export type TurboStatus = 'charging' | 'ready' | 'active' | 'cooldown'
export type PaddleSide = 'A' | 'B'

export interface TurboSideState {
  side: PaddleSide
  charge: number
  status: TurboStatus
  activeMs: number
  cooldownMs: number
}

export interface TurboHudState {
  states: TurboSideState[]
}

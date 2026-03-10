export interface Puck {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
}

export interface Score {
  playerA: number
  playerB: number
}

export interface Point {
  x: number
  y: number
}

export interface Line {
  controlPoints: Point[]
  flattenedPoints: Point[] | null
  width: number
}

export interface GameState {
  puck: Puck
  score: Score
  canvasWidth: number
  canvasHeight: number
  paddleHeight: number
  paddle1Y: number
  paddle2Y: number
  paused: boolean
  lines: Line[]
}

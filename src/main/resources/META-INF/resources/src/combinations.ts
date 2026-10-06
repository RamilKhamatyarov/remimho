import type { Line, PaddleSide, Point } from './types/game'

export interface CombinationLine { points: Point[]; width: number }
export interface PassingCombination {
  version: 1
  id: string
  name: string
  lines: CombinationLine[]
}

export const STORAGE_KEY = 'remimho.passing-combinations.v1'
export const MAX_SAVED = 20

/** Positions short deflectors on a reference route; actual play still uses server physics. */
function fromRoute(id: string, name: string, route: number[][]): PassingCombination {
  const unit = (x: number, y: number): Point => {
    const length = Math.hypot(x, y)
    return { x: x / length, y: y / length }
  }
  const lines = route.slice(1, -1).map(([x, y], index) => {
    const before = route[index]
    const after = route[index + 2]
    const incoming = unit(x - before[0], y - before[1])
    const outgoing = unit(after[0] - x, after[1] - y)
    const normal = unit(outgoing.x - incoming.x, outgoing.y - incoming.y)
    const cx = x - normal.x * 10
    const cy = y - normal.y * 10
    return {
      points: [-1, 1].map(sign => ({
        x: (cx - sign * normal.y * 40) / 800,
        y: (cy + sign * normal.x * 40) / 600,
      })),
      width: 5,
    }
  })
  return { version: 1, id, name, lines }
}

export const DEFAULT_COMBINATIONS: PassingCombination[] = [
  fromRoute('tic-tac-toe', 'Tic-Tac-Toe', [[30, 300], [210, 300], [390, 150], [590, 450], [790, 90]]),
  fromRoute('tiki-taka', 'Tiki-Taka', [[30, 350], [200, 350], [350, 250], [480, 350], [640, 240], [790, 90]]),
  fromRoute('switch-play', 'Switch Play', [[30, 180], [230, 180], [410, 440], [620, 440], [790, 120]]),
  fromRoute('triangle', 'Triangle', [[30, 430], [250, 430], [440, 190], [580, 400], [790, 510]]),
  fromRoute('cutback', 'Cutback', [[30, 230], [350, 230], [220, 400], [580, 480], [790, 140]]),
]

/** Reconstructs a bounded document instead of trusting imported IDs or object properties. */
export function parseCombination(value: unknown): PassingCombination {
  if (!value || typeof value !== 'object') throw new Error('Invalid combination file')
  const data = value as Record<string, unknown>
  if (data.version !== 1) throw new Error('Unsupported combination version')
  if (typeof data.id !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(data.id)) {
    throw new Error('Invalid combination ID')
  }
  if (typeof data.name !== 'string' || !data.name.trim() || data.name.trim().length > 60) {
    throw new Error('Name must contain 1 to 60 characters')
  }
  if (!Array.isArray(data.lines) || data.lines.length < 1 || data.lines.length > 32) {
    throw new Error('A combination needs 1 to 32 lines')
  }
  let pointCount = 0
  const lines = data.lines.map((raw: unknown): CombinationLine => {
    if (!raw || typeof raw !== 'object') throw new Error('Invalid line')
    const line = raw as Record<string, unknown>
    if (typeof line.width !== 'number' || !Number.isFinite(line.width) || line.width < 1 || line.width > 12) {
      throw new Error('Line width must be between 1 and 12')
    }
    if (!Array.isArray(line.points) || line.points.length < 2 || line.points.length > 256) {
      throw new Error('Each line needs 2 to 256 points')
    }
    pointCount += line.points.length
    if (pointCount > 4096) throw new Error('Too many combination points')
    const points = line.points.map((rawPoint: unknown): Point => {
      if (!rawPoint || typeof rawPoint !== 'object') throw new Error('Invalid point')
      const point = rawPoint as Record<string, unknown>
      const { x, y } = point
      if (typeof x !== 'number' || typeof y !== 'number' || !Number.isFinite(x) || !Number.isFinite(y)
        || x < 0 || x > 1 || y < 0 || y > 1) throw new Error('Coordinates must be between 0 and 1')
      return { x, y }
    })
    if (points.every(point => point.x === points[0].x && point.y === points[0].y)) {
      throw new Error('A line must have nonzero length')
    }
    return { points, width: line.width }
  })
  return { version: 1, id: data.id, name: data.name.trim(), lines }
}

export function captureCombination(
  lines: Line[],
  side: PaddleSide,
  width: number,
  height: number,
  name: string,
  id: string,
): PassingCombination {
  return parseCombination({
    version: 1, id, name,
    lines: lines.filter(line => line.ownerSide === side).map(line => ({
      width: line.width,
      points: (line.flattenedPoints ?? line.controlPoints).map(point => ({
        x: side === 'A' ? point.x / width : 1 - point.x / width,
        y: point.y / height,
      })),
    })),
  })
}

export function readSavedCombinations(storage: Pick<Storage, 'getItem'>): PassingCombination[] {
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) return []
  const data: unknown = JSON.parse(raw)
  if (!Array.isArray(data) || data.length > MAX_SAVED) throw new Error('Invalid saved combinations')
  return data.map(parseCombination)
}

export function saveCombinations(storage: Pick<Storage, 'setItem'>, layouts: PassingCombination[]): void {
  if (layouts.length > MAX_SAVED) throw new Error(`At most ${MAX_SAVED} custom combinations can be saved`)
  storage.setItem(STORAGE_KEY, JSON.stringify(layouts.map(parseCombination)))
}

interface Observation {
  atMs: number
  kind: string
  data: unknown
}

interface Entry {
  observation: Observation
  bytes: number
}

/** Fixed-capacity ring of detached objects; JSON encoding happens only during download. */
export class CaptureBuffer {
  enabled = false
  private entries: (Entry | undefined)[]
  private head = 0
  private count = 0
  private bytes = 0
  private limitedUntilMs = -Infinity
  private lastRenderedMs = -Infinity

  constructor(
    private readonly clock: () => number = () => performance.now(),
    private readonly maxEntries = 4096,
    private readonly maxBytes = 4 * 1024 * 1024,
  ) {
    if (maxEntries <= 0 || maxBytes <= 0) throw new Error('Invalid capture capacity')
    this.entries = new Array(maxEntries)
  }

  configure(enabled: boolean): void {
    this.enabled = enabled
    if (!enabled) this.clear()
  }

  record(kind: string, data: unknown): void {
    if (!this.enabled) return
    const atMs = this.clock()
    if (kind === 'rendered' && atMs - this.lastRenderedMs < 1000 / 60) return
    if (kind === 'rendered') this.lastRenderedMs = atMs
    this.prune(atMs)
    try {
      const budget = { bytes: 0, limit: Math.min(this.maxBytes, 64 * 1024) }
      const detached = copyEvidence(data, budget)
      const bytes = budget.bytes + kind.length * 3 + 32
      if (bytes > this.maxBytes) {
        this.limitedUntilMs = atMs + 15000
        return
      }
      while (this.count >= this.maxEntries || this.bytes + bytes > this.maxBytes) {
        this.removeFirst()
        this.limitedUntilMs = atMs + 15000
      }
      const index = (this.head + this.count) % this.maxEntries
      this.entries[index] = { observation: { atMs, kind, data: detached }, bytes }
      this.count++
      this.bytes += bytes
    } catch {
      // Diagnostics must never prevent drawing, receiving frames, or sending inputs.
      this.limitedUntilMs = atMs + 15000
    }
  }

  snapshot() {
    const atMs = this.clock()
    this.prune(atMs)
    const observations = Array.from({ length: this.count }, (_, offset) =>
      this.entries[(this.head + offset) % this.maxEntries]!.observation)
    return {
      atMs,
      capacityLimited: atMs < this.limitedUntilMs,
      entries: structuredClone(observations),
    }
  }

  clear(): void {
    this.entries = new Array(this.maxEntries)
    this.head = 0
    this.count = 0
    this.bytes = 0
    this.limitedUntilMs = -Infinity
    this.lastRenderedMs = -Infinity
  }

  private prune(now: number): void {
    while (this.count && now - this.entries[this.head]!.observation.atMs > 15000) this.removeFirst()
  }

  private removeFirst(): void {
    this.bytes -= this.entries[this.head]!.bytes
    this.entries[this.head] = undefined
    this.head = (this.head + 1) % this.maxEntries
    this.count--
  }
}

/** Bounded cloning with a conservative payload estimate, without allocating UTF-8 or JSON strings. */
function copyEvidence(value: unknown, budget: { bytes: number; limit: number }, depth = 0): unknown {
  budget.bytes += typeof value === 'string' ? value.length * 3 + 16 : 16
  if (budget.bytes > budget.limit || depth > 20) throw new Error('Capture entry too large')
  if (value === null || typeof value !== 'object') return value
  if (Array.isArray(value)) return value.map(item => copyEvidence(item, budget, depth + 1))
  const result: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(value)) {
    budget.bytes += key.length * 3 + 16
    result[key] = copyEvidence(item, budget, depth + 1)
  }
  return result
}

export const captureBuffer = new CaptureBuffer()

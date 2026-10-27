import { strToU8, zip } from 'fflate'
import { captureBuffer } from './captureBuffer'

interface ServerSegment {
  firstSequence: number
  lastSequence: number
  reason: string
  replayBase64: string
  expectedSnapshotBase64: string
}

interface ServerEvidence {
  schemaVersion: number
  capacityLimited: boolean
  segments: ServerSegment[]
  [key: string]: unknown
}

/** No browser evidence is posted to the server; it is packaged locally for manual sharing. */
export async function captureDossier(
  roomId: string,
  side: string,
  screenshot: Promise<Blob | null>,
  view: Record<string, unknown>,
  token: string | null,
  fetcher: typeof fetch = fetch,
): Promise<{ blob: Blob; partial: boolean }> {
  const capturedAt = new Date().toISOString()
  const browser = captureBuffer.snapshot()
  const files: Record<string, Uint8Array> = {
    'browser.json': strToU8(JSON.stringify({ roomId, side, view, ...browser })),
  }
  let serverStatus = 'unavailable'
  let screenshotStatus = 'unavailable'
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 5000)

  const image = screenshot.catch(() => null)
  try {
    const server = await fetchServerEvidence(roomId, token, controller.signal, fetcher)
    if (server) {
      const segments = server.segments.map((segment, index) => {
        const name = `segment-${index + 1}`
        files[`${name}.replay`] = decodeBase64(segment.replayBase64)
        files[`${name}.expected.snapshot`] = decodeBase64(segment.expectedSnapshotBase64)
        const { replayBase64: _replay, expectedSnapshotBase64: _snapshot, ...metadata } = segment
        return { ...metadata, file: `${name}.replay` }
      })
      files['server.json'] = strToU8(JSON.stringify({ ...server, segments }))
      serverStatus = server.segments.length === 0 ? 'empty' : server.capacityLimited ? 'capacity-limited' : 'available'
    }
  } catch {
    // A disconnected client still owns useful rendered-frame and input evidence.
  } finally {
    clearTimeout(timeout)
  }
  const serverRequestEndMs = performance.now()
  const png = await image
  if (png) {
    files['canvas.png'] = new Uint8Array(await png.arrayBuffer())
    screenshotStatus = 'available'
  }
  const partial = serverStatus !== 'available' || screenshotStatus !== 'available' || browser.capacityLimited
  files['manifest.json'] = strToU8(JSON.stringify({
    schemaVersion: 2, capturedAt, roomId, side, partial, serverStatus, screenshotStatus,
    browserCapacityLimited: browser.capacityLimited,
    serverRequestStartMs: browser.atMs, serverRequestEndMs,
    clocks: 'Browser atMs and server frame ageMs are observation clocks, not synchronized logical time.',
    replay: 'Use the same application build. Each segment has its own baseline and expected FullGameSnapshot.',
    privacy: 'Gameplay evidence only. Review before sharing; no automatic upload.',
  }, null, 2))
  const bytes = await new Promise<Uint8Array>((resolve, reject) => {
    zip(files, { level: 1 }, (error, result) => error ? reject(error) : resolve(result))
  })
  return { blob: new Blob([new Uint8Array(bytes)], { type: 'application/zip' }), partial }
}

async function fetchServerEvidence(
  roomId: string,
  token: string | null,
  signal: AbortSignal,
  fetcher: typeof fetch,
): Promise<ServerEvidence | null> {
  if (!token) return null
  const response = await fetcher(`/api/v1/capture/${encodeURIComponent(roomId)}`, {
    signal,
    cache: 'no-store',
    headers: { Accept: 'application/json', 'X-Capture-Token': token },
  })
  if (!response.ok) return null
  const server = await response.json() as ServerEvidence | null
  if (server?.schemaVersion !== 2 || !Array.isArray(server.segments)) return null
  return server
}

function decodeBase64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0))
}

// src/api/workshop.ts

// ── Types (mirror WorkshopResource.kt) ───────────────────────────────────────

export enum ContentType {
  LEVEL = 'LEVEL',
  SKIN = 'SKIN',
  THEME = 'THEME',
  POWERUP_SET = 'POWERUP_SET',
  GAME_MODE = 'GAME_MODE',
}

export interface WorkshopContentDTO {
  type: ContentType;
  data: unknown;
  metadata: Record<string, string>;
}

// ── Result pattern (no external deps) ────────────────────────────────────────

export type Ok<T> = { ok: T; error?: never };
export type Err = { ok?: never; error: string };
export type Result<T> = Ok<T> | Err;

function ok<T>(value: T): Ok<T> {
  return { ok: value };
}
function err(message: string): Err {
  return { error: message };
}

// ── API base ──────────────────────────────────────────────────────────────────

const BASE = '/api/v1/workshop';

async function post<T>(path: string, body: unknown): Promise<Result<T>> {
  try {
    const res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const text = await res.text();
      return err(`${res.status}: ${text}`);
    }
    return ok((await res.json()) as T);
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e));
  }
}

async function get<T>(path: string, params?: Record<string, string>): Promise<Result<T>> {
  try {
    const url = new URL(`${BASE}${path}`, window.location.origin);
    if (params) Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
    const res = await fetch(url.toString(), { headers: { Accept: 'application/json' } });
    if (!res.ok) {
      const text = await res.text();
      return err(`${res.status}: ${text}`);
    }
    return ok((await res.json()) as T);
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e));
  }
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Publish a workshop content item.
 * Maps to: POST /api/v1/workshop/content
 */
export async function publishContent(
  type: ContentType,
  data: unknown,
  metadata: Record<string, string>,
): Promise<Result<WorkshopContentDTO>> {
  const dto: WorkshopContentDTO = { type, data, metadata };
  return post<WorkshopContentDTO>('/content', dto);
}

/**
 * Search published workshop content by type.
 * Maps to: GET /api/v1/workshop/content?type=LEVEL&query=foo
 */
export async function searchContent(
  type: ContentType,
  query?: string,
): Promise<Result<WorkshopContentDTO[]>> {
  const params: Record<string, string> = { type };
  if (query) params['query'] = query;
  return get<WorkshopContentDTO[]>('/content', params);
}

// ── Vue composable wrapper ────────────────────────────────────────────────────

export function useWorkshopApi() {
  return { publishContent, searchContent, ContentType };
}

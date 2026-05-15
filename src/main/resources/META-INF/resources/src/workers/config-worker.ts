type WorkerRequest = {
  id: string;
  source: string;
  format: string;
};

type WorkerResponse = {
  id: string;
  ok: boolean;
  parsed?: unknown;
  compile?: unknown;
  preview?: unknown;
  error?: string;
};

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  const { id, source, format } = event.data;
  try {
    const parsed = parseConfig(source, format);
    const compileRes = await post('/api/v1/workshop/compile', { source, format });
    if (!compileRes.ok) throw new Error(JSON.stringify(compileRes.body));
    const compiled = compileRes.body as { config?: unknown };
    const previewRes = await post('/api/v1/workshop/preview', compiled.config ?? parsed);
    if (!previewRes.ok) throw new Error(JSON.stringify(previewRes.body));
    respond({ id, ok: true, parsed, compile: compiled, preview: previewRes.body });
  } catch (e) {
    respond({ id, ok: false, error: e instanceof Error ? e.message : String(e) });
  }
};

async function post(path: string, body: unknown): Promise<{ ok: boolean; body: unknown }> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { ok: res.ok, body: text ? JSON.parse(text) : null };
}

function parseConfig(source: string, format: string): unknown {
  if (format === 'json') return JSON.parse(source);
  const flat = format === 'toml' ? parseToml(source) : parseYaml(source);
  return inflate(flat);
}

function parseYaml(source: string): Record<string, string> {
  const out: Record<string, string> = {};
  const stack: Array<{ indent: number; key: string }> = [];
  for (const raw of source.split(/\r?\n/)) {
    const clean = raw.split('#')[0];
    if (!clean.trim()) continue;
    const indent = clean.match(/^ */)?.[0].length ?? 0;
    const line = clean.trim();
    const idx = line.indexOf(':');
    if (idx < 0) continue;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim().replace(/^"|"$/g, '');
    while (stack.length && stack[stack.length - 1].indent >= indent) stack.pop();
    if (!value) stack.push({ indent, key });
    else out[[...stack.map(s => s.key), key].join('.')] = value;
  }
  return out;
}

function parseToml(source: string): Record<string, string> {
  const out: Record<string, string> = {};
  let section = '';
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.split('#')[0].trim();
    if (!line) continue;
    if (line.startsWith('[') && line.endsWith(']')) {
      section = line.slice(1, -1).trim();
      continue;
    }
    const idx = line.indexOf('=');
    if (idx < 0) continue;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim().replace(/^"|"$/g, '');
    out[[section, key].filter(Boolean).join('.')] = value;
  }
  return out;
}

function inflate(flat: Record<string, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [path, value] of Object.entries(flat)) {
    const parts = path.split('.');
    let cursor = out;
    for (const part of parts.slice(0, -1)) {
      cursor[part] = (cursor[part] as Record<string, unknown>) ?? {};
      cursor = cursor[part] as Record<string, unknown>;
    }
    cursor[parts[parts.length - 1]] = coerce(value);
  }
  return out;
}

function coerce(value: string): string | number | boolean {
  if (value === 'true') return true;
  if (value === 'false') return false;
  const numeric = Number(value);
  return Number.isFinite(numeric) && value.trim() !== '' ? numeric : value;
}

function respond(response: WorkerResponse): void {
  self.postMessage(response);
}

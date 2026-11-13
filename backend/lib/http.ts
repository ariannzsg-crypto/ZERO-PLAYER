/**
 * Shared helpers for the ZERO PLAYER online-music backend.
 *
 * Everything here runs on the Vercel Edge runtime, so it sticks to Web
 * standard `Request`/`Response` and has **zero runtime dependencies**.
 */

export const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
} as const;

/** A safe error the API is allowed to show to a client. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export function json(data: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...headers },
  })
}

export function fail(status: number, code: string, message: string): Response {
  return json({ error: { code, message } }, status)
}

/**
 * CORS is only needed if the backend is called from a browser. The Android app
 * sends no `Origin`, so an empty allow list means "native callers only".
 */
function allowedOrigin(req: Request): string | null {
  const origin = req.headers.get('origin');
  if (!origin) return null;
  const allow = (process.env.ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  return allow.includes(origin) ? origin : null;
}

export function preflight(req: Request): Response | null {
  const origin = allowedOrigin(req);
  const headers: Record<string, string> = {
    'access-control-allow-methods': 'GET,POST,OPTIONS',
    'access-control-allow-headers': 'content-type,authorization',
    'access-control-max-age': '600',
  };
  if (!origin) return req.method === 'OPTIONS' ? new Response(null, { status: 204 }) : null;
  headers['access-control-allow-origin'] = origin;
  headers['vary'] = 'origin';
  return req.method === 'OPTIONS' ? new Response(null, { status: 204, headers }) : null;
}

export function withCors(req: Request, res: Response): Response {
  const origin = allowedOrigin(req);
  if (!origin) return res;
  const headers = new Headers(res.headers);
  headers.set('access-control-allow-origin', origin);
  headers.set('vary', 'origin');
  return new Response(res.body, { status: res.status, headers });
}

/* ------------------------------ rate limiting ----------------------------- */

interface Window {
  count: number;
  resetAt: number;
}

const windows = new Map<string, Window>();
const MAX_KEYS = 5_000;

/**
 * Fixed-window limiter. Edge isolates are short lived, so this throttles bursts
 * per instance rather than globally; see `backend/README.md` for the Upstash
 * note when a hard global limit is required.
 */
export function rateLimit(
  req: Request,
  bucket: string,
  limit: number,
  windowMs = 60_000,
): Response | null {
  const ip =
    req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    req.headers.get('x-real-ip') ||
    'unknown';
  const key = `${bucket}:${ip}`;
  const now = Date.now();

  if (windows.size > MAX_KEYS) {
    for (const [entry, value] of windows) {
      if (value.resetAt <= now) windows.delete(entry);
    }
    if (windows.size > MAX_KEYS) windows.clear();
  }

  const current = windows.get(key);
  if (!current || current.resetAt <= now) {
    windows.set(key, { count: 1, resetAt: now + windowMs });
    return null;
  }
  current.count += 1;
  if (current.count <= limit) return null;

  const retryAfter = Math.max(1, Math.ceil((current.resetAt - now) / 1000));
  return json(
    { error: { code: 'rate_limited', message: 'Too many requests. Try again shortly.' } },
    429,
    { 'retry-after': String(retryAfter) },
  );
}

/* -------------------------------- validation ------------------------------ */

export async function readJson(req: Request, maxBytes = 8_192): Promise<Record<string, unknown>> {
  const length = Number(req.headers.get('content-length') ?? 0);
  if (length > maxBytes) throw new HttpError(413, 'body_too_large', 'Request body is too large.');
  const text = await req.text();
  if (text.length > maxBytes) throw new HttpError(413, 'body_too_large', 'Request body is too large.');
  if (!text.trim()) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new HttpError(400, 'invalid_json', 'Request body must be valid JSON.');
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new HttpError(400, 'invalid_json', 'Request body must be a JSON object.');
  }
  return parsed as Record<string, unknown>;
}

export function requireString(
  source: Record<string, unknown>,
  key: string,
  options: { max?: number; pattern?: RegExp; patternHint?: string } = {},
): string {
  const raw = source[key];
  if (typeof raw !== 'string' || !raw.trim()) {
    throw new HttpError(400, 'missing_parameter', `"${key}" is required.`);
  }
  const value = raw.trim();
  if (value.length > (options.max ?? 512)) {
    throw new HttpError(400, 'invalid_parameter', `"${key}" is too long.`);
  }
  if (options.pattern && !options.pattern.test(value)) {
    throw new HttpError(400, 'invalid_parameter', `"${key}" ${options.patternHint ?? 'is malformed'}.`);
  }
  return value;
}

export function optionalString(
  source: Record<string, unknown>,
  key: string,
  options: { max?: number; pattern?: RegExp; patternHint?: string } = {},
): string | undefined {
  const raw = source[key];
  if (raw === undefined || raw === null || raw === '') return undefined;
  if (typeof raw !== 'string') {
    throw new HttpError(400, 'invalid_parameter', `"${key}" must be a string.`);
  }
  return requireString(source, key, options);
}

export function env(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new HttpError(500, 'server_misconfigured', `Server is missing ${name}.`);
  }
  return value;
}

/* --------------------------------- handler -------------------------------- */

/** Wraps a handler with CORS, error mapping and a JSON error body. */
export function handler(fn: (req: Request) => Promise<Response>) {
  return async (req: Request): Promise<Response> => {
    const pre = preflight(req);
    if (pre) return pre;
    try {
      return withCors(req, await fn(req));
    } catch (error) {
      if (error instanceof HttpError) {
        return withCors(req, fail(error.status, error.code, error.message));
      }
      console.error('[zero-backend] unhandled', error);
      return withCors(req, fail(502, 'upstream_error', 'The upstream service did not answer.'));
    }
  };
}

/** Timeout-guarded `fetch` so a slow provider cannot hang the function. */
export async function fetchJson(
  url: string,
  init: RequestInit = {},
  timeoutMs = 9_000,
): Promise<{ status: number; body: any }> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...init, signal: controller.signal });
    const text = await res.text();
    let body: any = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = { raw: text.slice(0, 400) };
    }
    return { status: res.status, body };
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new HttpError(504, 'upstream_timeout', 'The provider did not answer in time.');
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

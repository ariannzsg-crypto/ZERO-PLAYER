/**
 * Local test harness for the Edge handlers.
 *
 * Vercel runs `api/**<name>.ts` on the Edge runtime; this script wires the same
 * handlers into a plain Node HTTP server so they can be exercised offline with
 * `curl`, without a Vercel account.
 *
 *   npm run test:local         # compiles to .local-build and serves on :3000
 */

import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const buildDir = path.join(root, '.local-build');
const port = Number(process.env.PORT || 3000) || 3000;

/** Route table: URL path → compiled module inside .local-build. */
const routes = {
  '/api/health': 'api/health.js',
  '/api/spotify/token': 'api/spotify/token.js',
  '/api/soundcloud/token': 'api/soundcloud/token.js',
  '/api/youtube/search': 'api/youtube/search.js',
};

const cache = new Map();

async function loadHandler(relative) {
  if (!cache.has(relative)) {
    const module = await import(new URL(`file://${path.join(buildDir, relative)}`));
    cache.set(relative, module.default);
  }
  return cache.get(relative);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://localhost:${port}`);
  const target = routes[url.pathname];

  if (!target) {
    res.writeHead(404, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: { code: 'not_found', message: 'No such route.' } }));
    return;
  }

  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);

  const init = {
    method: req.method,
    headers: Object.entries(req.headers).map(([key, value]) => [
      key,
      Array.isArray(value) ? value.join(',') : String(value ?? ''),
    ]),
  };
  if (init.method !== 'GET' && init.method !== 'HEAD') {
    init.body = Buffer.concat(chunks);
    init.duplex = 'half';
  }

  try {
    const handler = await loadHandler(target);
    const response = await handler(new Request(url, init));
    const body = await response.text();
    res.writeHead(response.status, Object.fromEntries(response.headers));
    res.end(body);
  } catch (error) {
    console.error('[dev] handler crashed', error);
    res.writeHead(500, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: { code: 'dev_crash', message: String(error) } }));
  }
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[dev] listening on http://127.0.0.1:${port}`);
});

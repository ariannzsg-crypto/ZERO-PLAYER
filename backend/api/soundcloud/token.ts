import {
  HttpError,
  env,
  fetchJson,
  handler,
  json,
  optionalString,
  rateLimit,
  readJson,
  requireString,
} from '../../lib/http.js';

export const config = { runtime: 'edge' };

/**
 * `POST /api/soundcloud/token`
 *
 * Exchanges a SoundCloud authorization code (or refresh token) using the
 * client secret. SoundCloud requires the exchange to happen server side.
 *
 * Body: `{ code, codeVerifier?, redirectUri }` or `{ refreshToken }`
 */
export default handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed', 'Use POST.');

  const limited = rateLimit(req, 'soundcloud-token', 30);
  if (limited) return limited;

  const body = await readJson(req);
  const refreshToken = optionalString(body, 'refreshToken', { max: 2048 });

  const form = new URLSearchParams({
    client_id: env('SOUNDCLOUD_CLIENT_ID'),
    client_secret: env('SOUNDCLOUD_CLIENT_SECRET'),
  });

  if (refreshToken) {
    form.set('grant_type', 'refresh_token');
    form.set('refresh_token', refreshToken);
  } else {
    form.set('grant_type', 'authorization_code');
    form.set('code', requireString(body, 'code', { max: 2048 }));
    form.set('redirect_uri', requireString(body, 'redirectUri', { max: 512 }));
    const verifier = optionalString(body, 'codeVerifier', { max: 256 });
    if (verifier) form.set('code_verifier', verifier);
  }

  const { status, body: payload } = await fetchJson('https://secure.soundcloud.com/oauth/token', {
    method: 'POST',
    headers: { accept: 'application/json' },
    body: form.toString(),
  });

  if (status !== 200) {
    const reason = payload?.error_description || payload?.error || 'SoundCloud rejected the token request.';
    throw new HttpError(status === 400 || status === 401 ? 401 : 502, 'soundcloud_token_failed', String(reason));
  }

  return json({
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token ?? null,
    expiresIn: payload.expires_in ?? 3600,
    scope: payload.scope ?? '',
    tokenType: payload.token_type ?? 'OAuth',
  });
});

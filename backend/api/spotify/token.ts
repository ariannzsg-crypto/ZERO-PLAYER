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
 * `POST /api/spotify/token`
 *
 * The app runs the Authorization Code + PKCE flow (a public client, so no
 * secret ever reaches the phone) and posts the resulting code here. The
 * exchange itself needs `SPOTIFY_CLIENT_SECRET`, which only this function sees.
 *
 * Body: `{ grantType: "authorization_code", code, codeVerifier, redirectUri }`
 *    or `{ grantType: "refresh_token", refreshToken }`
 */
export default handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed', 'Use POST.');

  const limited = rateLimit(req, 'spotify-token', 30);
  if (limited) return limited;

  const body = await readJson(req);
  const grantType = requireString(body, 'grantType', { max: 40 });

  const form = new URLSearchParams();
  if (grantType === 'authorization_code') {
    form.set('grant_type', 'authorization_code');
    form.set('code', requireString(body, 'code', { max: 2048 }));
    form.set('redirect_uri', requireString(body, 'redirectUri', { max: 512 }));
    const verifier = optionalString(body, 'codeVerifier', { max: 256 });
    if (verifier) form.set('code_verifier', verifier);
  } else if (grantType === 'refresh_token') {
    form.set('grant_type', 'refresh_token');
    form.set('refresh_token', requireString(body, 'refreshToken', { max: 2048 }));
  } else {
    throw new HttpError(400, 'unsupported_grant', 'grantType must be authorization_code or refresh_token.');
  }

  const credentials = btoa(`${env('SPOTIFY_CLIENT_ID')}:${env('SPOTIFY_CLIENT_SECRET')}`);

  const { status, body: payload } = await fetchJson('https://accounts.spotify.com/api/token', {
    method: 'POST',
    headers: {
      authorization: `Basic ${credentials}`,
      'content-type': 'application/x-www-form-urlencoded',
    },
    body: form.toString(),
  });

  if (status !== 200) {
    const reason = payload?.error_description || payload?.error || 'Spotify rejected the token request.';
    throw new HttpError(status === 400 ? 400 : 502, 'spotify_token_failed', String(reason));
  }

  return json({
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token ?? null,
    expiresIn: payload.expires_in ?? 3600,
    scope: payload.scope ?? '',
    tokenType: payload.token_type ?? 'Bearer',
  });
});

import { HttpError, env, fetchJson, handler } from '../../lib/http.js';

export const config = { runtime: 'edge' };

/**
 * `GET /api/soundcloud/callback?code=...&state=...`
 *
 * SoundCloud rejects non-HTTPS redirect URIs, so this endpoint is registered on
 * the SoundCloud dashboard, performs the code exchange with the client secret,
 * and bounces the result back into the app.
 *
 * The `state` value is generated on the device and echoed back unchanged; the
 * app refuses any redirect whose state does not match the one it stored.
 */
export default handler(async (req) => {
  if (req.method !== 'GET') throw new HttpError(405, 'method_not_allowed', 'Use GET.');

  const url = new URL(req.url);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state') ?? '';
  const denied = url.searchParams.get('error');
  const appUri = process.env.APP_REDIRECT_URI || 'zeroplayer://oauth/soundcloud';

  const back = (params: Record<string, string>) => {
    const target = new URL(appUri);
    for (const [key, value] of Object.entries(params)) target.searchParams.set(key, value);
    return Response.redirect(target.toString(), 302);
  };

  if (denied) return back({ error: denied, state });
  if (!code) return back({ error: 'missing_code', state });

  const form = new URLSearchParams({
    client_id: env('SOUNDCLOUD_CLIENT_ID'),
    client_secret: env('SOUNDCLOUD_CLIENT_SECRET'),
    grant_type: 'authorization_code',
    code,
    redirect_uri: url.origin + '/api/soundcloud/callback',
  });

  const { status, body } = await fetchJson('https://secure.soundcloud.com/oauth/token', {
    method: 'POST',
    headers: { accept: 'application/json' },
    body: form.toString(),
  });

  if (status !== 200 || !body?.access_token) {
    return back({
      error: String(body?.error_description || body?.error || 'soundcloud_token_failed'),
      state,
    });
  }

  return back({
    access_token: String(body.access_token),
    refresh_token: String(body.refresh_token ?? ''),
    expires_in: String(body.expires_in ?? 3600),
    state,
  });
});

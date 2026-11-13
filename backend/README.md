# ZERO PLAYER — online music backend

A small Vercel project that keeps provider **secrets** off the phone and holds
the one API key the app cannot safely ship (YouTube Data API).

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/api/health` | GET | Which provider keys the deployment holds. Used by the app's setup sheet. |
| `/api/spotify/token` | POST | Authorization-code (PKCE verifier supplied) or refresh-token exchange with the Spotify client secret. |
| `/api/soundcloud/token` | POST | Authorization-code or refresh-token exchange with the SoundCloud client secret. |
| `/api/soundcloud/callback` | GET | SoundCloud's HTTPS redirect target; exchanges the code and bounces the result to `zeroplayer://oauth/soundcloud`. |
| `/api/youtube/search` | GET | YouTube Data API v3 search + durations. The API key never leaves the server. |

Deliberately absent: any endpoint that proxies, downloads, transcodes or caches
music streams. Catalogue metadata and OAuth only.

Runtime: **Vercel Edge Functions** (`export const config = { runtime: 'edge' }`)
with **zero runtime dependencies**. TypeScript is typechecked, not bundled.

## 1. Deploy

```bash
cd backend
npm install          # only typescript + @types/node, dev dependencies
npx tsc --noEmit     # typecheck

npm i -g vercel
vercel link          # create or link the project
vercel env add SPOTIFY_CLIENT_ID
vercel env add SPOTIFY_CLIENT_SECRET
vercel env add SOUNDCLOUD_CLIENT_ID
vercel env add SOUNDCLOUD_CLIENT_SECRET
vercel env add YOUTUBE_API_KEY
vercel env add APP_REDIRECT_URI        # zeroplayer://oauth/soundcloud
vercel --prod
```

Or push the repository and import it in the Vercel dashboard with **Root
Directory = `backend`**; the Edge functions and `vercel.json` are picked up as-is.

Every variable is documented in [`.env.example`](.env.example). The deployment
never needs a database: tokens live on the device, not on the server.

## 2. Provider credentials

### Spotify
1. <https://developer.spotify.com/dashboard> → **Create app**.
2. Add redirect URI exactly `zeroplayer://oauth/spotify`.
3. Select *Web API* and, while in Development Mode, add each tester's Spotify
   account under **User Management** (limit: 25 users).
4. Copy the client ID into the app (Setup sheet) and the client **secret** into
   `SPOTIFY_CLIENT_SECRET`.

The app is a public PKCE client: no secret ever reaches the phone. The exchange
runs in `/api/spotify/token`, which is why a secret must exist server side.

### SoundCloud
1. Register an application at <https://developers.soundcloud.com/>.
2. Redirect URI: `https://<your-project>.vercel.app/api/soundcloud/callback`
   (SoundCloud requires HTTPS, which is why the callback exists).
3. `SOUNDCLOUD_CLIENT_ID` goes into the app setup sheet, the secret into
   `SOUNDCLOUD_CLIENT_SECRET`.
4. SoundCloud streaming requires an **approved** application. Until approval the
   API returns metadata but not permitted streams; ZERO PLAYER therefore never
   tries to stream and hands playback to SoundCloud itself.

### YouTube
1. Google Cloud Console → new project → enable **YouTube Data API v3**.
2. Create an API key, restrict it to that API (and optionally to the Vercel
   egress IPs).
3. Set `YOUTUBE_API_KEY` on Vercel only — the APK never contains it.

## 3. Local development

```bash
npm run test:local
# [dev] listening on http://127.0.0.1:3000
curl -s localhost:3000/api/health
```

`dev.mjs` compiles the handlers with `tsconfig.local.json` and serves them from a
plain Node HTTP server, so the API can be exercised without a Vercel account.
For emulator use, point the app's backend URL at `http://10.0.2.2:3000`.

## 4. Rate limits

`lib/http.ts` implements a fixed-window limiter (30 requests/minute per IP per
bucket) with a small in-memory map. Edge isolates are short lived, so this
throttles bursts rather than enforcing a global quota. For a hard global limit
put Upstash Redis (or Vercel KV) behind the same `rateLimit` call — the handler
signature does not need to change.

## 5. Error contract

```json
{ "error": { "code": "spotify_token_failed", "message": "…" } }
```

| Status | Meaning |
| --- | --- |
| 400 | Validation failed (`missing_parameter`, `invalid_parameter`, `invalid_json`) |
| 401 | Provider rejected the credentials |
| 429 | Rate limited, with `retry-after` |
| 500 | Server misconfigured (a required variable is missing) |
| 502 / 504 | Upstream provider error or timeout |

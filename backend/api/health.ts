import { handler, json } from '../lib/http.js';

export const config = { runtime: 'edge' };

/** `GET /api/health` — tells the app which providers are configured server side. */
export default handler(async () => {
  return json({
    status: 'ok',
    time: new Date().toISOString(),
    providers: {
      spotify: Boolean(process.env.SPOTIFY_CLIENT_ID && process.env.SPOTIFY_CLIENT_SECRET),
      soundcloud: Boolean(process.env.SOUNDCLOUD_CLIENT_ID && process.env.SOUNDCLOUD_CLIENT_SECRET),
      youtube: Boolean(process.env.YOUTUBE_API_KEY),
    },
  });
});

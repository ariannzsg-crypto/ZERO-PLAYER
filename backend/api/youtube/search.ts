import { HttpError, env, fetchJson, handler, json, rateLimit } from '../../lib/http.js';

export const config = { runtime: 'edge' };

/** ISO-8601 duration (`PT3M12S`) → milliseconds. */
function parseDuration(value: unknown): number {
  if (typeof value !== 'string') return 0;
  const match = /^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(value);
  if (!match) return 0;
  const [, d, h, m, s] = match;
  return (
    (Number(d ?? 0) * 24 * 3600 + Number(h ?? 0) * 3600 + Number(m ?? 0) * 60 + Number(s ?? 0)) * 1000
  );
}

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;

/**
 * `GET /api/youtube/search?q=...&limit=20`
 *
 * The YouTube Data API key never leaves the server. Only public metadata is
 * returned; audio and video are never downloaded or proxied here — the app
 * plays the video inside the official YouTube IFrame player.
 */
export default handler(async (req) => {
  if (req.method !== 'GET') throw new HttpError(405, 'method_not_allowed', 'Use GET.');

  const limited = rateLimit(req, 'youtube-search', 30);
  if (limited) return limited;

  const url = new URL(req.url);
  const query = (url.searchParams.get('q') ?? '').trim();
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') ?? 20) || 20, 1), 30);

  if (!query) throw new HttpError(400, 'missing_parameter', '"q" is required.');
  if (query.length > 120) throw new HttpError(400, 'invalid_parameter', '"q" is too long.');

  const key = env('YOUTUBE_API_KEY');

  const search = await fetchJson(
    `https://www.googleapis.com/youtube/v3/search?${new URLSearchParams({
      part: 'snippet',
      type: 'video',
      videoCategoryId: '10', // Music — the only category this endpoint serves
      maxResults: String(limit),
      q: query,
      key,
    }).toString()}`,
  );

  if (search.status !== 200) {
    const reason =
      search.body?.error?.errors?.[0]?.reason ?? search.body?.error?.message ?? 'YouTube search failed.';
    throw new HttpError(search.status === 403 ? 429 : 502, 'youtube_search_failed', String(reason));
  }

  const items: any[] = search.body?.items ?? [];
  const ids = items
    .map((item) => item?.id?.videoId)
    .filter((id: unknown): id is string => typeof id === 'string' && VIDEO_ID.test(id));

  const durations = new Map<string, number>();
  if (ids.length) {
    const details = await fetchJson(
      `https://www.googleapis.com/youtube/v3/videos?${new URLSearchParams({
        part: 'contentDetails',
        id: ids.join(','),
        key,
      }).toString()}`,
    );
    if (details.status === 200) {
      for (const video of details.body?.items ?? []) {
        durations.set(video.id, parseDuration(video.contentDetails?.duration));
      }
    }
  }

  return json({
    items: items
      .filter((item) => typeof item?.id?.videoId === 'string')
      .map((item) => ({
        id: item.id.videoId,
        title: item.snippet?.title ?? '',
        channel: item.snippet?.channelTitle ?? '',
        thumbnail:
          item.snippet?.thumbnails?.medium?.url ??
          item.snippet?.thumbnails?.default?.url ??
          null,
        publishedAt: item.snippet?.publishedAt ?? null,
        durationMs: durations.get(item.id.videoId) ?? 0,
        url: `https://www.youtube.com/watch?v=${item.id.videoId}`,
      })),
  });
});

# ZERO PLAYER

**A premium, cinematic, offline Android music player.**

ZERO PLAYER is a native Android application built with Kotlin and Jetpack Compose. It indexes the audio
already stored on your phone and plays it through a real ten band DSP equalizer. It is offline by
default: the only network code is the optional AI assistant you enable with your own API key.

Pure black canvas. Charcoal surfaces. Silver chrome accents. No clutter.

---

## Table of contents

- [Screens](#screens)
- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Installation](#installation)
- [Build instructions](#build-instructions)
- [Download](#download)
- [Permissions](#permissions)
- [Performance notes](#performance-notes)
- [Roadmap](#roadmap)
- [License](#license)

---

## Screens

| Screen | What it shows |
| --- | --- |
| **Home** | Chrome wordmark, "Continue listening" hero, quick tiles, recently played, album and playlist rails, on-repeat rail |
| **Library** | Songs / Albums / Artists / Folders with a persistent sort order and one-tap rescan |
| **Albums, Artists, Folders** | Full detail pages with play and shuffle actions |
| **Playlists** | Smart playlists plus user playlists, create / rename / delete / reorder |
| **Search** | Instant search across titles, artists, albums and folders with recent-search memory |
| **Now Playing** | Artwork, seek bar, transport controls, favourite, queue, lyrics, equalizer and visualizer modes |
| **Lyrics** | Full-screen lyrics with LRC sync highlighting, manual import and AI lookup |
| **AI** | Optional assistant: endpoint and key, connection test, artist playlists, unknown-artist repair |
| **Widget** | Resizable 4×1 home screen widget with track info and transport controls |
| **Equalizer** | Ten band EQ curve, bass boost, virtualizer, pre-amp, presets and saved profiles |
| **Queue** | Live queue with jump, remove and clear |

Screenshots are captured from a running device:

```bash
adb exec-out screencap -p > docs/screenshots/home.png
```

Add captures to `docs/screenshots/` and they will render here.

---

## Features

### Playback
- Background playback through a `MediaSessionService` (Media3 / ExoPlayer)
- Lock screen, notification, headset and Bluetooth media controls
- Play, pause, next, previous, seek, shuffle and three repeat modes
- Queue editing: play next, add to queue, remove, reorder, clear
- Remembers the last queue, track and position across app restarts
- Sleep timer, playback speed (0.5× – 2×), audio focus and becoming-noisy handling

### Library
- MediaStore indexing with a Room cache for instant startup
- MP3, FLAC, WAV, AAC, OGG, M4A and OPUS
- All songs, albums, artists, folders, recently played, favourites and most played
- Sorting by title, artist, album, longest first and recently added
- Album artwork with a chrome monogram fallback

### Audio
- **True ten band equalizer** (31 Hz – 16 kHz, ±12 dB) implemented as a 32-bit float
  biquad cascade inside the ExoPlayer PCM pipeline — not the limited hardware EQ
- Bass boost (low shelf at 105 Hz), stereo virtualizer (mid/side widening), pre-amp
- Ten factory presets plus unlimited saved custom profiles (Room)
- Real time visualizer: spectrum bars, circular waveform and oscilloscope, fed by the same PCM tap

### Playlists
- **Artist playlists**: every track is filed into a playlist named after its singer, kept in sync
- Create, rename and delete playlists
- Add and remove tracks from any track menu
- Drag and drop reordering with a long press
- Smart playlists: Favorites, Most Played, Recently Played, Recently Added, Unplayed
- Persistent storage in Room

### Lyrics
- Reads embedded lyrics from ID3v2 `USLT`, Vorbis comments (`LYRICS`) and MP4 `©lyr`
- Reads `.lrc` sidecar files next to the audio file
- Full LRC parser: multiple timestamps per line, offset tags, metadata headers
- Current line highlighted and auto-scrolled; tap a line to seek
- Manual LRC import through the system file picker, stored per track

### AI assist (optional)
- Works with Google Gemini (native REST) or any OpenAI-compatible chat endpoint; provider, base URL and model are editable in the app
- **Lyrics lookup** — the sparkle button on the lyrics screen asks the model for the text and stores it
  per track, marked `ai` in the lyrics header
- **Artist playlists** — a playlist per artist is created and refreshed after every scan (switchable)
- **Unknown artist repair** — tracks tagged `Unknown Artist` are sent to the model one by one so it can
  read the singer from the file name, 40 tracks per run, then the artist playlists rebuild
- The feature is inert until you paste a key; the key is stored in the app's private preferences and
  sent only to the endpoint you configured

---

### Home screen widget
- Current title and artist, with previous / play-pause / next
- Works with the app closed: a tap reconnects the media session and the queued command runs at once
- Redraws only when the track or play state changes, so the launcher is never woken by the position ticker

---

## Technology stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 with a custom dark design system |
| Playback | AndroidX Media3 1.5.1 (ExoPlayer + MediaSessionService) |
| Persistence | Room 2.6.1 (KSP) + SharedPreferences for lightweight state |
| Library access | MediaStore API |
| Images | Coil 2.7 |
| Audio DSP | Custom biquad filters and radix-2 FFT (no native code) |
| Build | AGP 8.7.3, Gradle 8.11.1, JDK 17 |
| SDK | minSdk 26, targetSdk 35, compileSdk 35 |

No analytics, no ads, no third-party trackers. Network access exists only for the optional AI assistant
you configure yourself.

---

## Architecture

```
com.zeroplayer.app
├── MainActivity.kt          single activity, edge-to-edge, Compose entry point
├── ZeroPlayerApp.kt         Application, notification channel
├── core/Graph.kt            process-wide dependency graph (DB, repositories, prefs)
├── data/
│   ├── MediaStoreScanner    MediaStore query + format filtering
│   ├── MusicRepository      derived library state, playlists, stats, EQ profiles
│   ├── Prefs                queue, EQ, AI endpoint and user settings
│   ├── ArtistPlaylists      one-playlist-per-artist builder and sync pass
│   ├── db/                  Room entities, DAOs, database
│   └── model/               Song, Album, Artist, Folder, Playlist, SmartKind
├── net/                     AiClient — optional Gemini / OpenAI-compatible assistant
├── playback/
│   ├── PlaybackService      MediaSessionService, notification, queue persistence, sleep timer
│   ├── PlaybackController   MediaController bridge exposing a StateFlow snapshot
│   ├── EqRenderersFactory   injects the DSP chain into the audio sink
│   └── MediaItems           Song ⇄ MediaItem mapping
├── audio/
│   ├── EqualizerController  EQ state, presets, persistence
│   └── dsp/                 Biquad, TenBandEqProcessor, AudioTap
├── lyrics/                  LrcParser, EmbeddedLyrics, LyricsRepository
└── ui/
    ├── ZeroApp              navigation, bottom bar, mini player, permission gate
    ├── nav/                 destination model and navigator
    ├── theme/               palette, type scale, Material 3 theme
    ├── components/          artwork, song rows, sheets, mini player
    ├── screens/             home, library, details, playlists, search, now playing,
    │                        lyrics, equalizer, queue, settings, ai, permission
    └── visualizer/          FFT and canvas visualizers
```

State flows one way: MediaStore → Room → repository `StateFlow` → Compose. Playback state is a
`StateFlow` mirrored from the `MediaController`, so the UI never touches the player directly.

---

## Installation

1. Download the APK from the [Releases page](../../releases/latest).
2. Allow installation from unknown sources for your file manager or browser.
3. Install and open **ZERO PLAYER**.
4. Grant music access when prompted — this is the only permission the app needs to build your library.

Alternatively install with adb:

```bash
adb install -r ZERO-PLAYER-1.1.1-release.apk
```

---

## Build instructions

### Requirements
- Android Studio Ladybug or newer (JDK 17)
- Android SDK Platform 35 and Build Tools 35.0.0

### Android Studio
1. **File → Open** and select the project root.
2. Let Gradle sync (it downloads AGP, Kotlin, Media3, Compose and Room).
3. Press **Run ▶** on a device or emulator running Android 8.0 (API 26) or newer.

### Command line

```bash
# Debug build → app/build/outputs/apk/debug/
./gradlew assembleDebug

# Release build → app/build/outputs/apk/release/
./gradlew assembleRelease

# Install on a connected device
./gradlew installDebug
```

`local.properties` must point at your SDK (`sdk.dir=/path/to/Android/Sdk`).

### Release signing

The release build uses `keystore.properties` in the project root when it exists:

```properties
storeFile=zero-player.jks
storePassword=…
keyAlias=zero-player
keyPassword=…
```

Without that file the release variant is signed with the debug key so `assembleRelease` still produces
an installable APK. `keystore.properties`, `*.jks` and `*.keystore` are git-ignored — never commit them.

### Continuous integration

`.github/workflows/android.yml` builds both APKs on every push and pull request, uploads them as
artifacts, and publishes a GitHub Release with the release APK whenever a `v*` tag is pushed.

---

## Download

| Version | File | Notes |
| --- | --- | --- |
| **1.1.1** | `ZERO-PLAYER-1.1.1-release.apk` | Fixes queued playback (songs did not start), adds the Gemini assistant |
| 1.1.0 | `ZERO-PLAYER-1.1.0-release.apk` | AI assist, artist playlists, home screen widget |
| 1.0.0 | `ZERO-PLAYER-1.0.0-release.apk` | First release |

Grab it from [Releases](../../releases/latest).

---

## Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_AUDIO` (33+) / `READ_EXTERNAL_STORAGE` (≤ 32) | Read audio files and album art from MediaStore |
| `POST_NOTIFICATIONS` (33+) | Show the playback notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback alive in the background |
| `WAKE_LOCK` | Continue playback with the screen off |
| `MODIFY_AUDIO_SETTINGS` | Apply the equalizer DSP |
| `INTERNET` | Optional AI assistant only (lyrics lookup, artist guessing). Nothing else uses it |

Playback, the library, the equalizer and the visualizer never touch the network. `INTERNET` exists
solely for the AI assistant, which stays disabled until you enter your own API key in Settings → AI.
The key lives in the app's private preferences; there is no backend, no proxy and no key in the APK.
`ACCESS_NETWORK_STATE` appears in the merged manifest because the Media3 notification reads the
connection type.

---

## Performance notes

- The library is cached in Room so the UI renders before MediaStore finishes scanning.
- Audio effects run in a single pass over 16-bit PCM with preallocated float state — no per-frame allocations.
- The visualizer reads a 4096 sample ring buffer and reuses its FFT buffers; rendering stops when the
  screen is not visible or playback is paused.
- No polling services: the only loop is a 500 ms position ticker that runs while connected, and a
  frame loop that exists only while a visualizer is on screen.
- Compose uses `collectAsStateWithLifecycle`, layered navigation and stable keys for smooth 60 fps lists.

---

## Roadmap

- Replay gain / normalisation
- Folder-based auto playlists
- Android Auto browse tree
- Wear OS remote

---

## License

MIT — see [LICENSE](LICENSE).

# LyricTag — companion for Poweramp

A focused, privacy-respecting companion that finds, fixes and manages **lyrics
and missing metadata** for your local music library, and serves lyrics to
[Poweramp](https://powerampapp.com/) through its official plugin API.

Poweramp remains the player. LyricTag is the librarian.

## What it does

- Responds to Poweramp's `ACTION_NEED_LYRICS` requests (plain + LRC lyrics)
- Matches **your actual Poweramp library** via its documented content provider
  (after you approve read-only access inside Poweramp) — the count is real
  because it comes from Poweramp, with whole-device scan as fallback
- Finds missing lyrics from 5 free, keyless sources (LRCLIB, AMLL, KuGou,
  Lyricify, LunaBeat), scored and reviewable — offline-first, only on your tap
- Batch-fetch missing lyrics in the background (WorkManager, cancellable,
  4 tracks at a time; the now-playing song always jumps the queue)
- Looks up incomplete tags via MusicBrainz + iTunes with before/after review;
  only empty tags are ever filled. "Fix all metadata" auto-applies only
  near-certain matches (score ≥ 90); filename dumps (YTDL) are parsed and
  re-healed on every scan
- Fetches album art (iTunes + Deezer), cached on-device, shown in lists
- Scrobbles Poweramp playback to ListenBrainz / Last.fm (your own tokens;
  offline plays queue with real timestamps)
- Review-before-write: nothing is overwritten without your approval
- Embeds lyrics into files where safe, or saves `.lrc` / `.txt` next to them

## What it deliberately does NOT do

- It does **not** touch Poweramp's private database — it only *reads* the
  documented public provider (`content://com.maxmpz.audioplayer.data`),
  and only after your approval. See [docs/LIMITATIONS.md](docs/LIMITATIONS.md).
- No analytics, no ads, no tracking, no Google Play Services.

## Build from source

Requires JDK 17 + Android SDK (API 35). See [docs/BUILD.md](docs/BUILD.md).

```sh
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (needs keystore)
./gradlew test lint                 # unit tests + lint
```

## Project layout

```
app/            Android app (Compose UI, manifest, resources)
core/           pure-Kotlin logic: normalize, match, confidence, LRC parse
poweramp/       Poweramp intent contract (vendored constants) + receiver
lyrics/         LyricsProvider interface + LRCLIB provider + cache repo
tags/           audio tag read/write (jaudiotagger) + merge/overwrite rules
scan/           MediaStore/SAF scanner + WorkManager jobs
db/             Room database (tracks, lyrics, candidates, jobs)
docs/           findings, architecture, build, privacy, dependencies
```

## License

Apache-2.0 — see [LICENSE](LICENSE). Third-party notices in [NOTICE](NOTICE).
Poweramp API constants are used under the permissive license in
`poweramp/api/LICENSE-poweramp.txt` (same terms MaxMP grants plugin authors).

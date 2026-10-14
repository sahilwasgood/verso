# Technical Findings — Verso (formerly LyricTag)

Date: 2026-10-06. Verified against primary sources (not memory).

## 1. Poweramp Lyrics Plugin API (authoritative: github.com/maxmpz/powerampapi)

Build 948+ supports external lyrics (plain + LRC) via **directed broadcast intents**
(sent to one specific plugin receiver — "broadcast" is quoted in the official docs).

| Item | Value |
|---|---|
| Request | action `com.maxmpz.audioplayer.ACTION_NEED_LYRICS`, explicit intent to plugin's exported `BroadcastReceiver` |
| Request extras | All `PowerampAPI.Track.*` fields as extras: `realId` (long, required for response), `title`, `album`, `artist` ("" when unknown), `durMs` (int), `fileType` (int; `TYPE_STREAM=23` has no duration) |
| Response | action `com.maxmpz.audioplayer.ACTION_UPDATE_LYRICS`, extras: `id` (long = Track.REAL_ID), `lyrics` (String, plain or LRC; null/absent = failure), `info_line` (short attribution) |
| Deep link | `com.maxmpz.audioplayer.ACTION_LYRICS_LINK` activity with `track` extra; plugin name+icon shown by Poweramp |
| Semantics | Poweramp won't re-update if lyrics cached unless user refreshes; late responses still processed; multiple plugins → last non-empty wins; tiny/empty lyrics ignored; response may arrive anytime (even unsolicited) |

Send responses with `PowerampAPIHelper.sendPAIntent(context, intent)` from the
vendored API lib (handles package resolution across Poweramp editions).
Receiver must be `exported=true` with the `ACTION_NEED_LYRICS` intent-filter.
Package-visibility: the api lib manifest merges the needed `<queries>` tag.

**No private DB access needed or used.** Track extras on the request intent are
sufficient for search (title/artist/album/duration).

## 2. Full-library enumeration: what is / isn't possible

- Official **content-provider API exists** (`com.maxmpz.audioplayer` provider,
  REST-like URIs documented in PowerampAPI.java) but: write access can corrupt
  the Poweramp DB, and on Android 8+ a runtime permission must be requested
  *from Poweramp itself* (`ACTION_ASK_FOR_DATA_PERMISSION`). It is read-fragile
  across Poweramp editions and requires the user to approve inside Poweramp.
- Decision: **do NOT depend on the Poweramp provider**. Primary library source
  = **MediaStore + SAF user-selected folders + direct file scan** (jaudiotagger
  reads tags straight from files). This is honest, robust, and edition-proof.
  Document the limitation in README + in-app ("Poweramp library browsing inside
  this app is not possible via public API; folders you select are scanned").
- MediaBrowserCompat/MediaSession give only the now-playing queue (≤800 items),
  not a library — not useful for full scans.

## 3. Lyrics source: LRCLIB (lrclib.net)

- Free, no API key, generous rate limits. `GET /api/get?track_name=&artist_name=
  &album_name=&duration=` (exact signature, ±2s duration tolerance, falls back to
  external sources) + `GET /api/search` (keyword fallback) + `GET /api/get/{id}`.
- Returns `plainLyrics`, `syncedLyrics` (LRC), `instrumental`, ids, durations.
- Client identification REQUIRED: `User-Agent: <AppName>/<ver> (<url>)` and/or
  `Lrclib-*` headers. No key to leak → no secret-management problem.
- Legally usable for this purpose: public community DB with publish/flag flow;
  attribute via Poweramp `info_line` + in-app source labels. (We only read.)
- Note: duration in seconds for `/api/get` — convert from ms.

## 4. Metadata read/write: jaudiotagger `net.jthink:jaudiotagger:3.0.1`

- Maven Central, pure Java (no native deps), supports MP3/MP4/M4A/Ogg/FLAC/WAV/
  AIFF/DSF/WMA read+write; generic `FieldKey` API covers title/artist/album/
  album-artist/track/disc/year/genre/lyrics across formats.
- License **LGPL-2.1**. F-Droid accepts LGPL libs from Maven Central source
  builds (it is already shipped by F-Droid apps). Record in dependency audit.
- Embedding safety: read → modify in-memory tag → `commit()`; wrap in
  size/MTIME-verify + try/catch with user-visible failure states; atomicity via
  library's own temp-file commit. Artwork preserved automatically (we never touch
  other fields). SAF/DocumentFile output streams used where only SAF access.
- Micro-edits needed for Android: none at 3.0.1 (pure java.util/java.io; works
  on API 26+; the old FileChannel.map Android bug was pre-ICS).

## 5. Android platform decisions (target 2026)

- minSdk 26 / targetSdk 35 (AGP 8.7, JDK 17). Notification/post-33 permission
  model respected. No Play Services, no Firebase — F-Droid clean.
- Storage: `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32) for
  MediaStore scan; SAF `ACTION_OPEN_DOCUMENT_TREE` for user folders and for
  write access on API 29+ scoped storage; persist URI grants.
- Background: WorkManager (from `androidx.work`, Maven Central) for library
  scan + batch lyric fetch jobs: cancellable, constrained (Unmetered/Charging
  optional), observable progress. No foreground-service abuse.
- DB: Room (paged lists via Paging 3 for 10k+ track libraries).
- DI: manual constructor injection via Application-scoped container (no Hilt —
  fewer deps, fully source-buildable, easier F-Droid audit).
- UI: Jetpack Compose BOM + Material3, dynamic color w/ static fallback palette,
  single-activity + Navigation Compose, UDF (ViewModel + StateFlow).
- Network: OkHttp + Retrofit + Moshi or kotlinx.serialization (all Apache-2.0,
  Central). Timeouts + exponential backoff + cancellation via coroutine scope.

## 6. F-Droid readiness checklist (per current Inclusion Policy)

- OSI license (Apache-2.0 chosen), no proprietary deps/SDKs/trackers, no GMS.
- Build 100% from source with FLOSS toolchain; all deps from Maven Central.
- No prebuilt jars; reproducible-friendly Gradle config (no timestamps in
  archives, pinned versions via version catalog).
- Fastlane metadata dir + changelogs; `fdroid` build flavor without any
  non-free bits (single flavor suffices here — nothing to strip).
- Anti-features: none expected (network use is user-triggered; add
  `NonFreeNet`? No — LRCLIB is a free service with open API; no anti-feature
  applies, but the README documents network use explicitly).
- License audit doc (`docs/DEPENDENCIES.md`) listing every dependency + license.

## 7. Prior art (behavioral reference only — no code copied)

- "Lyrics for Poweramp" (abhishekabhi789, Play): confirms the exact UX shape
  users expect — auto-fetch on NEED_LYRICS, library browse, LRC editor, save
  as file / embed / push to Poweramp. Our implementation is independently
  written from the official API example + docs above.

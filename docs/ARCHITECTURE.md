# Architecture

```
UI (Compose, MainActivity + screens/)
 └ ViewModels (Dashboard, Library, Detail) — StateFlow, UDF
 └ Repositories: LyricsRepository (providers → TrackMatcher scoring → cache)
    Tags: TagManager (jaudiotagger, verify-after-write)
    Scan: LibraryScanner (MediaStore enumerate → tag read → Room)
 └ core/ (pure Kotlin, unit-tested): TrackNormalizer, LrcParser, TrackMatcher, MetadataRules
 └ poweramp/: PowerampContract (vendored constants) + LyricsRequestReceiver
 └ db/: Room (tracks, lyrics_cache)
```

Rules: screens never touch network/files directly; providers behind the
`LyricsProvider` interface; auto-apply only HIGH-confidence matches; every
file write needs explicit user confirmation; offline-first (cached lyrics
and local tags always browsable).

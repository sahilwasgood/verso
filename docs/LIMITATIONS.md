# Known limitation: no Poweramp library browsing

Poweramp exposes **no public API for enumerating its full internal library**.
The official API offers: lyrics plugin intents, command/status intents,
MediaSession/MediaBrowser (now-playing queue only), and a content provider
that requires a user grant inside Poweramp and is unsafe to write to.

LyricTag therefore scans **audio files on your device** (MediaStore + folders
you pick) instead of pretending to read Poweramp's library. Matching to what
Poweramp plays happens through the lyrics request intents, which carry
title/artist/album/duration.

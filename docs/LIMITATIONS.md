# Poweramp library matching — how it works

Poweramp's **documented** content provider (`content://com.maxmpz.audioplayer.data/…`,
see the official `PowerampAPI.java`) exposes its library folders and files
(`folders`, `folders/{id}/files`, `files/{id}` with parsed tags, durations
and lyric flags). This is a public API — not reverse engineering.

Access rules (verified on-device, Poweramp full version):
- Reads work with **no grant on some builds**, but most builds throw
  `SecurityException` until the user approves. The official grant flow is
  `ACTION_ASK_FOR_DATA_PERMISSION` sent to Poweramp's API receiver with our
  package name — Poweramp then shows its own approval screen.
- LyricTag **never writes** to the provider (writes can corrupt Poweramp's DB).

Scan scope order (first available wins):
1. Folders explicitly picked in Settings → Library (manual override).
2. Poweramp's exact file set via the provider (the genuine "same as Poweramp"
   count — no faking). Folder `primary/X/` maps to `/storage/emulated/0/X/`.
3. Whole-device MediaStore scan, with a dashboard button to request Poweramp
   access ("Match my Poweramp library") when Poweramp is installed but unreadable.

Lyric lyrics requests from Poweramp carry title/artist/album/duration and work
regardless of scope.

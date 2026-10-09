# Poweramp library matching — how it works

Poweramp's **documented** content provider (`content://com.maxmpz.audioplayer.data/…`,
see the official `PowerampAPI.java`) exposes its library folders and files
(`folders`, `folders/{id}/files`, `files/{id}` with parsed tags, durations
and lyric flags). This is a public API — not reverse engineering.

Access rules (verified on-device, Poweramp full version + trial build 1025):
- Reads worked with **no grant on the builds we tested** (vivo Android 16,
  Waydroid Android 11). Earlier "access denied" symptoms turned out to be
  our own bugs, not missing grants: wrong intent extra (`package` instead
  of Poweramp's `pak`) and unqualified projections (`_id`/`name` are
  ambiguous in Poweramp's `folder_files⨝albums⨝artists` join — use
  `folder_files._id`, `folder_files.name`, `artists.artist`, `albums.album`;
  a null projection returns `_id` only). See the commit history.
- If a build ever does gate reads, the official flow is
  `ACTION_ASK_FOR_DATA_PERMISSION` with the `pak` extra — kept as the
  "Match my Poweramp library" button (harmless no-op when unreadable).
- LyricTag **never writes** to the provider (writes can corrupt Poweramp's DB).

Scan scope order (first available wins):
1. Folders explicitly picked in Settings → Library (manual override).
2. Poweramp's exact file set via the provider (the genuine "same as Poweramp"
   count — no faking). Folder `primary/X/` maps to `/storage/emulated/0/X/`.
3. Whole-device MediaStore scan, with a dashboard button to request Poweramp
   access ("Match my Poweramp library") when Poweramp is installed but unreadable.

Lyric lyrics requests from Poweramp carry title/artist/album/duration and work
regardless of scope.

# Security Policy

Report vulnerabilities privately: open a GitHub Security Advisory or email
the maintainers (see README). Do not file public issues for security bugs.

Scope of review (done per release):

- exported components: launcher activity (with LYRICS_LINK filter) and the
  Poweramp `LyricsRequestReceiver` only - both validate extras defensively.
- intent handling: `realId`/`title` checked; no file paths accepted from
  Poweramp intents.
- file writes: tag edits preserve unrelated fields/artwork, verify post-write
  size sanity, surface failures instead of corrupting.
- LRC parsing: regex-bounded, no backtracking-prone patterns on untrusted input.
- network: HTTPS only (LRCLIB), timeouts set, no cert pinning bypasses, no WebView.
- dependencies: `docs/DEPENDENCIES.md` license audit; no binary blobs.

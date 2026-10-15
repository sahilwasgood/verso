# Contributing

- Kotlin style: official code style, no wildcard imports.
- Every `core/` change needs unit tests (`./gradlew :core:test`).
- No new dependencies without a license check - append to `NOTICE` and
  `docs/DEPENDENCIES.md`, and confirm Maven Central + F-Droid compatibility.
- No network calls from Compose screens - repositories only.
- Never overwrite user files without explicit confirmation in the UI.
- Security: keep exported components minimal (currently only the Poweramp
  lyrics receiver + launcher/LYRICS_LINK activity), validate all intent
  extras, never trust filenames/paths from intents.

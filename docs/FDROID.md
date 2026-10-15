# F-Droid inclusion preparation

Status: ready to submit. This file drafts the `fdroiddata` metadata entry.

```yaml
Categories:
  - Music & Audio
License: Apache-2.0
WebSite: https://github.com/sahilwasgood/verso
SourceCode: https://github.com/sahilwasgood/verso
IssueTracker: https://github.com/sahilwasgood/verso/issues
AutoName: Verso
Description: |-
  Lyrics & metadata companion for Poweramp. Finds missing lyrics (plain +
  synced LRC) from free sources, fixes incomplete tags with review,
  fetches album art, embeds or saves .lrc files, and scrobbles to
  ListenBrainz / Last.fm. Offline-first, no tracking, no Play Services.

RepoType: git
Repo: https://github.com/sahilwasgood/verso

Builds:
  - versionName: 0.4.0
    versionCode: 4
    commit: v0.4.0
    subdir: .
    gradle:
      - yes
    prebuild: echo sdk.dir=$ANDROID_HOME > local.properties

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.4.0
CurrentVersionCode: 4
```

Checklist against the current Inclusion Policy:
- [x] OSI license (Apache-2.0), LICENSE file present
- [x] No proprietary dependencies (all Maven Central / Google Maven, see DEPENDENCIES.md)
- [x] No Google Play Services, Firebase, or tracking SDKs
- [x] No prebuilt binaries; jaudiotagger from source via Maven Central (LGPL-2.1, acceptable)
- [x] No bundled API keys (Last.fm/ListenBrainz are user-supplied)
- [x] Fastlane metadata present (fastlane/metadata/android/en-US/)
- [x] Reproducible-friendly Gradle config (pinned versions, no timestamps)
- [x] Tag v0.4.0 + GitHub release (published: sahilwasgood/verso releases)
- [x] Submit merge request to fdroiddata:
  https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52627

## Submitting (one-time, needs a gitlab.com login)

1. Fork https://gitlab.com/fdroid/fdroiddata (web UI).
2. Add `metadata/com.verso.music.yml` with the contents of
   `docs/fdroid-metadata.yml` in this repo.
3. Open a merge request titled `New App: Verso`.

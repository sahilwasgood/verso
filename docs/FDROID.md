# F-Droid inclusion preparation

Status: ready to submit. This file drafts the `fdroiddata` metadata entry.

```yaml
Categories:
  - Music & Audio
License: Apache-2.0
WebSite: https://github.com/lyrictag/lyrictag
SourceCode: https://github.com/lyrictag/lyrictag
IssueTracker: https://github.com/lyrictag/lyrictag/issues
AutoName: LyricTag
Description: |-
  Lyrics & metadata companion for Poweramp. Finds missing lyrics (plain +
  synced LRC) from free sources, flags incomplete tags, reviews changes
  before writing, embeds or saves .lrc files. Offline-first, no tracking,
  no Play Services.

RepoType: git
Repo: https://github.com/lyrictag/lyrictag

Builds:
  - versionName: 0.3.0
    versionCode: 3
    commit: v0.3.0
    subdir: .
    gradle:
      - yes
    prebuild: echo sdk.dir=$ANDROID_HOME > local.properties

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.3.0
CurrentVersionCode: 3
```

Checklist against the current Inclusion Policy:
- [x] OSI license (Apache-2.0), LICENSE file present
- [x] No proprietary dependencies (all Maven Central / Google Maven, see DEPENDENCIES.md)
- [x] No Google Play Services, Firebase, or tracking SDKs
- [x] No prebuilt binaries; jaudiotagger from source via Maven Central (LGPL-2.1, acceptable)
- [x] No bundled API keys (Last.fm/ListenBrainz are user-supplied)
- [x] Fastlane metadata present (fastlane/metadata/android/en-US/)
- [x] Reproducible-friendly Gradle config (pinned versions, no timestamps)
- [ ] Tag v0.3.0 + GitHub release (maintainer step)
- [ ] Submit merge request to fdroiddata (maintainer step)

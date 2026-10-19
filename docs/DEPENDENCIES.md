# Dependency & license audit

| Artifact | License | Source |
|---|---|---|
| Kotlin stdlib / coroutines / serialization | Apache-2.0 | Maven Central |
| AndroidX (core, activity, compose BOM, material3, navigation, lifecycle, room, work, paging) | Apache-2.0 | Google Maven |
| OkHttp / logging-interceptor | Apache-2.0 | Maven Central |
| Retrofit / converter-kotlinx-serialization | Apache-2.0 | Maven Central |
| jaudiotagger `net.jthink:jaudiotagger:3.0.1` | LGPL-2.1-or-later | Maven Central |
| Coil `io.coil-kt:coil-compose:2.6.0` (album-art loading + disk cache) | Apache-2.0 | Maven Central |
| JUnit 4 (tests) | EPL-1.0 | Maven Central |

No prebuilt binaries, no GMS, no proprietary SDKs. All F-Droid acceptable.

## Sensitive permissions (with justification)

| Permission | Why |
|---|---|
| SAF folder grants (`OpenDocumentTree`, persistable) | Writing fixed tags, embedded lyrics and sidecar files inside the folders the user picks on the home screen ("Folder access"). Every write is user-initiated (review-apply buttons, batch with on-screen progress), flows through the SAF grant only, and MediaStore covers devices where the file row exists. Without a grant the app is read-only on modern Android. |

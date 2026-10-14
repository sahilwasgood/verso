# Build

Prereqs: JDK 17, Android SDK with platform 35 + build-tools 35.0.0.

```sh
# point Gradle at your SDK (or set ANDROID_SDK_ROOT / ANDROID_HOME)
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease   # needs signing config (see below)
./gradlew :core:test             # unit tests (normalizer, LRC, matcher, rules)
./gradlew lint                   # Android lint
```

Release signing: create a keystore and `~/.gradle/gradle.properties`:

```
VERSO_STORE_FILE=/path/to.keystore
VERSO_STORE_PASSWORD=…
VERSO_KEY_ALIAS=…
VERSO_KEY_PASSWORD=…
```

F-Droid: single flavor, no GMS/proprietary deps, all deps from Maven Central.
Fastlane metadata lives in `fastlane/metadata/android/en-US/`. See
`docs/FDROID.md`.

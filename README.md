# Call Recorder (Android 16 / API 36)

Native Kotlin Android app that automatically records phone calls in the background
without any in-call interaction. Built against `compileSdk`/`targetSdk` 36 (Android 16),
`minSdk` 29.

## Features

- **Automatic call recording** driven by `TelephonyManager` `PHONE_STATE` broadcasts
  (`CallStateReceiver` → `CallRecorderService` foreground service).
- **Multiple formats / quality**: MP4 (AAC), MP3 container, AMR-NB, AMR-WB, WAV/PCM,
  with High / Medium / Low presets (bitrate + sample rate).
- **Configurable storage folder**: SAF tree URI (user-picked folder), MediaStore or
  app-private fallback, deterministic file naming (`yyyy-MM-dd_HHmmss_TYPE_NUMBER.ext`).
- **Restart on boot** (`BootReceiver`, toggleable via `autoStart`).
- **Minimal UI**: permission onboarding + status summary + start/stop service.
- **Full unit-test coverage** of domain logic (state machines, naming, storage, coordinator).

## Project layout

```
app/src/main/java/com/callrecorder/
  CallRecorderService.kt      Foreground service; receives call-state intents
  MainActivity.kt             Minimal UI + permission onboarding
  BootReceiver.kt             Restarts service on device boot
  call/                       CallState, CallEvent, CallStateTracker, CallStateReceiver
  model/                      RecordingFormat, RecordingQuality, CallType, RecorderConfig
  recorder/                   AudioRecorder, MediaRecorderAudioRecorder, RecordingRepository,
                              RecordingStateMachine, RecordingSession, RecorderState
  service/                    RecordingCoordinator
  settings/                   SettingsRepository (SharedPreferences)
  storage/                    FileNamingPolicy, StorageConfig
app/src/test/java/com/callrecorder/   JUnit tests for all of the above
```

## Build & test

```bat
gradlew.bat :app:testDebugUnitTest :app:unitCoverageReport :app:assembleDebug
```

- Test reports: `app/build/reports/tests/testDebugUnitTest/index.html`
- Coverage report: `app/build/reports/jacoco/unitCoverage/index.html`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Known platform limitations (important)

1. **Cellular call audio**: Since Android 10, `AudioSource.VOICE_CALL` is reserved for
   system / privileged apps. Third-party apps can only record via `MIC` or
   `VOICE_COMMUNICATION`, so the *remote* party may be quiet or require speakerphone.
   Silent full-duplex call recording without root is **not possible** on stock Android.
2. **WhatsApp / VoIP calls**: There is no public API to detect or capture a WhatsApp
   call's audio. This project records `VOICE_COMMUNICATION` while a VoIP call is active
   (best-effort), but it cannot guarantee capture or hide the ongoing microphone
   indicator. Recording other parties without consent may be illegal in your
   jurisdiction — the user is responsible for compliance.
3. **Play Store policy**: Call-recording apps are heavily restricted; distribution via
   Play may require the app to be the default dialer or use the Accessibility API
   (which itself is restricted).
4. **AGP 8.7.3 + compileSdk 36**: produces a warning (`suppressUnsupportedCompileSdk=36`
   set in `gradle.properties`). Update AGP when an official release supporting API 36
   is available.

## Test status

- Unit tests: **39 passing, 0 failures** across 8 test classes
  (`CallStateTrackerTest`, `RecordingFormatTest`, `RecordingQualityTest`,
  `RecordingRepositoryTest`, `RecordingStateMachineTest`, `RecordingCoordinatorTest`,
  `FileNamingPolicyTest`, `StorageConfigTest`).
- JaCoCo overall instruction coverage: **48%** (pure domain logic is near-100%; the
  uncovered portion is the Android-framework glue — service, activity, receivers,
  `MediaRecorder` wrapper — which requires instrumentation tests / a device).

## Release APK

Build a signed, minified, resource-shrunk release APK:

```bat
gradlew.bat :app:assembleRelease
```

The signed APK is produced at `app/build/outputs/apk/release/app-release.apk` and
copied (with a checksum manifest) to `dist/call-recorder-<version>.apk`.

Signing credentials are read from `local.properties`:

```
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<password>
RELEASE_KEY_ALIAS=<alias>
RELEASE_KEY_PASSWORD=<password>
```

A self-signed debug keystore (`release.keystore`) is provided for local testing only —
**replace it with your own keystore before publishing.**

### Verifying the artifact

```bat
"%ANDROID_HOME%\build-tools\36.0.0\apksigner.bat" verify --verbose dist\call-recorder-1.0.0.apk
"%ANDROID_HOME%\build-tools\36.0.0\zipalign.exe" -c -v 4 dist\call-recorder-1.0.0.apk
```

Both commands should succeed. The APK is signed with the v2 scheme (verified on the
built artifact), is zip-aligned (verified), and targets Android 16 / API 36.

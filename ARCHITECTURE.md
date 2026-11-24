# Architecture

## Module map

```
CallRecorderService      foreground service; receives call-state intents
  └─ RecordingCoordinator    start/stop orchestration
       ├─ AudioRecorder      interface (MediaRecorderAudioRecorder impl)
       └─ RecordingRepository file naming + output resolution
CallStateReceiver        PHONE_STATE broadcast → service
BootReceiver             BOOT_COMPLETED / MY_PACKAGE_REPLACED → service (only if recorderEnabled)
MainActivity             settings UI, recordings list, update check
  ├─ SettingsRepository  SharedPreferences
  └─ UpdateChecker       GitHub Releases API
```

## Audit findings (2026-11-11)

### 1. Recording stops when the app is closed

`CallRecorderService.isRunning` is a `@Volatile` in-memory flag. Nothing persists
"recording is enabled". `onStartCommand` with a null/absent `EXTRA_STATE` returns
`START_STICKY`, but `onDestroy` sets `isRunning = false` and there is no path that
restarts the service from a persisted flag. Closing the app (or the OS reclaiming
it) therefore silently disables recording, and the UI forgets the previous state.

**Fix:** persist `recorderEnabled` in SettingsRepository. Service reads it on
start, returns `START_STICKY` only while enabled, and BootReceiver restarts it
based on the same flag (not the unrelated `autoStart`).

### 2. Enable button needs multiple taps

`toggleService()` calls `stopService`/`startForegroundService` and then
`refreshUi()`. The button's label is derived from `CallRecorderService.isRunning`,
which only flips inside `onCreate`/`onDestroy` — asynchronously. A tap therefore
looks like it did nothing, so users tap again (which now stops it).

**Fix:** drive the button from the persisted flag, update the UI optimistically
immediately on tap, and disable the button only while the transition is in flight.

### 3. Hard dependency on API 29+ and untested version paths

`minSdk 29` excludes Android 8–9 entirely. `FOREGROUND_SERVICE_MICROPHONE` and
`foregroundServiceType` are used unconditionally. `POST_NOTIFICATIONS` is added at
runtime but the manifest declares it unconditionally (harmless, but untested).
No `Build.VERSION.SDK_INT` gating exists around any of it, and there are no
per-API tests.

**Fix:** `minSdk 26`; gate `foregroundServiceType`, notification permission,
`registerReceiver` flags and `AudioSource` choice on API level; add Robolectric
`@Config(sdk = [...])` tests across 26–36.

### 4. Only one audio source is ever used

`resolveAudioSource()` returns `VOICE_COMMUNICATION` unconditionally, despite the
class comment claiming a fallback chain. Some OEM ROMs do permit `VOICE_CALL`
(which is why other apps appear to record both sides).

**Fix:** attempt `VOICE_CALL` → `VOICE_COMMUNICATION` → `MIC`, catching failures
at `prepare()` and stepping down; record which source succeeded into the filename
metadata and surface it in the UI/compatibility matrix.

### 5. No share/export path

Recordings can only be played in-app. There is no `ACTION_SEND` integration and
no configurable recipient.

**Fix:** expose recordings through a `FileProvider` (`${applicationId}.recordings`,
see `res/xml/file_paths.xml`) and add per-recording / share-all buttons in
`MainActivity` that fire `ACTION_SEND` / `ACTION_SEND_MULTIPLE`. An optional
`shareRecipient` (email) is pre-filled. `CallRecorderService.maybeAutoShare()`
shares automatically after a completed call when `autoShareOnComplete` is set.
Recordings now live under `filesDir/recordings` so the provider path resolves.

### 6. No way to reduce the app's footprint

The activity always appears in the launcher and in recents. There is no
"minimise" mode.

**Fix:** `minimiseMode` persists a flag; when enabled, `MainActivity.applyMinimiseMode()`
sets a blank task description on create/pause so the activity is de-emphasised in
recents, while the ongoing foreground-service notification remains the entry point.
This does not hide the app from Settings (platform limit).

## Known platform limits (not bugs)

- `AudioSource.VOICE_CALL` is reserved for system/privileged apps on stock
  Android 10+. Where an OEM ROM permits it, the app will use it and say so.
- The microphone indicator and the foreground-service notification cannot be
  suppressed by a normal app.
- The app cannot hide itself from Settings → Apps.
- Recording calls without consent is illegal in many jurisdictions.

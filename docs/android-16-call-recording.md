# Call recording on Android 16: why the old APK worked and this app did not

This note records the analysis behind the `1.3.0-diag` build. It covers three
things: what the working F-Droid APK actually does, why this project produced no
files at all on Android 16, and what is still impossible without elevated access.

## 1. What the working APK is

`Call_Recorder.apk` supplied for analysis is:

| Property | Value |
| --- | --- |
| Package | `com.github.axet.callrecorder` |
| Version | 1.7.13 (code 219) |
| minSdk / targetSdk | 9 / **28** |
| Declared permissions | `RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `FOREGROUND_SERVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAPTURE_AUDIO_OUTPUT` (never granted), storage |
| Native libs | `liblame`, `libopus`, `libvorbis`, `libogg` — MP3 / OGG / Opus encoding |
| Foreground service type | none declared (predates the requirement) |

Two facts explain the difference in behaviour:

1. **`targetSdkVersion` 28.** Everything Google added from Android 12 onwards for
   foreground services — the ban on starting one from the background, the
   requirement to declare a service type, the Android 14 rule that a
   `microphone` service cannot be created while the app is in the background —
   is gated on the app's target SDK. An app targeting 28 is exempt from all of
   it. This project targets 36, so none of those exemptions apply.
2. **It records the microphone, not the call.** `CAPTURE_AUDIO_OUTPUT` is
   `signature|privileged`; a sideloaded app never gets it. So on the Asus the app
   was capturing `MIC`. On speakerphone the far end reaches the microphone
   acoustically, which is why handsfree sounded good. Off speakerphone only your
   own voice is near the microphone, which is exactly the "quality suffered"
   you noticed.

So the old APK is not doing anything a modern target SDK forbids — it is simply
old enough to be exempt from the rules, and it uses the microphone.

## 2. Why this project produced no recordings on Android 16

Three independent failures, in the order they occur:

### 2.1 The service never started (this is why nothing was saved)

`CallStateReceiver` called `startForegroundService()` while the app was in the
background. On Android 12+ that throws `ForegroundServiceStartNotAllowedException`
unless the app matches a listed exemption, and a `PHONE_STATE` broadcast is not
one. Worse, on Android 14+ a service of type `microphone` cannot be *created*
while the app is in the background at all: `RECORD_AUDIO` is a while-in-use
permission, so the platform sees the app as not currently holding it and throws
`SecurityException`. The service died before `MediaRecorder` was ever touched,
which is why you got no file and no error dialog.

`BootReceiver` had the same problem: Android 14+ explicitly forbids launching a
`microphone` foreground service from `BOOT_COMPLETED`.

### 2.2 `VOICE_CALL` always fails on stock Android

`MediaRecorderAudioRecorder` tried `VOICE_CALL` first. Since Android 10 that
source requires `CAPTURE_AUDIO_OUTPUT`, so on stock firmware it always throws and
falls through to `MIC` — silently, in a loop, on every call.

### 2.3 The microphone stream is silenced during a call

This is the part no amount of app code can change. The audio framework shares one
input between the call and apps, and while a voice call is active
(`AudioManager.MODE_IN_CALL`) an ordinary app's capture is replaced with silence.
The documented exemptions are a privileged app holding `CAPTURE_AUDIO_OUTPUT`, or
an enabled **AccessibilityService**.

That last one is the only lever available to a sideloaded app, and it is what
Skvalex, Cube ACR and the rest use.

## 3. What changed in this build

* **Arm once, then stay resident.** The microphone foreground service is created
  from `MainActivity` (a visible activity) or from a notification action — both
  allowed — and then stays alive with `START_STICKY`. Call-state broadcasts are
  delivered to the already-running service with a plain `startService()`, so the
  Android 12+/14 restrictions never come into play. If the recorder is not armed
  when a call arrives, you get a notification instead of a crash.
* **Boot is honest.** `BootReceiver` still tries a direct start (works up to
  Android 13) and falls back to a notification whose action arms the service.
* **Inert AccessibilityService** (`CallAudioAccessibilityService`). It reads no
  window content and does nothing on events; its only job is to be enabled so the
  audio framework stops silencing the capture during a call. Enable it from
  Settings → Accessibility **before** dialling: Android 16 blocks enabling an
  accessibility service while a call is in progress.
* **Capture diagnostics.** Every recording now gets a `.json` sidecar with the
  audio source that actually opened, peak and mean amplitude, duration, byte
  count, `AudioManager` mode and device model. `peakAmplitude == 0` means the
  platform handed us silence; anything above a few hundred means real audio.
* **Start delay.** Recording opens 1.2 s after `OFFHOOK`; opening immediately is
  a common cause of silent or clipped first seconds.
* **WAV fixed.** `WAV_PCM` used to write an AAC stream into a `.wav` file. It now
  goes through `AudioRecord` and writes a real 16 kHz mono PCM WAV.

## 4. What is still not possible without elevated access

Full, high-quality capture of both call legs on stock Android 16, from an
ordinary sideloaded app, is not achievable. The permission that would allow it
(`CAPTURE_AUDIO_OUTPUT`) cannot be granted to a normal app, and `adb shell pm
grant` does not work for signature permissions.

Options, in order of effort:

1. **As built (free).** Microphone capture, best with speakerphone. With the
   accessibility service enabled you should get your own voice clearly and the
   far end acoustically. Expect this to work; it is what the old APK does.
2. **ADB-shell helper (Shizuku).** The shell user (`uid 2000`) holds
   `CAPTURE_AUDIO_OUTPUT` in the platform's shell manifest, so a helper process
   started through Shizuku can open `VOICE_CALL` / `VOICE_UPLINK` +
   `VOICE_DOWNLINK` and pipe PCM back to the app. This is the approach behind
   ShizuCallRecorder and Skvalex's "built-in ADB". It needs a one-time ADB
   authorisation per boot on some setups, and a real rewrite of the recorder
   layer.
3. **Root or a privileged install.** Either run the app from `/system/priv-app`
   with `CAPTURE_AUDIO_OUTPUT` in `privapp-permissions`, or ship a Magisk module
   that grants it. Only realistic on an unlocked device.

## 5. Test plan for the Mivi (Android 16)

1. Uninstall the previous build first — this APK is debug-signed, so it will not
   install over a release-signed one.
2. Install `call-recorder-1.3.0-diag.apk`, open it, grant microphone, phone state
   and notification permissions.
3. Tap **Enable call recording**. A dialog appears: open accessibility settings
   and enable **Call Recorder audio access**. (If you skip this, the app still
   arms, but expect silent recordings.)
4. Make a call, answer it, put it on speakerphone, talk for 30 seconds, hang up.
5. Open the recordings list. Alongside the audio file there is a `.json`. Report:
   - `source` — which audio source actually opened (`MIC` expected),
   - `peakAmplitude` — 0 means silence, > 500 means real audio,
   - `bytes` — anything above a few kB means frames were written.

That single file tells us whether the platform silenced the capture or whether
the audio path works and only the routing needs fixing.

## 6. Build notes

Built here with `compileSdk`/`targetSdk` 36, AGP 8.7.3, Gradle 8.13, JDK 17.
Debug signing only; `dist/` release signing is unchanged.

# Capturing both call legs through Shizuku

The microphone path always records one side well and the other side only
acoustically. This module adds a second, independent capture path that can record
the real call mix, without root.

## Why it can work at all

`AudioSource.VOICE_CALL` is gated on `android.permission.CAPTURE_AUDIO_OUTPUT`,
which is `signature|privileged` and cannot be granted to a normal app. The shell
user (`uid 2000`) holds that permission, and Shizuku lets an app run code as the
shell user. So the capture runs in a shell-identity process, and the app only
receives the audio.

That is the same approach used by ShizuCallRecorder and by Skvalex's "built-in
ADB" mode.

## How this module is built

```
app process (uid u0_aXXX)                shell process (uid 2000)
  PreferredAudioRecorder                   RecorderUserService
    └─ ShizukuRecorder  ── bindUserService ──▶  IShizukuRecorder.Stub
         │  pipe (ParcelFileDescriptor)  ◀──────  AudioRecord(VOICE_CALL)
         └─ reader thread → .pcm → WavWriter → .wav
```

* `IShizukuRecorder.aidl` — the interface. Transaction id `16777114` is reserved
  by Shizuku for `destroy()`; leave it alone.
* `RecorderUserService` — runs as shell. Tries `VOICE_CALL`, then
  `VOICE_COMMUNICATION`, then `MIC`, and streams 16 kHz mono PCM into the pipe.
  It reports the source it opened and a running peak amplitude.
* `ShizukuRecorder` — app side. Binds the user service, owns the pipe and the
  reader thread, and wraps the result in a WAV container.
* `PreferredAudioRecorder` (in `:app`) — decides per call whether to use Shizuku,
  and falls back to `MediaRecorderAudioRecorder` if anything fails. The fallback
  is silent and the call is still recorded.

Nothing here is a hard dependency: if Shizuku is missing, not running, or the
permission was never granted, `willUseShizuku()` returns false and the app behaves
exactly like the previous build.

## Prerequisites on the phone

1. Install **Shizuku** (Play Store or F-Droid).
2. Start it: either via **Wireless debugging** (Developer options → Wireless
   debugging → pair, then "Start" in Shizuku) or on a rooted device.
3. Open this app and tap **Enable call recording**. Shizuku shows its consent
   dialog; allow it.
4. The behaviour checkbox *"Record both sides through Shizuku"* is on by default.
   Turn it off to compare against the microphone path on the same device.

Shizuku stops when the phone reboots, and its permission survives but its service
does not. After a reboot, start Shizuku again; until then the app records through
the microphone.

## What to expect in the diagnostics

Every recording still gets a `.json` sidecar:

* `source` — `SHIZUKU:VOICE_CALL` means both legs are in the file;
  `SHIZUKU:MIC` means Shizuku bound but the platform refused `VOICE_CALL` on this
  device; no `SHIZUKU:` prefix means the fallback ran.
* `peakAmplitude` — 0 means silence, a few hundred means real audio.
* `file` ends in `.wav` on the Shizuku path, because that is what is produced.

## Limits, stated plainly

* Untested on hardware: this compiles and packages, but it has not run on a
  device. Expect one round of adjustment on first use.
* Shizuku must be running for every call. It is not a set-and-forget daemon.
* The shell process is not a normal app process: no `Context`, no receivers, no
  content resolvers. Only the audio path is used here for that reason.
* On devices where the audio HAL does not expose the call mix to the shell user
  either, `VOICE_CALL` will fail and you will see `SHIZUKU:MIC` or the fallback.
  That is a hardware/OEM answer, not a code bug.
* Android developer verification (global from January 2027) does not apply to
  ADB installs, which is how you would keep loading builds like this one.

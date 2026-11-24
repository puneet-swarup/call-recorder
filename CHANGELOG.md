# Changelog

All notable changes to this project will be documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [1.3.0] - 2026-11-11

### Added
- **Share / export recordings**: per-recording and share-all buttons expose files
  via `FileProvider` and `ACTION_SEND` / `ACTION_SEND_MULTIPLE`; an optional
  configurable recipient email is pre-filled.
- **Auto-share on completion**: recordings can be shared automatically right after
  a call ends (opt-in), using the configured recipient.
- **Minimise mode**: hides the app from the recents list (custom task description)
  while keeping the foreground-service notification as the entry point.

### Fixed
- Recording enable state now persists across app close and reboot
  (`recorderEnabled` flag is the single source of truth; `BootReceiver` respects it).
- Enable button responds on the first tap (optimistic UI, driven by persisted state).
- `minSdk` lowered 29 to 26 with API-level gating of foreground-service and
  notification paths.
- Audio source fallback chain (`VOICE_CALL` to `VOICE_COMMUNICATION` to `MIC`) instead
  of a single hard-coded source.
- `BootReceiver` now also restarts on `MY_PACKAGE_REPLACED` and is direct-boot aware.
- Recordings storage unified under `filesDir/recordings` so `FileProvider` sharing
  resolves correctly.

### Tests
- Added `SettingsRepositoryTest` (Robolectric) for persistence and field round-trip.
- Added coordinator test for `currentRecordingPath()`.
- Full suite: **52 tests passing, 0 failures**.

## [1.0.0] - 2026-11-10

### Added
- Automatic call recording driven by `PHONE_STATE` broadcasts and a foreground service.
- Multiple recording formats (m4a, mp3, amr, awb, wav) with high/medium/low quality presets.
- SAF folder picker with app-private storage fallback.
- Restart-on-boot support via `BootReceiver`.
- GitHub Actions CI that builds and releases the signed APK on every `v*.` tag.
- Unit tests for domain logic (40 tests across 8 suites).

## [1.1.0] - 2026-11-10

### Added
- Full settings UI: format picker, quality picker, storage folder picker (SAF), and behaviour toggles.
- Recordings list on the main screen with inline playback.
- Adaptive launcher icon (gradient background + microphone foreground) with round variant.
- "Where are my recordings saved?" status line showing the resolved output folder.

### Changed
- Replaced the placeholder button-only screen with a card-based layout (status, permissions, storage, format, quality, behaviour, recordings).
- Service now marks itself running/stopped so the UI toggle reflects real state.

### Removed
- Unused `WAKE_LOCK` and `PACKAGE_USAGE_STATS` permissions (battery and least-privilege cleanup).

## [1.2.0] - 2026-11-10

### Added
- In-app "Check for updates" card: queries the GitHub Releases API, compares
 the published tag against the installed versionCode, and reports either
 "you are on the latest version" or "version X.Y.Z is available" with a
 button that opens the release page.
- UpdateChecker - dependency-free, unit-tested class with an injectable
 fetcher so the GitHub call is testable offline (9 new tests).
- REQUEST_INSTALL_PACKAGES permission, declared ahead of a future in-app
 installer.

### Changed
- Version bumped to 1.2.0 (versionCode 3).

### Known limitations
- The check is manual only (button-driven). No background polling.
- The release page opens in the browser; the user downloads and sideloads the
 APK. Android does not allow silent self-update outside Play or enterprise
 provisioning.

### Release notes
- v1.2.0 published 2026-11-10: https://github.com/puneet-swarup/call-recorder/releases/tag/v1.2.0
 (app-release.apk 2,176,955 bytes, signed, sha256 + BUILD_INFO.txt attached)
- v1.1.0 was never tagged; its changes shipped to main inside v1.2.0.

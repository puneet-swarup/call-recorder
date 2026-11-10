# Changelog

All notable changes to this project will be documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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

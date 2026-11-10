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

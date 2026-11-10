# Contributing

Thanks for your interest in improving Call Recorder!

This document describes the workflow for contributions.

## Development setup

Prerequisites:
- JDK 17 (eclipse temurin or Microsoft OpenJDK 17)
- Android SDK with platform android-36 and build-tools 36.0.0
- Gradle 8.13 (the wrapper is bundled)

Build commands:

```bash
./gradwlw :app:testDebugUnitTest :app:unitCoverageReport :app:assembleDebug
```

## Pull request workflow

1. Fork the repository and create a topic branch.
2. Make your changes and add unit tests for any new pure logic.
3. Ensure `./gradlew :app:testDebugUnitTest` passes locally.
4. Open a pull request using the provided template.

Commit messages should follow [Conventional Commits](https://www.conventionalcommits.org/): `feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`.

## Reporting bugs

Open an issue using the bug report template. Please include the device ANDroid version, app version, and a clear reproduction path.

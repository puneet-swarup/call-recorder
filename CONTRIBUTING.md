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

## Release checklist

Every user-visible change that bumps `versionCode` / `versionName` in
`app/build.gradle.kts` **must** be followed by a tag, otherwise the release
workflow never runs and no APK is published.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a `CHANGELOG.md` entry under the new version.
3. Commit and push to `main`; wait for CI to go green.
4. Tag the merged commit and push the tag:

   ```bash
   git tag -a vX.Y.Z -m "Call Recorder X.Y.Z"
   git push origin vX.Y.Z
   ```

5. Confirm the `Release` workflow run succeeds and the GitHub Release has
   `app-release.apk`, `app-release.apk.sha256` and `BUILD_INFO.txt`.
6. Verify the published APK (`apksigner verify`, `zipalign -c -v 4`).

A version bump without a tag is a silent release: the code ships to `main`
but no signed APK reaches users.

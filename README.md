# 30-Min

[![CI](https://github.com/mikelj14/30-min-app/actions/workflows/ci.yml/badge.svg)](https://github.com/mikelj14/30-min-app/actions/workflows/ci.yml)

Android app that blocks phone use for the first N minutes after you wake up.

Status: testing with friends before a Play Store release.

## Requirements

- JDK 17
- Android SDK with `platforms;android-37.0` and `build-tools;36.0.0`
- Android 12+ device (minSdk 31)

Or skip local setup and use the Dev Container (needs only Docker).

## Build

```bash
./gradlew assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew installDebug      # install on a connected device (adb)
```

## Lint

```bash
./gradlew ktlintCheck       # report style violations
./gradlew ktlintFormat      # auto-fix what it can
```

## Dev Container

`.devcontainer/` holds a pinned JDK + Android SDK image. Open the repo in any
Dev Container-aware editor, or build from the shell:

```bash
docker build -t thirtymin-dev .devcontainer
docker run --rm -v "$PWD":/work -w /work \
  -v thirtymin-gradle:/home/ubuntu/.gradle thirtymin-dev ./gradlew assembleDebug
```

Installing to a phone still runs on the host (the container has no adb).

## CI

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:
secret scan (gitleaks), Gradle wrapper validation, ktlint, Android Lint, unit tests,
debug build and release build (R8 shrinking).

## Release

`.github/workflows/release.yml` runs when a version tag is pushed:

```bash
git switch main && git pull
git tag v0.2.0 && git push origin v0.2.0
```

It checks the tag is on `main`, runs the checks, builds a release APK signed
with the key from GitHub secrets (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`), and publishes a GitHub
Release with the APK and its sha256. Tag `vX.Y.Z` gives versionCode
`X*10000 + Y*100 + Z`; tags must only go up.

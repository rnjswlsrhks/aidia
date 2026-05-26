# AGENTS.md

## Cursor Cloud specific instructions

This is an Android app (SSH client) built with Kotlin + Jetpack Compose. There is no backend; it is a single-module Gradle project.

### Prerequisites

- **JDK 17** — required by the Android Gradle Plugin (`jvmTarget = "17"`).
- **Android SDK** at `$ANDROID_HOME` (`/opt/android-sdk`) with `platforms;android-34`, `build-tools;34.0.0`, and `platform-tools`.

### Key commands

| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Lint | `./gradlew lint` |
| All checks (lint + unit tests) | `./gradlew check` |
| Clean build | `./gradlew clean assembleDebug` |

### Notes

- There are **no unit or instrumented tests** in the project currently. `./gradlew check` succeeds with `NO-SOURCE` for test tasks.
- The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.
- A shared `debug.keystore` is committed to the repo so all builds use the same signing key.
- Version code auto-increments via `GITHUB_RUN_NUMBER` env var; local builds default to `versionCode = 1000`.
- This is a pure Android client app — there are no services to start, no database, and no backend. The "hello world" for this project is a successful `assembleDebug` producing a valid APK.
- The `--no-daemon` flag is recommended for CI/cloud builds to avoid long-lived Gradle daemons.

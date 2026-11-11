# Utsav Lens

Native Android app that turns a photo or video into a festive-themed photo or video.
Three engines behind one interface: LITE (offline compositing, default), LOCAL AI (on-device
Stable Diffusion inpainting, experimental) and HD CLOUD (user's own Gemini API key).

## Hard rules (do not break)
- Kotlin, Compose, Material 3, single activity, MVVM, Hilt, Coroutines/Flow. minSdk 26.
- No backend, analytics, ads, accounts or telemetry. LITE must work with zero network.
- Network only for (a) optional model download, (b) HD CLOUD calls the user triggers.
- APK is built only by GitHub Actions. No secrets, `local.properties` or keystore in the repo. Debug only.
- All bundled visuals are original and drawn in code / vector XML. No third-party images, fonts, music.
- LLM access only through user-supplied API keys.
- Verify library versions / model IDs against official docs before using them.
- Never stub a feature and report it as done.

## Build
- CI: `.github/workflows/build.yml` → `./gradlew test`, `./gradlew assembleDebug`, artifact + Release `build-<run>`.
- Local (no Android SDK needed): `./gradlew -PjvmOnly test` builds and tests the pure-Kotlin modules
  (`:core`, `:themes`, `:engine-api`). Android modules can only be compiled in CI.
- Plugins are on the root `buildscript` classpath (not `plugins {}` with versions) so `-PjvmOnly` works.
- AGP 9 built-in Kotlin: Android modules do not apply `org.jetbrains.kotlin.android`.

## Module map
| Module | Kind | Depends on | Purpose |
|---|---|---|---|
| `:core` | Kotlin/JVM | – | models, `AppResult`, dispatchers, atomic file writer, settings model + migrations |
| `:themes` | Kotlin/JVM | core | theme JSON model, validator, catalogue |
| `:engine-api` | Kotlin/JVM | core | `TransformEngine`, `ImageEditProvider`, request/progress types |
| `:platform` | Android lib | core | read-only bitmap decode (EXIF aware), `MediaStoreWriter` (insert-only gallery writer) |
| `:segmentation` | Android lib | core, platform | person mask + face boxes |
| `:engine-lite` | Android lib | engine-api, themes, segmentation | Canvas compositing + procedural art |
| `:engine-local` | Android lib | engine-api, themes, segmentation | on-device SD inpainting |
| `:engine-cloud` | Android lib | engine-api, themes | cloud engine + providers |
| `:video` | Android lib | core, themes, engine-lite (art) | Media3 slideshow / overlay export |
| `:data` | Android lib | core | Room, key store, settings store, MediaStore writer |
| `:app` | Application | all | Compose UI, navigation, Hilt wiring, foreground service |

Why modules, not packages: the compiler enforces that UI only sees `:engine-api`, and the pure
modules are testable without an Android SDK.

## Verified versions (2026-11-11)
See `gradle/libs.versions.toml`. Sources: developer.android.com (AGP 9.5.1, AndroidX table, Compose BOM
2026.09.00), services.gradle.org (Gradle 9.9.1), Maven Central metadata (Kotlin 2.4.21, KSP 2.3.13,
Hilt 2.60.1, coroutines, serialization, OkHttp, Coil, ONNX Runtime 1.31.1, test libs),
action READMEs (checkout v7, setup-java v6, setup-android v4, setup-gradle v6, upload-artifact v7,
action-gh-release v3).

## Decisions log
- 2026-11-11 Gradle modules over packages (see above).
- 2026-11-11 Room 2.8.5 instead of Room 3: established migration-test tooling; builds are CI-only.
- 2026-11-11 Keys: Android Keystore AES-GCM directly; `androidx.security:security-crypto` is deprecated.
- 2026-11-11 Gemini via plain REST (OkHttp) so base URL and model ID stay user-editable.
- 2026-11-11 CI also triggers on the development branch so each phase is verified before merge.
- 2026-11-11 The user asked for all phases to run without stopping; open Phase 0 questions were
  answered with the recommended defaults (MediaPipe, branch CI trigger, `com.utsavlens.app`).

## Current phase
Phase 1 (skeleton + CI).

## Requirements checklist
Maintained at the bottom of this file from Phase 2 onward.

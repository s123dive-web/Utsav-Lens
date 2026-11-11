# Utsav Lens

Native Android app that turns a photo or video into a festive-themed photo or video, saves it to
the gallery and shares it. Three engines behind one interface:

| Engine | What it does | Network |
|---|---|---|
| LITE (default) | on-device person segmentation + procedural compositing. Person pixels never modified | none |
| LOCAL AI (experimental) | on-device SD 1.5-family (LCM) background repaint via ONNX Runtime; person protected | model download only |
| HD CLOUD (optional) | user's own Gemini key; outfit change / restyle | user-triggered calls only |

## Hard rules (do not break)
- Kotlin, Compose, Material 3, single activity, MVVM, Hilt, Coroutines/Flow. minSdk 26, target 37.
- No backend, analytics, ads, accounts or telemetry. LITE must work with zero network.
- Network only for (a) the optional model download, (b) HD CLOUD / prompt-assist calls the user triggers.
- APK is built only by GitHub Actions. No secrets, `local.properties` or keystore in the repo. Debug only.
- All bundled visuals are original: drawn in code (`engine-lite/.../art`) or vector XML. No third-party
  images, fonts or music. (The two bundled `.tflite` files are ML models, not artwork: MediaPipe
  selfie segmenter and BlazeFace short-range, Apache-2.0, from Google's model storage.)
- LLM access only through user-supplied API keys.
- Verify library versions / model IDs / API shapes against official sources before using them.
- Never stub a feature and report it as done. Say plainly what is unverified.

## Build and test
- CI `.github/workflows/build.yml`: push to `main` or the dev branch, or manual dispatch →
  hygiene checks → `./gradlew test --continue` → `./gradlew assembleDebug` → artifact + Release `build-<run>`.
- Local, without Android SDK or Google Maven: `./gradlew -PjvmOnly test` builds and tests the pure
  Kotlin modules (`:core`, `:themes`, `:engine-api`, `:sd-core`). Android modules compile only in CI.
- Plugins sit on the root `buildscript` classpath (not `plugins {}` with versions) so `-PjvmOnly` works.
- AGP 9 built-in Kotlin: Android modules do not apply `org.jetbrains.kotlin.android`.
- UI strings: edit `tools/strings.tsv`, run `python3 tools/gen_strings.py` (CI fails if the three
  `strings.xml` files are not in sync with the table).
- `local-ai-validate.yml`: runs the diffusion pipeline with the real weights on the runner CPU.
- `probe.yml` + `tools/probe/probe.sh`: research helper used to read official docs / hosting metadata
  from CI, because the development sandbox cannot reach those hosts.

## Module map
| Module | Kind | Depends on | Purpose |
|---|---|---|---|
| `:core` | Kotlin/JVM | – | `AppResult`/`AppError`, dispatchers, `AtomicFileWriter`, hashing, settings model + `SettingsCodec`/`SettingsStore`, `SecretStore`, job recovery, backoff |
| `:themes` | Kotlin/JVM | core | theme model, `ThemeParser` (validator), `ThemeCatalogue`, `ThemeLookup` |
| `:engine-api` | Kotlin/JVM | core | `TransformEngine`, `ImageEditProvider`, `PromptAssistProvider`, request/progress types, `OptionsPolicy`, `EngineContract` |
| `:sd-core` | Kotlin/JVM | core, ONNX Runtime API (compileOnly) | CLIP tokenizer, LCM scheduler, latent blending, `SdPipeline` |
| `:platform` | Android lib | core | read-only bitmap decode (EXIF aware, subsampled), `MediaStoreWriter` (insert-only) |
| `:segmentation` | Android lib | core, platform | MediaPipe person mask + face boxes, `MaskOps`, mask debug bitmap |
| `:engine-lite` | Android lib | engine-api, themes, segmentation, platform | `LiteRenderer` compositor + procedural art, `LiteEngine` |
| `:engine-local` | Android lib | + sd-core, engine-lite | `LocalAiEngine`, `DeviceGate`, `ModelStore`, `ModelDownloader` |
| `:engine-cloud` | Android lib | engine-api, themes, platform, engine-lite (greeting painter) | `CloudEngine`, Gemini providers, error mapping, prompt builder |
| `:video` | Android lib | core, themes, platform, engine-lite (art) | Media3 Transformer slideshow / overlay export |
| `:data` | Android lib | core | Room database + DAOs, `KeystoreCipher` |
| `:app` | Application | all | Compose UI, navigation, Hilt wiring, `JobController`, foreground `JobService` |

Why modules, not packages: the compiler enforces that UI only sees `:engine-api`, and four modules
are testable on any machine without an Android SDK.

## Engine interface (`:engine-api`)
```kotlin
interface TransformEngine {
    val id: String                                    // "lite" | "local" | "cloud"
    suspend fun capabilities(): EngineCapabilities    // outfitChange, faceAlwaysPreserved, maxResolutionPx, needsNetwork, needsDownload, maxVariations
    suspend fun isReady(): Readiness                  // Ready | NeedsDownload(bytes) | NeedsKey | Unsupported(reason, detail)
    fun transform(request: TransformRequest): Flow<TransformProgress>   // Running(percent, stage, preview, step) ... then exactly one Success(uris, notes) or Failure(error, canFallbackToLite)
}
```
Contract (checked by `EngineContract`): cold flow, cancellable, monotonic percent, exactly one
terminal event emitted last, failures emitted not thrown, output never equals the source URI.
`OptionsPolicy.resolve` forces unsupported options off; the UI uses `OptionsPolicy.availability`
to disable controls.

## Provider interfaces
- `ImageEditProvider` (`id`, `displayName`, `keyHelpUrl`, `defaultConfig`, `supportsOutfitChange`,
  `testKey`, `edit`) – cloud image backends. `CloudEngine` only talks to this; add a provider by
  implementing it and adding it to `AppModule.imageProviders`.
- `PromptAssistProvider` (`testKey`, `refine`) – optional text/vision LLM.
- Gemini implementation: `POST {base}/v1beta/interactions`, headers `x-goog-api-key` and
  `Api-Revision: 2026-05-20`, body `{model, input:[{type:"text"},{type:"image",mime_type,data}], response_format}`.
  Shape copied from the official guides via the probe workflow on 2026-11-11. The guides do not
  show the raw REST response, so the parser looks for `{"type":"image"|"text"}` blocks anywhere in
  the response. Base URL and model ID are editable in Settings.

## Theme schema (`app/src/main/assets/themes/<id>.json`)
```
schemaVersion: 1
id            ^[a-z][a-z0-9_]{1,39}$, must equal the file name
name          { en, hi, mr }                       all required
category      festival | celebration | national
palette       { primary, secondary, accent, background }   #RRGGBB or #AARRGGBB
lite          { background{style,intensity}, overlays[{type,anchor,scale,opacity}] (max 6),
                border{style,widthPct}, grade{warmth,saturation,contrast,vignette,glow}, particles{type,density} }
greetings     { en[], hi[], mr[] }
prompt        { positive, negative, outfit?, recommendedStrength }
```
The allowed `style` / `type` / `anchor` names are the enums in `themes/Theme.kt`; `engine-lite`
implements every value with an exhaustive `when`. Adding a theme that reuses existing names needs
no code change. A malformed file is skipped with a logged warning (`ThemeCatalogue.load`).

## Data
- Room v1 (`data/schemas/.../1.json`): `history`, `job`, `cloud_usage`, `model_file` (reserved; the
  downloader currently tracks verified files with a marker file in the model directory). Destructive
  migration is not enabled. `MigrationTest` builds a database from every exported schema file and
  opens it with the current schema + `AppDatabase.MIGRATIONS`.
- Settings: one versioned JSON document (`files/datastore/settings.json`), written atomically,
  loaded through migrations + typed deep merge (`SettingsCodec`).
- API keys: `files/secure/*.enc`, AES-256-GCM under a non-exportable Android Keystore key.
  `secure/`, `models/` and `datastore/` are excluded from backup and device transfer.
- Outputs: new MediaStore entries in `Pictures/UtsavLens` and `Movies/UtsavLens` (IS_PENDING
  publish on API 29+, `AtomicFileWriter` on API 26-28). Originals are only ever opened for reading.
- Jobs are persisted before they start; on process start unfinished jobs without a live worker are
  marked failed ("interrupted") and can be retried from the stored request.

## Decisions log
- Gradle modules over packages (see module map).
- Room 2.8.5 rather than Room 3: established tooling; builds are CI-only.
- Keys: Android Keystore directly; `androidx.security:security-crypto` is deprecated.
- Settings in a JSON file through `AtomicFileWriter` instead of DataStore: one write path for all
  app files and pure-JVM tests.
- Segmentation: MediaPipe Tasks 0.10.26 with bundled models (no usage reporting) instead of ML Kit.
  1.2.2 exists; not adopted because the major-version API change could not be checked locally.
- Capture uses the system camera app (`TakePicture` / `CaptureVideo`), so no CAMERA permission.
- In-app language without AppCompat: activity/service wrap their base context (`LocaleSupport`).
- LITE layering: background (graded original + themed layer) → back overlays → untouched person →
  front overlays/particles → faces re-drawn from the untouched person layer → border → greeting.
- Slideshow transitions are dip-to-colour + slow zoom drawn at composition level, because Media3
  Transformer has no cross-fade between sequence items.
- Gemini via plain REST (OkHttp), no SDK: base URL and model stay user-editable.
- HTTP 429 is shown as a quota error and not retried; only 5xx / unknown IO errors are retried.
- LOCAL AI: option A (ONNX Runtime) with LCM Dreamshaper v7 and latent-blend inpainting; option B
  (Local Dream / QNN) rejected (CC BY-NC licence, proprietary SDK). See `docs/LOCAL_AI_SPIKE.md`.
- The user asked for all phases to run without stopping; open questions were resolved with the
  recommended defaults (MediaPipe, dev-branch CI trigger, `com.utsavlens.app`, option A).

## Known limits (be honest about these)
- **Nothing has been run on a physical device or emulator.** Evidence is CI compilation, JVM /
  Robolectric tests (including real Canvas rendering of all themes) and the real-weights pipeline
  run. Launch, camera, picker, notifications, Keystore, Media3 export and MediaPipe inference on
  real hardware are untested.
- HD CLOUD has never been called with a real key. Request shape is from the official guide; the
  response parser is deliberately tolerant because the raw REST response is not documented there.
- LOCAL AI needs about 12 GB RAM and a 4.3 GB download; speed on phones is unmeasured. CPU only.
- LOCAL AI "keep original background" is a light restyle, not zero change.
- Debug builds are signed with a per-runner key: a new build cannot be installed over an old one.
- Adding a theme with a brand-new motif needs a new renderer in `engine-lite/art`.
- Video: slideshow photos are letterboxed to 9:16 or 1:1; 1080p may fail on very old encoders.
- Typed API keys are not kept across rotation (deliberately not written to saved state).

## Current phase
All phases (0-10) implemented. See the checklist below for what is verified and how.

## Hardening review (Phase 10)
| Area | Mechanism | Verified by |
|---|---|---|
| Low memory | subsampled decode with halving retry, LITE halves its working size on OOM, engines turn `OutOfMemoryError` into a failure event, `onTrimMemory` drops caches and idle segmentation models, `largeHeap` | code + unit tests for decode downscaling; not exercised on a device |
| Rotation | state in ViewModels / `rememberSaveable`; pending camera capture URI is saveable | by design; not exercised on a device |
| Process death | nav arguments + `SavedStateHandle` drafts; jobs persisted before start and reconciled on next start; stale temp files removed | `JobRecovery` unit test, DAO test; not exercised on a device |
| Large files | bounds-first decode, video frames via `MediaMetadataRetriever`, streaming download and export | `BitmapIoTest`; not exercised on a device |
| Permission denial | notifications / legacy storage denial never blocks a job (result kept in app storage and shareable); no camera permission needed | by design; not exercised on a device |
| Airplane mode | LITE has no network code path; cloud checks connectivity first and fails with "Use Lite instead"; model download reports the network problem and resumes later | by design; not exercised on a device |

## Requirements checklist
Legend: ✅ verified by an automated check that ran in CI · 🟡 implemented, compiled in CI, not run on a device · ❌ not done

| Requirement | Status | Evidence |
|---|---|---|
| Kotlin, Compose, M3, single activity, MVVM, Hilt, Flow; minSdk 26 | ✅ | builds in CI |
| No backend / analytics / ads / accounts / telemetry | ✅ | no such dependency; network code only in `engine-cloud`, `ModelDownloader` |
| APK built only by Actions; artifact + Release per run | ✅ | Releases `build-N` |
| No secrets / keystore / local.properties | ✅ | CI hygiene step |
| Original visuals only | ✅ | all art in `engine-lite/art` + vector XML; no image/font/audio files in the repo |
| Engine interface, options auto-disabled by capability | ✅ | `EngineContractTest`, `OptionsPolicy` tests |
| 19 data-driven themes with schema validation; bad file skipped | ✅ | `BundledThemesTest`, `ThemeParserTest` |
| LITE: person pixels unchanged, all themes render | ✅ | `LiteRendererTest` (native Canvas, all 19 themes, face pixels compared) |
| No person found → whole image + notice | ✅ / 🟡 | renderer test / UI text on device |
| Segmentation bundled and offline, feathered mask, debug toggle | 🟡 | `MaskOpsTest` for the maths; MediaPipe inference itself not run |
| Media pick / capture | 🟡 | |
| User options (face, background, outfit, strength, aspect, greeting, variations) | ✅ / 🟡 | policy + renderer tests / UI on device |
| Before/after compare slider, save, share | 🟡 | |
| History; deleting never touches originals | ✅ / 🟡 | DAO test + CI guard that only `MediaStoreWriter` can delete media / UI on device |
| Settings: engines, keys, models, language, storage | 🟡 | |
| English / Hindi / Marathi, all strings in resources | ✅ | generated from one table; CI fails on drift |
| Video slideshow (1-20 photos, transitions, overlays, greeting, own audio, 9:16 & 1:1 1080p) | 🟡 | `SlideshowTimelineTest` for timing; export not run on a device |
| Video overlay (border, animated overlays, grade, greeting) | 🟡 | decoration rendering tested at t>0 for all themes |
| Foreground service with progress and cancel | 🟡 | |
| HD CLOUD: key screen + test + link, encrypted keys, consent, EXIF strip, error mapping, LITE fallback, backoff, daily count | ✅ / 🟡 | provider, prompt, backoff, secret-store and EXIF tests / never called with a real key |
| Provider interface with editable model + base URL | ✅ | `ImageEditProvider`, settings |
| LOCAL AI spike report | ✅ | `docs/LOCAL_AI_SPIKE.md` |
| LOCAL AI engine: resumable verified download, Wi-Fi only, size shown, deletable, device gate, cancel, step progress | ✅ / 🟡 | real-weights pipeline run in CI, gate/store tests / never run on a phone |
| Prompt assist (optional LLM key) | 🟡 | parser + sanitiser tests |
| Room exported schema + migration test | ✅ | `MigrationTest` |
| Atomic writes; originals never overwritten | ✅ | `AtomicFileWriterTest`, `BitmapIoTest`, CI write guard |
| Typed, versioned, deep-merged settings | ✅ | `SettingsCodecTest`, `StoresTest` |
| Process-death safe jobs | ✅ / 🟡 | `JobRecovery` test / on device |
| Dark + light, large touch targets, foldables | 🟡 | adaptive grids and width-capped columns; not seen on a device |
| "Use only photos you have the right to edit" note | 🟡 | Home, Options, Settings |

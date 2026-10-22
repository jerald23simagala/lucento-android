# Quality, verification and release checks

Rule: a check counts only if it actually ran and passed. Anything not executed is listed as **NOT VERIFIED**.

## v0.4.0 verification status (authoring session, 2026-10-22)

| Check | Result | How |
|---|---|---|
| JVM unit tests (pure Kotlin: money, domain, repositories with in-memory DAO fakes) | **215 passed, 0 failed** | Local harness: kotlinc 2.0.21 + JUnit 4.13.2 with stub Room annotations and `runTest` → `runBlocking`. Not Gradle; Android-dependent tests (none exist) would not run here. |
| Migration SQL vs exported schemas | **1→2 and 2→3 match exactly** (tables, columns, indices, FKs) | `python3 tools/check_migrations.py` (SQLite) |
| Room schema history | **Authentic 1.json, 2.json, 3.json committed** | Room exports from CI artifacts `room-schemas` of commits `11e2f3d` (v1), `b55d7a2` (v2), `6eec35b` (v3). Entities did not change between those commits and the next schema bump (checked with `git diff`). SHA-256: 1 `7e1f0df5…`, 2 `31202eb2…`, 3 `da0bebe4…`. |
| Lottie files render | **All 17 render without errors in lottie-web 5.12.2** | `tools/lottie/render.js` (headless Chromium); previews in `docs/design/`. Not lottie-android. |
| Kotlin/Compose/Android compilation (`assembleDebug`) | **NOT VERIFIED** | No Android SDK or Maven/Google repository access in the authoring environment. Run CI or `tools/verify.ps1`. |
| Lint | **NOT VERIFIED** for v0.4 | v0.3.0 baseline (CI `6eec35b`): 0 errors, 48 warnings — 42 GradleDependency, 3 AndroidGradlePluginVersion, 2 UnusedResources, 1 OldTargetApi. |
| Release build (R8 + resource shrinking), App Bundle | **NOT VERIFIED** | CI job "Release build…" + "Inspect release artifacts". |
| Instrumented migration tests (1→2, 2→3, 1→3) | **NOT VERIFIED** | CI job "Room migration tests (emulator)" (API 30, reactivecircus/android-emulator-runner). |
| Emulator / device QA, screenshots vs mockup, light/dark, narrow screens, keyboard | **NOT VERIFIED** | No emulator or device reachable. |
| APK / AAB size impact of Lottie | **NOT VERIFIED** | Asset payload is 176 KB raw / ≈18 KB compressed; library size reported by CI summary. |

## CI pipeline (`.github/workflows/android.yml`)
Job **build** (blocking, in order): wrapper validation → JVM tests → `assembleDebug` (Compose compile, KSP, schema
export) → schema committed and unchanged (`git diff --exit-code app/schemas`) → offline migration check → `lintDebug`
→ `assembleRelease bundleRelease` (signed only if `LUCENTO_KEYSTORE_BASE64`, `LUCENTO_KEYSTORE_PASSWORD`,
`LUCENTO_KEY_ALIAS`, `LUCENTO_KEY_PASSWORD` secrets exist; otherwise unsigned) → inspect release APK with `aapt2`
(application id `com.lucento.app`, not debuggable, no INTERNET / ACCESS_NETWORK_STATE; debug id `com.lucento.app.debug`)
→ size table in the run summary → artifacts (debug APK, release AAB/APK + R8 mapping, schemas, reports).

Job **migrations** (needs build): fails immediately if schema history is incomplete, otherwise boots an API 30 x86_64
emulator and runs `MigrationTest` with `connectedDebugAndroidTest`. No `continue-on-error` anywhere: a red job means
something did not pass.

## Lint policy
- `abortOnError = true`: **errors and fatal issues block** CI. `app/lint.xml` promotes privacy/release issues
  (AllowBackup, HardcodedDebugMode, SecureRandom, TrustAllX509TrustManager, ExportedContentProvider,
  SetJavaScriptEnabled, MissingPermission) to errors.
- **Warnings are informational**: reported (HTML/XML/SARIF artifacts) and triaged here, not suppressed.
- Triage of the v0.3.0 warnings: GradleDependency/AGP version → *informational* (upgrades are deliberate, through the
  version catalog, kept mutually compatible); OldTargetApi → accepted for now (targetSdk 35; move to 36 with the next
  AGP upgrade); UnusedResources (`lucento_blue`, `lucento_sun` colours) → kept warning; harmless, may be removed.
- `MissingTranslation` ignored: the app is English-only.

## Release configuration review (source-level)
- IDs: release `com.lucento.app`; debug `com.lucento.app.debug` + `-debug` version suffix.
- Manifest: `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules` exclude everything from cloud
  backup and device transfer; INTERNET and ACCESS_NETWORK_STATE removed with `tools:node="remove"`; no camera,
  microphone or storage permission (system camera/photo picker/speech recogniser are external apps); FileProvider not
  exported, cache path only. Debug-only: `ui-tooling` (`debugImplementation`).
- R8: `isMinifyEnabled` + `isShrinkResources`; Room, Navigation, kotlinx.serialization, ML Kit and Lottie ship consumer
  rules; app rules keep serializers; `-dontwarn okio.**` for Lottie's optional loaders.
- Signing: only from environment variables (`LUCENTO_KEYSTORE_FILE`, …); `.gitignore` excludes `*.jks`, `*.keystore`,
  `keystore.properties`. No secrets in source or build files.

## Run the full verification locally (Windows)
`powershell -ExecutionPolicy Bypass -File tools\verify.ps1` — runs unit tests, debug build, schema diff, lint,
release build + bundle, and (if a device/emulator is connected) the migration tests; writes `build\verify-summary.txt`.

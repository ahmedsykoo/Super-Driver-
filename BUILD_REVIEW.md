# Super Driver - Build Review

## Structural checks

- Project root contains `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, and `app/`.
- Android package is consistently `com.superdriver.app`.
- Source path is `app/src/main/java/com/superdriver/app/`.
- Test path is `app/src/test/java/com/superdriver/app/`.
- Manifest components resolve to real Kotlin source files.
- Android XML files parse successfully.
- Resource references were checked and no missing local resource reference was found.
- `.github/workflows/` contains the configured CI and APK build workflow files.
- No nested project ZIP, APK, AAB, or build artifact is included.
- No legacy `Viaje Rentable AR`, `com.zibete`, `DriverAssistant`, or ARS identifiers remain in the project naming.

## Build System & Gradle Configuration Fixes

1. **Gradle Distribution**: Corrected Gradle wrapper distribution URL from invalid non-existent `gradle-9.1.0-bin.zip` to stable `gradle-8.10.2-bin.zip`.
2. **Android Gradle Plugin (AGP)**: Updated AGP version from invalid `9.0.1` to stable `8.7.3`.
3. **Kotlin Android Plugin**: Added missing `org.jetbrains.kotlin.android` plugin (v2.0.21) to root `build.gradle.kts` and applied it in `app/build.gradle.kts` so Kotlin sources are compiled.
4. **Compose Plugin**: Updated `org.jetbrains.kotlin.plugin.compose` to `2.0.21`.
5. **CI/CD APK Workflow**: Updated `.github/workflows/build-apk.yml` to trigger on pushes to `main`, `master`, `arena/*` branches, pull requests, tags (`v*`), and manual execution via `workflow_dispatch`.

## UI & Localizations

- Removed lingering Spanish strings from `MainScreen.kt` and updated all tab titles, buttons, section headers, and field labels to Arabic (🇪🇬).

## JVM logic tests

Coverage includes:

- Egyptian EGP parsing.
- Arabic-Indic digits and Arabic decimal separators.
- Pickup + trip distance/time parsing.
- Joined OCR variants such as `Viaje26`.
- Offer-presence validation.
- Own-overlay suppression, including Arabic `قبول/رفض/مراجعة` overlays.
- Profitability calculations.
- Review/reject/accept decisions.
- OCR frame gating and candidate buffering.
- Offer signature/detection state.
- Overlay state.
- Avoid-zone matching.
- Configuration form validation.

## Egyptian smoke test

The following Arabic offer was parsed successfully:

```text
أوبر
230.84 ج.م.
استلام 7 د (2.5 كم)
الرحلة 37 دقيقة (29 كم)
قبول
```

Parsed values:

- Fare: 230.84 EGP
- Pickup: 2.5 km / 7 min
- Trip: 29 km / 37 min
- Total: 31.5 km / 44 min
- EGP/km: 7.3283
- EGP/hour: 314.7818
- Estimated cost: 138.50 EGP
- Estimated net: 92.34 EGP
- Decision with defaults: REVIEW

## CI verification gate

GitHub Actions is configured to run:

1. Project structure validation.
2. JDK 17 setup.
3. Gradle wrapper validation.
4. `testDebugUnitTest`.
5. `lintDebug`.
6. Only after verification succeeds: debug APK build and unsigned release APK build.
7. SHA-256 checksum generation.
8. APK artifact upload (`super-driver-egypt-apks`).

## OCR limitation

The current ML Kit recognizer uses the Latin text-recognition model. The parser and sanitizer support Arabic text and Arabic-Indic digits, but that does not make the OCR engine itself an Arabic OCR engine. For Uber UI rendered in native Arabic script, AccessibilityService or a dedicated Arabic OCR engine should be added before treating Arabic OCR as production-complete.

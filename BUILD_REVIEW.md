# Super Driver - Build Review

## Structural checks

- Project root contains `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, and `app/`.
- Android package is consistently `com.superdriver.app`.
- Source path is `app/src/main/java/com/superdriver/app/`.
- Test path is `app/src/test/java/com/superdriver/app/`.
- Manifest components resolve to real Kotlin source files.
- Android XML files parse successfully.
- Resource references were checked and no missing local resource reference was found.
- `.github/workflows/` contains only the two intended workflow YAML files.
- No nested project ZIP, APK, AAB, or build artifact is included.
- No legacy `Viaje Rentable AR`, `com.zibete`, `DriverAssistant`, or ARS identifiers remain in the project naming.

## JVM logic tests

A lightweight JVM harness compiled the final pure Kotlin production graph and selected unit-test classes without Android/Gradle dependencies.

Result: **135 passed, 0 failed**.

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
8. APK artifact upload.

## Local limitation

A full Android Gradle build was not executed in this environment because the required Gradle 9.1.0 distribution could not be downloaded. The wrapper itself is present and passes shell/archive checks. Therefore the final CI run remains the authoritative Android build verification.

## OCR limitation

The current ML Kit recognizer uses the Latin text-recognition model. The parser and sanitizer support Arabic text and Arabic-Indic digits, but that does not make the OCR engine itself an Arabic OCR engine. For Uber UI rendered in native Arabic script, AccessibilityService or a dedicated Arabic OCR engine should be added before treating Arabic OCR as production-complete.

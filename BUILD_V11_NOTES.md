# Super Driver V11.1

## What was fixed
- The three controls are now real actions, not visual-only buttons.
- Overlay button opens Android's per-app "Display over other apps" settings and reports failure instead of silently doing nothing.
- Accessibility button opens the Android Accessibility settings, attempting the service-specific page first and falling back to the universal page.
- Accessibility status is queried from Android after returning to the app.
- Monitoring is stored natively and can only be enabled when both required Android prerequisites are actually enabled.
- The native AccessibilityService reads visible text/content descriptions from Uber, DiDi Captain and inDrive.
- The trip parser prefers the distance explicitly labelled as the trip distance, avoiding the pickup distance when both appear on the offer card.
- The floating result now shows app, fare, distance, gross price/km, net price/km and suitable/not-suitable status.
- The floating result is dismissed when the ride-app offer disappears or another unsupported app becomes active.
- Arabic/Persian digits and Arabic decimal separators are normalized.
- Added a regression test matching the Arabic offer shape used during device testing.
- Removed the obsolete `MyApp` test dependency.
- GitHub Actions no longer uses the Gradle cache before a wrapper exists, which was the cause of the previous setup-java failure.
- CI creates `android/local.properties` before any Gradle invocation and generates a Gradle wrapper on the runner.
- Version bumped to 1.1.0 (versionCode 3).

## Important Android behavior
Android does not expose Accessibility Service as a normal runtime permission. The user must enable the service in Android Settings. Likewise, overlay access is a special app-op and is not shown in the normal Permissions list. The app now opens the correct settings pages directly.

## Validation performed on the source package
- ZIP extracted successfully.
- XML files parsed successfully.
- Kotlin/Dart source brace/parenthesis sanity checks passed.
- Required Android/Flutter files are present.
- Regression tests were reviewed and the exact Arabic trip parsing case is covered.

A full `flutter analyze`, `flutter test`, and release APK build cannot be executed in this review container because the Flutter SDK is not installed. The GitHub workflow is configured to perform those checks and fail before APK upload if any of them fail.

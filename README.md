# Super Driver

Super Driver is a Flutter Android utility for evaluating ride offers.

## Runtime prerequisites
1. **Display over other apps**: opened directly from the app with Android's overlay settings.
2. **Accessibility Service**: opened directly from the app's permission flow and used to read visible ride-offer text.
3. **Trip monitoring**: an app-level switch that starts/stops processing once the first two prerequisites are active.

The app currently supports the package IDs used for Uber Driver in the native service. The actual package ID presented by a particular regional/app release may differ, so unsupported packages are ignored rather than pretending to have detected a trip.

## Offer processing
The service reads visible text, extracts the fare and trip distance, calculates gross and discounted EGP/km, and displays a native Android floating result above the ride app when overlay access is granted.

## Build
GitHub Actions performs:
- Flutter dependency resolution
- static checks
- `flutter analyze`
- `flutter test`
- Gradle wrapper generation
- `flutter build apk --release`
- APK integrity/existence verification
- artifact upload

The workflow creates `android/local.properties` before Gradle is invoked, avoiding the previous `local.properties` failure. It also does not request Gradle caching until a wrapper is available.

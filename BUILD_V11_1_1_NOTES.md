# Super Driver 1.1.1+4

## Permission controls fixed

- Overlay button now launches Android's official overlay settings flow.
- Accessibility button now tries the Accessibility service details flow with the service ComponentName in the form Android Settings expects, then falls back to the universal Accessibility Settings page.
- Monitoring cannot be enabled until both Overlay and Accessibility are actually granted.
- Accessibility status is read from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` rather than relying only on OEM-dependent `AccessibilityManager` behavior.
- The Android 11+ overlay behavior is handled correctly: Android intentionally opens the top-level overlay settings screen on modern Android, so the user selects **Super Driver** there.
- The Accessibility service config is filtered to the supported ride apps, including the current Uber Driver package `com.ubercab.driver`.

## Important

Android does not allow an ordinary app to silently grant itself Overlay or Accessibility privileges. The app must launch Android Settings and the user must explicitly enable them.

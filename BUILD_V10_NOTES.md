# Super Driver V10

This version implements the three real Android prerequisites shown in the app:

1. Display over other apps (`SYSTEM_ALERT_WINDOW`)
2. Accessibility service for automatic trip detection
3. Trip monitoring switch persisted in native Android preferences

The AccessibilityService monitors Uber Driver package IDs and builds a real system overlay when a fare and trip distance are detected. Arabic-Indic and Persian digits are normalized. When an offer contains both pickup distance and trip distance, the parser prefers the distance explicitly labelled `المسافة`.

The Flutter home screen no longer pretends that a permission is enabled. Each status is queried from Android on resume.

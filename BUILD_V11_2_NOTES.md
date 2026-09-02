# Super Driver V11.2

## Floating Overlay & Live Trip Detection Fixes

### Root causes resolved:
1. **Premature overlay dismissal bug**:
   - Previously, every system/status-bar event from `com.android.systemui` or other background processes checked `rootInActiveWindow` and immediately called `hideOverlay()`, destroying the overlay milliseconds after detection.
   - Now, the overlay persists and is debounced cleanly, only dismissing after 6 seconds of consecutive misses or when explicitly closed via the new close button `✕`.

2. **Invisible Bidi & Unicode characters in Arabic text**:
   - Arabic text in Android accessibility trees frequently contains invisible directional markers (`\u200E`, `\u200F`, `\u061C`), zero-width spaces (`\u200B`, `\uFEFF`), non-breaking spaces (`\u00A0`, `\u202F`), and diacritics.
   - These caused Java/Dart regexes to fail to match amounts like `١٥٢٫٠٠ ج.م` or `EGP 95.00`.
   - Comprehensive text cleaning (`cleanAndNormalizeText`) now strips all invisible Unicode markers and normalizes all digit/space formats before parsing.

3. **Flexible Price Extraction**:
   - Added support for all variations of Egyptian Uber offer cards:
     - Label with or without currency (`القبول مقابل`, `قبول مقابل`, `السعر`, `الأجرة`, `المبلغ`).
     - Currency prefix (`EGP 95.00`, `ج.م 120.00`, `LE 100`).
     - Currency suffix (`95.00 EGP`, `120.00 ج.م.`, `100 LE`).
     - Arabic and Eastern numeral normalization (`٠-٩` and `۰-۹` to standard decimals).

4. **Trip Distance vs Pickup Distance Parsing**:
   - Improved duration and trip regexes to capture:
     - `مشوار لمدة 21 د (16.9 كلم)`
     - `مشوار 21 د (16.9 كم)`
     - `21 د (16.9 كم)`
     - `18 min (12.4 km) trip`
     - Labeled trip distances (`مسافة الرحلة: 16.9 كم`).
   - Accurately distinguishes pickup distance (e.g. `على بعد 5 د (1.2 كم)` or `6 min (2.1 km) away`) from trip distance.

5. **Accessibility Service Configuration**:
   - Added `flagIncludeNotImportantViews` in XML and Kotlin `AccessibilityServiceInfo` so Jetpack Compose / custom Uber views are not skipped.
   - Added `typeWindowsChanged` event listening.
   - Enabled monitoring across all Uber app packages (`com.ubercab.driver`, `com.ubercab`, `com.ubercab.driver.flavour`, `com.uber.client`).

6. **Floating Overlay UI Redesign**:
   - Modern, high-visibility rounded floating card with green/red theme.
   - Header with status badge (`✓ مناسب • UberX` / `✕ غير مناسب`) and a manual close button `✕`.
   - Clear trip fare and distance display.
   - Computed rate per km (after discount) vs driver's required minimum.
   - Smooth dragging anywhere on screen with position persistence.

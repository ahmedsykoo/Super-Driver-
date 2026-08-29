# Super Driver V9

This version fixes the exact failure reported by GitHub Actions:

Flutter 3.47 requires Gradle >= 8.14, while V8 still used Gradle 8.13.

Toolchain:
- Flutter 3.47.0
- Java 17
- Android Gradle Plugin 8.13.2
- Gradle 8.14.3
- Kotlin Gradle Plugin 2.2.20

AGP 9 is NOT enabled. The project explicitly remains on AGP 8.13.2 and legacy AGP/Kotlin compatibility flags remain disabled only where appropriate.

CI:
flutter pub get
flutter analyze
flutter test
flutter build apk --release
APK existence verification
artifact upload

# Super Driver V8 build notes

This archive is structured as a Flutter project at the archive root.

Build toolchain:
- Flutter 3.35.7
- Java 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin Gradle Plugin 2.2.20
- AGP 9 new DSL disabled
- Built-in Kotlin disabled

CI also re-checks and normalizes the Android versions before building, then runs:
flutter pub get -> flutter analyze -> flutter test -> flutter build apk --release

The workflow can consume this archive from the repository root and extract it without flattening the directory structure.

# تثبيت وبناء Super Driver

1. افتح مجلد المشروع الذي يحتوي على `settings.gradle.kts`.
2. استخدم JDK 17.
3. اسمح لـGradle بتنزيل التوزيعة والاعتماديات عند أول بناء.
4. نفذ:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

الناتج:

```text
app/build/outputs/apk/debug/app-debug.apk
```

نسخة release الناتجة من CI غير موقعة، ولا تصلح للنشر في المتجر قبل توقيعها بمفتاح إصدار حقيقي.

# تعليمات مشروع Super Driver

- اسم التطبيق: Super Driver.
- `applicationId`: `com.superdriver.app`.
- مشروع Android الحقيقي يبدأ من جذر المستودع، حيث توجد `settings.gradle.kts` و`gradlew`.
- كود التطبيق داخل `app/src/main/java/com/superdriver/app/`.
- الاختبارات داخل `app/src/test/java/com/superdriver/app/`.
- Workflows فقط داخل `.github/workflows/`.
- لا تضع المشروع داخل ZIP متداخل.
- لا تقبل أو ترفض الرحلات تلقائيًا.
- أي تغيير في parser يجب أن يصاحبه اختبار.
- أي تغيير في الحسابات يجب أن يصاحبه اختبار للحالات الطبيعية والحالات الناقصة.
- قبل تسليم نسخة: راجع البناء، الاختبارات، lint، الصلاحيات، الأداء، الأمان، والمسارات.

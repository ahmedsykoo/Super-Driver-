# معمارية Super Driver

```text
Uber Driver / الشاشة
        ↓
MediaProjection
        ↓
ImageReader + preprocessing
        ↓
OCR
        ↓
RecognizedTextSanitizer
        ↓
TripOfferTextParser
        ↓
TripOfferPresenceValidator
        ↓
TripOfferDecisionPipeline
        ↓
DriverProfitCalculator
        ↓
OverlayCardState
        ↓
WindowManager Overlay
```

## الطبقات

- `capture`: إدارة MediaProjection والإطارات.
- `ocr`: التطبيع، parser، التحقق، وpipeline.
- `calculator`: حساب الربحية والقرار.
- `config`: إعدادات السائق وحفظها.
- `overlay`: النافذة العائمة.
- `ui`: شاشة الإعدادات والتشخيص.
- `zones`: قواعد المناطق التي تستحق مراجعة أو رفضًا.

لا توجد أي أتمتة للضغط على أزرار Uber.

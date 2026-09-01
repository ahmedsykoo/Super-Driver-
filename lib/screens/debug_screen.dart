import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/price_calculator.dart';

// Reads the latest accessibility-tree text published by the
// UberAccessibilityService via the existing MethodChannel set up by
// MainActivity.kt (channel "com.superdriver/accessibility"). Returns
// "" if the service has not pushed any text yet or the user hasn't
// enabled the accessibility service.
class DebugBridge {
  static const _channel = MethodChannel('com.superdriver/accessibility');

  static Future<String> getLatestText() async {
    try {
      final v = await _channel.invokeMethod<String>('getAccessibilityText');
      return v ?? '';
    } on PlatformException {
      return '';
    } on MissingPluginException {
      return '';
    }
  }

  static Future<String> getOcrText() async {
    try {
      final v = await _channel.invokeMethod<String>('getOcrText');
      return v ?? '';
    } on PlatformException {
      return '';
    } on MissingPluginException {
      return '';
    }
  }
}

class DebugScreen extends StatefulWidget {
  const DebugScreen({super.key});

  @override
  State<DebugScreen> createState() => _DebugScreenState();
}

class _DebugScreenState extends State<DebugScreen> {
  bool get isArabic => Localizations.localeOf(context).languageCode == 'ar';
  String _tr(String ar, String en) => isArabic ? ar : en;
  String _rawText = '';
  String _ocrText = '';
  bool _loading = true;
  String _extractedPrice = '—';
  String _extractedTrip = '—';
  String _perKm = '—';

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    final text = await DebugBridge.getLatestText();
    final ocr = await DebugBridge.getOcrText();
    final price = PriceCalculator.extractPrice(text);
    // The default setting analyzes the trip distance without pickup distance.
    final trip = PriceCalculator.extractTripDistance(text, policy: 'strict');
    String perKm = '—';
    if (price != null && trip != null && trip > 0) {
      perKm = (price / trip).toStringAsFixed(2);
    }
    if (!mounted) return;
    setState(() {
      _rawText = text;
      _ocrText = ocr;
      _extractedPrice = price?.toStringAsFixed(2) ?? '—';
      _extractedTrip = trip?.toStringAsFixed(2) ?? '—';
      _perKm = perKm;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_tr('تشخيص — نص الوصول', 'Debug — Accessibility text')),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _refresh,
            tooltip: _tr('تحديث', 'Refresh'),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _label(_tr('نص الوصول الخام', 'Raw accessibility text')),
                  const SizedBox(height: 6),
                  SelectableText(
                    _rawText.isEmpty ? _tr('(فارغ — افتح Uber أولًا ثم ارجع)', '(empty — open Uber first, then come back)') : _rawText,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
                  ),
                  const SizedBox(height: 24),
                  _label(_tr('السعر المستخرج (ج.م)', 'Extracted price (EGP)')),
                  Text(_extractedPrice, style: _valueStyle()),
                  const SizedBox(height: 16),
                  _label(_tr('مسافة الرحلة المستخرجة (كم)', 'Extracted trip distance (km)')),
                  Text(_extractedTrip, style: _valueStyle()),
                  const SizedBox(height: 16),
                  _label(_tr('الحساب ج.م/كم', 'Computed EGP/km')),
                  Text(_perKm, style: _valueStyle()),
                  const SizedBox(height: 24),
                  _label(_tr('حالة OCR', 'OCR status')),
                  const SizedBox(height: 6),
                  SelectableText(
                    _ocrText.isEmpty
                        ? _tr('(OCR غير مفعّل حاليًا؛ يعتمد التطبيق على نص الوصول)', '(OCR is currently disabled; the app uses accessibility text)')
                        : _ocrText,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                  ),
                  const SizedBox(height: 24),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.amber.shade100,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.amber.shade700),
                    ),
                    child: Text(
                      _tr(
                        'طريقة الاستخدام:\n1. افتح Uber وأظهر عرضًا حقيقيًا.\n2. اضغط Home بدون إغلاق التطبيق.\n3. افتح Super Driver واضغط تشخيص.\n4. اضغط تحديث وانسخ نص الوصول الخام.\n5. أرسل لقطة الشاشة للمطور.',
                        'How to use:\n1. Open Uber and bring up a real offer card.\n2. Press Home without closing the app.\n3. Re-open Super Driver and tap Debug.\n4. Tap Refresh and copy the raw accessibility text.\n5. Send the screenshot to the developer.',
                      ),
                      style: const TextStyle(fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _label(String s) => Text(
    s,
    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.black54),
  );

  TextStyle _valueStyle() => const TextStyle(
    fontSize: 22,
    fontWeight: FontWeight.bold,
    fontFamily: 'monospace',
  );
}

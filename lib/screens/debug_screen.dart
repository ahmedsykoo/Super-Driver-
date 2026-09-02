import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/price_calculator.dart';

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
    final price = PriceCalculator.extractPrice(text);
    final trip = PriceCalculator.extractTripDistance(text, policy: 'strict') ??
        PriceCalculator.extractTripDistance(text, policy: 'bare');
    String perKm = '—';
    if (price != null && trip != null && trip > 0) {
      perKm = (price / trip).toStringAsFixed(2);
    }
    if (!mounted) return;
    setState(() {
      _rawText = text;
      _extractedPrice = price != null ? '${price.toStringAsFixed(2)} EGP' : '—';
      _extractedTrip = trip != null ? '${trip.toStringAsFixed(2)} km' : '—';
      _perKm = perKm != '—' ? '$perKm EGP/km' : '—';
      _loading = false;
    });
  }

  void _copyText(String text) {
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: const Color(0xFF10B981),
        behavior: SnackBarBehavior.floating,
        content: Text(_tr('تم نسخ النص إلى الحافظة', 'Text copied to clipboard')),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_tr('تشخيص قراءة الشاشة', 'Screen Diagnostics')),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh_rounded, color: Color(0xFF38BDF8)),
              onPressed: _loading ? null : _refresh,
              tooltip: _tr('تحديث البيانات', 'Refresh Data'),
            ),
          ],
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator(color: Color(0xFF0284C7)))
            : SingleChildScrollView(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Extracted Values Highlights
                    Container(
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1E293B),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: const Color(0xFF334155)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.analytics_outlined, color: Color(0xFF10B981)),
                              const SizedBox(width: 8),
                              Text(
                                _tr('نتائج التحليل الفوري', 'Live Extracted Results'),
                                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                              ),
                            ],
                          ),
                          const SizedBox(height: 14),
                          _resultRow(_tr('السعر المستخرج:', 'Extracted Fare:'), _extractedPrice, const Color(0xFF34D399)),
                          const Divider(color: Color(0xFF334155), height: 16),
                          _resultRow(_tr('المسافة المستخرجة:', 'Extracted Distance:'), _extractedTrip, const Color(0xFF38BDF8)),
                          const Divider(color: Color(0xFF334155), height: 16),
                          _resultRow(_tr('المعدل المحسوب:', 'Computed Rate:'), _perKm, const Color(0xFFFDE047)),
                        ],
                      ),
                    ),
                    const SizedBox(height: 18),

                    // Raw Accessibility Text
                    Container(
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        color: const Color(0xFF1E293B),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: const Color(0xFF334155)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  const Icon(Icons.code_rounded, color: Color(0xFF38BDF8)),
                                  const SizedBox(width: 8),
                                  Text(
                                    _tr('نص الوصول المقروء (Raw Text)', 'Captured Accessibility Text'),
                                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
                                  ),
                                ],
                              ),
                              if (_rawText.isNotEmpty)
                                IconButton(
                                  icon: const Icon(Icons.copy_rounded, size: 20, color: Color(0xFF94A3B8)),
                                  onPressed: () => _copyText(_rawText),
                                  tooltip: _tr('نسخ النص', 'Copy Text'),
                                ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: const Color(0xFF0F172A),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: const Color(0xFF334155)),
                            ),
                            child: SelectableText(
                              _rawText.isEmpty
                                  ? _tr('(لا يوجد نص حالياً — افتح أوبر وارجع للتطبيق ثم اضغط تحديث)', '(No text captured yet — open Uber with a trip offer and tap refresh)')
                                  : _rawText,
                              style: const TextStyle(fontFamily: 'monospace', fontSize: 13, color: Color(0xFFCBD5E1), height: 1.5),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 18),

                    // Instructions Card
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: const Color(0x4D064E3B),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0x6610B981)),
                      ),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.info_outline_rounded, color: Color(0xFF34D399), size: 22),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              _tr(
                                'خطوات اختبار التشخيص:\n١. افتح تطبيق أوبر درايفر وأظهر عرض رحلة حقيقي.\n٢. ارجع لتطبيق Super Driver وافتح شاشة التشخيص.\n٣. اضغط تحديث للتحقق من التقاط السعر والمسافة بنجاح.',
                                'How to diagnose:\n1. Open Uber Driver and receive a trip offer.\n2. Switch to Super Driver Diagnostics.\n3. Tap Refresh to verify captured fare & distance.',
                              ),
                              style: const TextStyle(fontSize: 13, color: Color(0xFFA7F3D0), height: 1.5),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _resultRow(String label, String value, Color color) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 14, color: Color(0xFF94A3B8))),
        Text(
          value,
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: color, fontFamily: 'monospace'),
        ),
      ],
    );
  }
}

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/trip_data.dart';
import '../services/accessibility_listener.dart';
import '../services/price_calculator.dart';

class HomeScreen extends StatefulWidget {
  final ValueChanged<Locale> onLocaleChange;
  const HomeScreen({super.key, required this.onLocaleChange});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  TripData? _trip;
  AccessibilityStatus _status = const AccessibilityStatus(
    overlay: false,
    accessibility: false,
    monitoring: false,
  );
  double _minPrice = 7.5;
  double _discount = 0;

  final _price = TextEditingController();
  final _distance = TextEditingController();
  final _min = TextEditingController(text: '7.5');
  final _discountController = TextEditingController(text: '0');

  bool get isArabic => Localizations.localeOf(context).languageCode == 'ar';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      _minPrice = p.getDouble('minPrice') ?? 7.5;
      _discount = p.getDouble('uberDiscount') ?? 0;
      _min.text = _minPrice.toString();
      _discountController.text = _discount.toString();
    });
    await _refreshStatus();
  }

  Future<void> _refreshStatus() async {
    final status = await AccessibilityListener.getStatus();
    if (mounted) setState(() => _status = status);
  }

  Future<void> _save() async {
    final min = double.tryParse(_min.text.replaceAll(',', '.')) ?? 7.5;
    final discount = (double.tryParse(_discountController.text.replaceAll(',', '.')) ?? 0).clamp(0, 100).toDouble();
    final p = await SharedPreferences.getInstance();
    await p.setDouble('minPrice', min);
    await p.setDouble('uberDiscount', discount);
    if (mounted) {
      setState(() {
        _minPrice = min;
        _discount = discount;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isArabic ? 'تم حفظ الإعدادات' : 'Settings saved')),
      );
    }
  }

  Future<void> _toggleMonitoring() async {
    if (!_status.overlay) {
      await AccessibilityListener.openOverlaySettings();
      return;
    }
    if (!_status.accessibility) {
      await AccessibilityListener.openAccessibilitySettings();
      return;
    }
    final enabled = !_status.monitoring;
    final ok = await AccessibilityListener.setMonitoringEnabled(enabled);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isArabic ? 'أكمل التصريحين الأولين أولاً' : 'Complete the first two permissions first')),
      );
    }
    await _refreshStatus();
  }

  Future<void> _readUber() async {
    final text = await AccessibilityListener.getScreenText();
    final price = PriceCalculator.extractPrice(text);
    final distance = PriceCalculator.extractDistance(text);
    if (!mounted) return;
    if (price != null && distance != null) {
      setState(() => _trip = TripData(
        price: price, distance: distance, timestamp: DateTime.now()));
    }
  }

  void _calculate() {
    final price = double.tryParse(_price.text.replaceAll(',', '.'));
    final distance = double.tryParse(_distance.text.replaceAll(',', '.'));
    if (price == null || distance == null || price <= 0 || distance <= 0) return;
    setState(() => _trip = TripData(price: price, distance: distance, timestamp: DateTime.now()));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshStatus();
      _readUber();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _price.dispose();
    _distance.dispose();
    _min.dispose();
    _discountController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final trip = _trip;
    final suitable = trip?.isSuitable(_minPrice, _discount) ?? false;
    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Super Driver'),
          centerTitle: true,
          actions: [
            PopupMenuButton<String>(
              onSelected: (v) => widget.onLocaleChange(Locale(v)),
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'ar', child: Text('العربية')),
                PopupMenuItem(value: 'en', child: Text('English')),
              ],
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  children: [
                    Text(
                      isArabic ? 'حالة الخدمة' : 'Service status',
                      style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 14),
                    _permissionRow(
                      icon: Icons.open_in_new,
                      title: isArabic ? 'الظهور فوق التطبيقات' : 'Display over other apps',
                      enabled: _status.overlay,
                      button: isArabic ? 'تفعيل' : 'Enable',
                      onPressed: _openOverlay,
                    ),
                    _permissionRow(
                      icon: Icons.accessibility_new,
                      title: isArabic ? 'اكتشاف الرحلات تلقائياً' : 'Automatic trip detection',
                      enabled: _status.accessibility,
                      button: isArabic ? 'تفعيل' : 'Enable',
                      onPressed: _openAccessibility,
                    ),
                    _permissionRow(
                      icon: Icons.radar,
                      title: isArabic ? 'تشغيل متابعة الرحلات' : 'Enable trip monitoring',
                      enabled: _status.monitoring,
                      button: _status.monitoring
                          ? (isArabic ? 'إيقاف المتابعة' : 'Stop monitoring')
                          : (isArabic ? 'تشغيل المتابعة' : 'Start monitoring'),
                      onPressed: _toggleMonitoring,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _status.ready
                          ? (isArabic ? '✅ التطبيق جاهز للعمل فوق تطبيقات الرحلات' : '✅ Ready to work over ride apps')
                          : (isArabic ? '⚠️ فعّل الثلاث خطوات من إعدادات الهاتف ثم ارجع للتطبيق' : '⚠️ Complete all three steps to start Super Driver'),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: _status.ready ? Colors.green.shade700 : Colors.orange.shade800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      isArabic
                          ? 'ملاحظة: الظهور فوق التطبيقات تصريح خاص، وقراءة الرحلات تتطلب تفعيل خدمة إمكانية الوصول يدويًا من إعدادات Android.'
                          : 'Note: overlay is a special Android permission, and trip reading requires manually enabling Accessibility Service in Android Settings.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  children: [
                    Text(
                      trip == null
                          ? (isArabic ? 'في انتظار الرحلة...' : 'Waiting for trip...')
                          : (suitable ? (isArabic ? '✅ مناسب' : '✅ Suitable') : (isArabic ? '❌ غير مناسب' : '❌ Not Suitable')),
                      style: TextStyle(
                        fontSize: 27,
                        fontWeight: FontWeight.bold,
                        color: trip == null ? null : (suitable ? Colors.green : Colors.red),
                      ),
                    ),
                    if (trip != null) ...[
                      const SizedBox(height: 12),
                      _row(isArabic ? 'سعر الرحلة' : 'Trip price', '${trip.price.toStringAsFixed(2)} EGP'),
                      _row(isArabic ? 'المسافة' : 'Distance', '${trip.distance.toStringAsFixed(1)} km'),
                      _row(isArabic ? 'السعر/كم' : 'Price/km', '${trip.pricePerKm.toStringAsFixed(2)} EGP'),
                      _row(isArabic ? 'بعد الخصم' : 'After discount', '${trip.pricePerKmAfterDiscount(_discount).toStringAsFixed(2)} EGP'),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),
            Text(isArabic ? 'اختبار يدوي' : 'Manual test', style: Theme.of(context).textTheme.titleLarge),
            _field(_price, isArabic ? 'سعر الرحلة' : 'Trip price'),
            _field(_distance, isArabic ? 'المسافة (كم)' : 'Distance (km)'),
            FilledButton.icon(onPressed: _calculate, icon: const Icon(Icons.calculate), label: Text(isArabic ? 'احسب الرحلة' : 'Calculate trip')),
            const SizedBox(height: 20),
            Text(isArabic ? 'الإعدادات' : 'Settings', style: Theme.of(context).textTheme.titleLarge),
            _field(_min, isArabic ? 'الحد الأدنى / كم' : 'Minimum / km'),
            _field(_discountController, isArabic ? 'خصم الشركة %' : 'Company discount %'),
            FilledButton.tonalIcon(onPressed: _save, icon: const Icon(Icons.save), label: Text(isArabic ? 'حفظ الإعدادات' : 'Save settings')),
          ],
        ),
      ),
    );
  }

  Future<void> _openOverlay() async {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(isArabic ? 'جارٍ فتح إعدادات الظهور فوق التطبيقات...' : 'Opening overlay settings...')),
    );
    final opened = await AccessibilityListener.openOverlaySettings();
    if (!mounted) return;
    if (!opened) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isArabic ? 'تعذر فتح إعدادات الظهور فوق التطبيقات. افتح إعدادات الهاتف يدويًا.' : 'Could not open overlay settings. Open Android Settings manually.')),
      );
    }
  }

  Future<void> _openAccessibility() async {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(isArabic ? 'جارٍ فتح إعدادات إمكانية الوصول...' : 'Opening Accessibility settings...')),
    );
    final opened = await AccessibilityListener.openAccessibilitySettings();
    if (!mounted) return;
    if (!opened) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isArabic ? 'تعذر فتح إعدادات إمكانية الوصول. افتح إعدادات الهاتف يدويًا.' : 'Could not open Accessibility settings. Open Android Settings manually.')),
      );
    }
  }

  Widget _permissionRow({required IconData icon, required String title, required bool enabled, required String button, required VoidCallback onPressed}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Theme.of(context).colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(12)),
      child: Column(
        children: [
          Row(children: [Icon(icon), const SizedBox(width: 10), Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w600))), Text(enabled ? '✅' : '❌', style: const TextStyle(fontSize: 20))]),
          const SizedBox(height: 8),
          SizedBox(width: double.infinity, child: FilledButton(onPressed: onPressed, child: Text(enabled && button == (isArabic ? 'تفعيل' : 'Enable') ? (isArabic ? 'تم التفعيل' : 'Enabled') : button))),
        ],
      ),
    );
  }

  Widget _field(TextEditingController c, String label) => Padding(
    padding: const EdgeInsets.only(top: 10, bottom: 8),
    child: TextField(controller: c, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: label, border: const OutlineInputBorder())),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(label), Text(value, style: const TextStyle(fontWeight: FontWeight.bold))]),
  );
}

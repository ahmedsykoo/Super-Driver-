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
  AccessibilityStatus _status = const AccessibilityStatus(overlay: false, accessibility: false, monitoring: false);
  double _minPrice = 7.5;
  double _discount = 0;
  String _selectedApp = 'Uber';
  String get _appKey => _selectedApp.toLowerCase().replaceAll(' ', '_');
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
      _minPrice = p.getDouble('minPrice_uber') ?? p.getDouble('minPrice') ?? 7.5;
      _discount = p.getDouble('discount_uber') ?? p.getDouble('uberDiscount') ?? 0;
      _min.text = _minPrice.toString();
      _discountController.text = _discount.toString();
    });
    await _refreshStatus();
  }

  Future<void> _refreshStatus() async {
    final status = await AccessibilityListener.getStatus();
    if (mounted) setState(() => _status = status);
  }

  Future<void> _openOverlay() async {
    await AccessibilityListener.openOverlaySettings();
    if (mounted) await _refreshStatus();
  }

  Future<void> _openAccessibility() async {
    await AccessibilityListener.openAccessibilitySettings();
    if (mounted) await _refreshStatus();
  }

  Future<void> _toggleMonitoring() async {
    if (!_status.overlay) {
      await _openOverlay();
      return;
    }
    if (!_status.accessibility) {
      await _openAccessibility();
      return;
    }
    final ok = await AccessibilityListener.setMonitoringEnabled(!_status.monitoring);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تعذر تغيير حالة المتابعة')));
    }
    await _refreshStatus();
  }

  Future<void> _selectApp(String app) async {
    final p = await SharedPreferences.getInstance();
    final key = app.toLowerCase().replaceAll(' ', '_');
    if (!mounted) return;
    setState(() {
      _selectedApp = app;
      _minPrice = p.getDouble('minPrice_$key') ?? 7.5;
      _discount = p.getDouble('discount_$key') ?? 0;
      _min.text = _minPrice.toString();
      _discountController.text = _discount.toString();
    });
  }

  Future<void> _save() async {
    final min = double.tryParse(_min.text.replaceAll(',', '.')) ?? 7.5;
    final discount = (double.tryParse(_discountController.text.replaceAll(',', '.')) ?? 0).clamp(0, 100).toDouble();
    final p = await SharedPreferences.getInstance();
    await p.setDouble('minPrice_$_appKey', min);
    await p.setDouble('discount_$_appKey', discount);
    if (_selectedApp == 'Uber') {
      await p.setDouble('minPrice', min);
      await p.setDouble('uberDiscount', discount);
    }
    if (!mounted) return;
    setState(() { _minPrice = min; _discount = discount; });
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(isArabic ? 'تم حفظ الإعدادات' : 'Settings saved')));
  }

  Future<void> _readUber() async {
    final text = await AccessibilityListener.getScreenText();
    final price = PriceCalculator.extractPrice(text);
    final distance = PriceCalculator.extractDistance(text);
    if (mounted && price != null && distance != null) {
      setState(() => _trip = TripData(price: price, distance: distance, timestamp: DateTime.now()));
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
    _price.dispose(); _distance.dispose(); _min.dispose(); _discountController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Super Driver'), centerTitle: true,
          actions: [PopupMenuButton<String>(onSelected: (v) => widget.onLocaleChange(Locale(v)), itemBuilder: (_) => const [PopupMenuItem(value: 'ar', child: Text('العربية')), PopupMenuItem(value: 'en', child: Text('English'))])],
        ),
        body: AnimatedSwitcher(duration: const Duration(milliseconds: 300), child: _status.ready ? _settingsPage() : _activationPage()),
      ),
    );
  }

  Widget _activationPage() {
    return ListView(key: const ValueKey('activation'), padding: const EdgeInsets.all(16), children: [
      _sectionTitle('تفعيل Super Driver'),
      const SizedBox(height: 8),
      const Text('فعّل الخطوات الثلاث بالترتيب، وبعد اكتمالها سينتقل التطبيق تلقائياً إلى الإعدادات.', textAlign: TextAlign.center),
      const SizedBox(height: 18),
      _permissionRow(Icons.open_in_new, 'الظهور فوق التطبيقات', _status.overlay, _openOverlay),
      _permissionRow(Icons.accessibility_new, 'اكتشاف الرحلات تلقائياً', _status.accessibility, _openAccessibility),
      _permissionRow(Icons.radar, 'تشغيل متابعة الرحلات', _status.monitoring, _toggleMonitoring),
      const SizedBox(height: 12),
      Text(_status.ready ? 'التطبيق جاهز' : 'أكمل التفعيل من إعدادات الهاتف ثم ارجع للتطبيق', textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.bold, color: _status.ready ? Colors.green : Colors.orange.shade800)),
      const SizedBox(height: 8),
      const Text('يجب تفعيل الصلاحيات يدوياً من Android. التطبيق لا يستطيع منحها تلقائياً.', textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Colors.grey)),
    ]);
  }

  Widget _settingsPage() {
    final trip = _trip;
    final suitable = trip?.isSuitable(_minPrice, _discount) ?? false;
    return ListView(key: const ValueKey('settings'), padding: const EdgeInsets.all(16), children: [
      Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(children: [
        const Text('إعدادات Super Driver', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        SwitchListTile(title: const Text('تشغيل متابعة الرحلات'), subtitle: Text(_status.monitoring ? 'المتابعة تعمل الآن' : 'المتابعة متوقفة'), value: _status.monitoring, onChanged: (_) => _toggleMonitoring(), secondary: Icon(_status.monitoring ? Icons.play_circle : Icons.pause_circle, color: _status.monitoring ? Colors.green : Colors.red)),
      ]))),
      Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(children: [
        Text(trip == null ? 'في انتظار الرحلة...' : (suitable ? '✅ مناسب' : '❌ غير مناسب'), style: TextStyle(fontSize: 27, fontWeight: FontWeight.bold, color: trip == null ? null : (suitable ? Colors.green : Colors.red))),
        if (trip != null) ...[
          const SizedBox(height: 12), _row('سعر الرحلة', '${trip.price.toStringAsFixed(2)} EGP'), _row('المسافة', '${trip.distance.toStringAsFixed(1)} km'), _row('السعر/كم', '${trip.pricePerKm.toStringAsFixed(2)} EGP'), _row('بعد الخصم', '${trip.pricePerKmAfterDiscount(_discount).toStringAsFixed(2)} EGP'),
        ],
      ]))),
      const SizedBox(height: 14),
      Text('اختبار يدوي', style: Theme.of(context).textTheme.titleLarge),
      _field(_price, 'سعر الرحلة'), _field(_distance, 'المسافة (كم)'),
      FilledButton.icon(onPressed: _calculate, icon: const Icon(Icons.calculate), label: const Text('احسب الرحلة')),
      const SizedBox(height: 18),
      Text('إعدادات التطبيقات', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      DropdownButtonFormField<String>(
        value: _selectedApp,
        decoration: const InputDecoration(labelText: 'التطبيق', border: OutlineInputBorder()),
        items: const [DropdownMenuItem(value: 'Uber', child: Text('Uber')), DropdownMenuItem(value: 'inDrive', child: Text('inDrive')), DropdownMenuItem(value: 'DiDi', child: Text('DiDi'))],
        onChanged: (value) { if (value != null) _selectApp(value); },
      ),
      _field(_min, 'الحد الأدنى / كم'), _field(_discountController, 'خصم الشركة %'),
      FilledButton.tonalIcon(onPressed: _save, icon: const Icon(Icons.save), label: const Text('حفظ الإعدادات')),
    ]);
  }

  Widget _sectionTitle(String text) => Text(text, textAlign: TextAlign.center, style: const TextStyle(fontSize: 27, fontWeight: FontWeight.bold));
  Widget _permissionRow(IconData icon, String title, bool enabled, VoidCallback onPressed) => Card(color: Theme.of(context).colorScheme.surfaceContainerHighest, child: Padding(padding: const EdgeInsets.all(12), child: Column(children: [Row(children: [Icon(icon), const SizedBox(width: 10), Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w600))), Text(enabled ? '✅' : '❌', style: const TextStyle(fontSize: 20))]), const SizedBox(height: 8), SizedBox(width: double.infinity, child: FilledButton(onPressed: onPressed, child: Text(enabled ? 'تم التفعيل' : 'تفعيل')))])));
  Widget _field(TextEditingController c, String label) => Padding(padding: const EdgeInsets.only(top: 10, bottom: 8), child: TextField(controller: c, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: label, border: const OutlineInputBorder())));
  Widget _row(String label, String value) => Padding(padding: const EdgeInsets.symmetric(vertical: 5), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(label), Text(value, style: const TextStyle(fontWeight: FontWeight.bold))]));
}

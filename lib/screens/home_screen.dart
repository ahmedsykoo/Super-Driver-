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
      const SizedBox(height: 18),
      const Text('إعدادات التطبيقات', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
      const SizedBox(height: 8),
      _appCard('Uber', Icons.local_taxi, Colors.black87),
      _appCard('inDrive', Icons.directions_car, Colors.green.shade700),
      _appCard('DiDi', Icons.directions_car_filled, Colors.orange.shade800),
    ]);
  }

  Widget _appCard(String app, IconData icon, Color color) => Card(
    child: ListTile(
      leading: CircleAvatar(backgroundColor: color, child: Icon(icon, color: Colors.white)),
      title: Text('إعدادات $app', style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: const Text('الحد الأدنى، الخصم، ومسافة الوصول'),
      trailing: const Icon(Icons.chevron_left),
      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => AppSettingsPage(appName: app))),
    ),
  );

  Widget _sectionTitle(String text) => Text(text, textAlign: TextAlign.center, style: const TextStyle(fontSize: 27, fontWeight: FontWeight.bold));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
  }
  Widget _permissionRow(IconData icon, String title, bool enabled, VoidCallback onPressed) => Card(color: Theme.of(context).colorScheme.surfaceContainerHighest, child: Padding(padding: const EdgeInsets.all(12), child: Column(children: [Row(children: [Icon(icon), const SizedBox(width: 10), Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w600))), Text(enabled ? '✅' : '❌', style: const TextStyle(fontSize: 20))]), const SizedBox(height: 8), SizedBox(width: double.infinity, child: FilledButton(onPressed: onPressed, child: Text(enabled ? 'تم التفعيل' : 'تفعيل')))])));
  Widget _field(TextEditingController c, String label) => Padding(padding: const EdgeInsets.only(top: 10, bottom: 8), child: TextField(controller: c, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: label, border: const OutlineInputBorder())));
  Widget _row(String label, String value) => Padding(padding: const EdgeInsets.symmetric(vertical: 5), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(label), Text(value, style: const TextStyle(fontWeight: FontWeight.bold))]));
}

class AppSettingsPage extends StatefulWidget {
  final String appName;
  const AppSettingsPage({super.key, required this.appName});

  @override
  State<AppSettingsPage> createState() => _AppSettingsPageState();
}

class _AppSettingsPageState extends State<AppSettingsPage> {
  late final String _key;
  final _min = TextEditingController();
  final _discount = TextEditingController();
  bool _includePickupDistance = false;
  bool _enabled = true;

  @override
  void initState() {
    super.initState();
    _key = widget.appName.toLowerCase().replaceAll(' ', '_');
    _load();
  }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      _min.text = (p.getDouble('minPrice_$_key') ?? 7.5).toString();
      _discount.text = (p.getDouble('discount_$_key') ?? 0).toString();
      _includePickupDistance = p.getBool('includePickupDistance_$_key') ?? false;
      _enabled = p.getBool('enabled_$_key') ?? true;
    });
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    final min = double.tryParse(_min.text.replaceAll(',', '.')) ?? 7.5;
    final discount = (double.tryParse(_discount.text.replaceAll('%', '').replaceAll(',', '.').trim()) ?? 0).clamp(0, 100).toDouble();
    await p.setDouble('minPrice_$_key', min);
    await p.setDouble('discount_$_key', discount);
    await p.setDouble('discount_${_key}_percent', discount);
    await p.setBool('includePickupDistance_$_key', _includePickupDistance);
    await p.setBool('enabled_$_key', _enabled);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم حفظ إعدادات التطبيق')));
  }

  @override
  void dispose() {
    _min.dispose();
    _discount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('إعدادات ${widget.appName}')),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(children: [
        Text(widget.appName, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        SwitchListTile(title: const Text('تحليل التطبيق'), subtitle: Text(_enabled ? 'مفعّل' : 'متوقف'), value: _enabled, onChanged: (v) => setState(() => _enabled = v)),
        TextField(controller: _min, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'الحد الأدنى المقبول لكل كيلومتر', border: OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _discount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'خصم الشركة %', border: OutlineInputBorder())),
        SwitchListTile(title: const Text('احتساب مسافة الوصول إلى العميل'), subtitle: const Text('متوقف افتراضياً؛ عند تشغيله تُضاف مسافة الوصول إلى مسافة الرحلة'), value: _includePickupDistance, onChanged: (v) => setState(() => _includePickupDistance = v)),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _save, icon: const Icon(Icons.save), label: const Text('حفظ الإعدادات'))),
      ]))),
    ]),
  );
}

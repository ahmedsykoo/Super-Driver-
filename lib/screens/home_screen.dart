import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/trip_data.dart';
import '../services/accessibility_listener.dart';
import '../services/price_calculator.dart';
import '../services/trip_history.dart';
import 'debug_screen.dart';

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
  List<TripData> _history = [];
  String? _lastRecordedSignature;
  bool get isArabic => Localizations.localeOf(context).languageCode == 'ar';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    final history = await TripHistoryStore.load();
    if (!mounted) return;
    setState(() {
      _minPrice = p.getDouble('minPrice_uber') ?? p.getDouble('minPrice') ?? 7.5;
      _discount = p.getDouble('discount_uber') ?? p.getDouble('uberDiscount') ?? 0;
      _min.text = _minPrice.toString();
      _discountController.text = _discount.toString();
      _history = history;
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

  Future<void> _recordTrip(TripData trip) async {
    final signature = '${trip.price.toStringAsFixed(2)}|${trip.distance.toStringAsFixed(2)}';
    if (_lastRecordedSignature == signature) return;
    _lastRecordedSignature = signature;
    final history = await TripHistoryStore.add(trip);
    if (mounted) setState(() => _history = history);
  }

  Future<void> _readUber() async {
    final text = await AccessibilityListener.getScreenText();
    final price = PriceCalculator.extractPrice(text);
    final distance = PriceCalculator.extractDistance(text);
    if (price == null || distance == null || price <= 0 || distance <= 0) return;
    final trip = TripData(price: price, distance: distance, timestamp: DateTime.now());
    if (mounted) setState(() => _trip = trip);
    await _recordTrip(trip);
  }

  Future<void> _calculate() async {
    final price = double.tryParse(_price.text.replaceAll(',', '.'));
    final distance = double.tryParse(_distance.text.replaceAll(',', '.'));
    if (price == null || distance == null || price <= 0 || distance <= 0) return;
    final trip = TripData(price: price, distance: distance, timestamp: DateTime.now());
    setState(() => _trip = trip);
    await _recordTrip(trip);
  }

  Future<void> _clearHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_tr('مسح سجل الرحلات؟', 'Clear trip history?')),
        content: Text(_tr('سيتم حذف السجل المحفوظ على هذا الجهاز.', 'The history saved on this device will be deleted.')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(_tr('إلغاء', 'Cancel'))),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(_tr('مسح', 'Clear'))),
        ],
      ),
    );
    if (confirmed != true) return;
    await TripHistoryStore.clear();
    if (mounted) setState(() => _history = []);
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
          actions: [
            IconButton(
              icon: const Icon(Icons.bug_report),
              tooltip: _tr('تشخيص', 'Debug'),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const DebugScreen()),
              ),
            ),
            PopupMenuButton<String>(onSelected: (v) => widget.onLocaleChange(Locale(v)), itemBuilder: (_) => const [PopupMenuItem(value: 'ar', child: Text('العربية')), PopupMenuItem(value: 'en', child: Text('English'))]),
          ],
        ),
        body: AnimatedSwitcher(duration: const Duration(milliseconds: 300), child: _status.ready ? _settingsPage() : _activationPage()),
      ),
    );
  }

  Widget _activationPage() {
    return ListView(key: const ValueKey('activation'), padding: const EdgeInsets.all(16), children: [
      _sectionTitle(_tr('تفعيل Super Driver', 'Activate Super Driver')),
      const SizedBox(height: 8),
      Text(_tr('فعّل الخطوات الثلاث بالترتيب، وبعد اكتمالها سينتقل التطبيق تلقائياً إلى الإعدادات.', 'Enable the three steps in order. The app will move to settings when they are complete.'), textAlign: TextAlign.center),
      const SizedBox(height: 18),
      _permissionRow(Icons.open_in_new, _tr('الظهور فوق التطبيقات', 'Display over other apps'), _status.overlay, _openOverlay),
      _permissionRow(Icons.accessibility_new, _tr('اكتشاف الرحلات تلقائياً', 'Detect trips automatically'), _status.accessibility, _openAccessibility),
      _permissionRow(Icons.radar, _tr('تشغيل متابعة الرحلات', 'Trip monitoring'), _status.monitoring, _toggleMonitoring),
      const SizedBox(height: 12),
      Text(_status.ready ? _tr('التطبيق جاهز', 'App is ready') : _tr('أكمل التفعيل من إعدادات الهاتف ثم ارجع للتطبيق', 'Complete activation in Android settings, then return here'), textAlign: TextAlign.center, style: TextStyle(fontWeight: FontWeight.bold, color: _status.ready ? Colors.green : Colors.orange.shade800)),
      const SizedBox(height: 8),
      Text(_tr('يجب تفعيل الصلاحيات يدوياً من Android. التطبيق لا يستطيع منحها تلقائياً.', 'Permissions must be enabled manually in Android. The app cannot grant them automatically.'), textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: Colors.grey)),
    ]);
  }

  Widget _settingsPage() {
    final trip = _trip;
    final suitable = trip?.isSuitable(_minPrice, _discount) ?? false;
    return ListView(key: const ValueKey('settings'), padding: const EdgeInsets.all(16), children: [
      Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(children: [
        Text(_tr('إعدادات Super Driver', 'Super Driver settings'), style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        SwitchListTile(title: Text(_tr('تشغيل متابعة الرحلات', 'Trip monitoring')), subtitle: Text(_status.monitoring ? _tr('المتابعة تعمل الآن', 'Monitoring is active') : _tr('المتابعة متوقفة', 'Monitoring is paused')), value: _status.monitoring, onChanged: (_) => _toggleMonitoring(), secondary: Icon(_status.monitoring ? Icons.play_circle : Icons.pause_circle, color: _status.monitoring ? Colors.green : Colors.red)),
      ]))),
      Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(children: [
        Text(trip == null ? _tr('في انتظار الرحلة...', 'Waiting for a trip...') : (suitable ? _tr('✅ مناسب', '✅ Suitable') : _tr('❌ غير مناسب', '❌ Not suitable')), style: TextStyle(fontSize: 27, fontWeight: FontWeight.bold, color: trip == null ? null : (suitable ? Colors.green : Colors.red))),
        if (trip != null) ...[
          const SizedBox(height: 12), _row(_tr('سعر الرحلة', 'Trip fare'), '${trip.price.toStringAsFixed(2)} EGP'), _row(_tr('المسافة', 'Distance'), '${trip.distance.toStringAsFixed(1)} km'), _row(_tr('السعر/كم', 'Price/km'), '${trip.pricePerKm.toStringAsFixed(2)} EGP'), _row(_tr('بعد الخصم', 'After discount'), '${trip.pricePerKmAfterDiscount(_discount).toStringAsFixed(2)} EGP'),
        ],
      ]))),
      _manualTripCard(),
      if (_history.isNotEmpty) _historyCard(),
      const SizedBox(height: 18),
      Text(_tr('إعدادات خدمات أوبر', 'Uber service settings'), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
      const SizedBox(height: 8),
      _appCard('UberX', Icons.local_taxi, Colors.black87, settingsKey: 'uberx'),
      _appCard('UberX Saver', Icons.savings, Colors.black87, settingsKey: 'uberx_saver'),
      _appCard('UberX أولوية', Icons.bolt, Colors.black87, settingsKey: 'uberx_priority'),
      _appCard('Intercity', Icons.route, Colors.black87, settingsKey: 'intercity'),
      const SizedBox(height: 10),
      _appCard('Uber (افتراضي)', Icons.taxi_alert, Colors.blueGrey, settingsKey: 'uber'),
    ]);
  }

  Widget _manualTripCard() => Card(
    child: Padding(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(_tr('إضافة رحلة يدويًا', 'Add trip manually'), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          _field(_price, _tr('سعر الرحلة بالجنيه', 'Trip fare in EGP')),
          _field(_distance, _tr('مسافة الرحلة بالكيلومتر', 'Trip distance in km')),
          SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _calculate, icon: const Icon(Icons.add), label: Text(_tr('إضافة للسجل', 'Add to history')))),
        ],
      ),
    ),
  );

  Widget _historyCard() {
    final total = _history.fold<double>(0, (sum, trip) => sum + trip.price);
    final average = _history.isEmpty ? 0.0 : _history.fold<double>(0, (sum, trip) => sum + trip.pricePerKm) / _history.length;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(_tr('سجل الرحلات', 'Trip history'), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                IconButton(onPressed: _clearHistory, icon: const Icon(Icons.delete_outline), tooltip: _tr('مسح السجل', 'Clear history')),
              ],
            ),
            _row(_tr('عدد الرحلات', 'Trips'), '${_history.length}'),
            _row(_tr('إجمالي الأسعار', 'Total fare'), '${total.toStringAsFixed(2)} EGP'),
            _row(_tr('متوسط السعر/كم', 'Average price/km'), '${average.toStringAsFixed(2)} EGP'),
            const Divider(),
            ..._history.take(8).map((trip) => ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.route),
              title: Text('${trip.price.toStringAsFixed(2)} EGP • ${trip.pricePerKm.toStringAsFixed(2)} EGP/km'),
              subtitle: Text('${trip.distance.toStringAsFixed(1)} km • ${_formatDate(trip.timestamp)}'),
            )),
          ],
        ),
      ),
    );
  }

  String _formatDate(DateTime value) {
    final local = value.toLocal();
    final day = local.day.toString().padLeft(2, '0');
    final month = local.month.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '${day}/${month} ${hour}:${minute}';
  }

  Widget _appCard(String app, IconData icon, Color color, {required String settingsKey}) => Card(
    child: ListTile(
      leading: CircleAvatar(backgroundColor: color, child: Icon(icon, color: Colors.white)),
      title: Text(_tr('إعدادات $app', '$app settings'), style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: Text(_tr('الحد الأدنى، الخصم، ومسافة الوصول', 'Minimum, discount, and pickup distance')),
      trailing: const Icon(Icons.chevron_left),
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => AppSettingsPage(appName: app, settingsKey: settingsKey),
        ),
      ),
    ),
  );

  String _tr(String ar, String en) => isArabic ? ar : en;

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
  final String? settingsKey;
  const AppSettingsPage({super.key, required this.appName, this.settingsKey});

  @override
  State<AppSettingsPage> createState() => _AppSettingsPageState();
}

class _AppSettingsPageState extends State<AppSettingsPage> {
  bool get isArabic => Localizations.localeOf(context).languageCode == 'ar';
  String _tr(String ar, String en) => isArabic ? ar : en;
  late final String _key;
  final _min = TextEditingController();
  final _discount = TextEditingController();
  bool _includePickupDistance = false;
  bool _enabled = true;

  @override
  void initState() {
    super.initState();
    _key = widget.settingsKey ?? widget.appName.toLowerCase().replaceAll(' ', '_');
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
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(_tr('تم حفظ إعدادات التطبيق', 'App settings saved'))));
  }

  @override
  void dispose() {
    _min.dispose();
    _discount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(_tr('إعدادات ${widget.appName}', '${widget.appName} settings'))),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(children: [
        Text(widget.appName, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        SwitchListTile(title: Text(_tr('تحليل التطبيق', 'Analyze app')), subtitle: Text(_enabled ? _tr('مفعّل', 'Enabled') : _tr('متوقف', 'Disabled')), value: _enabled, onChanged: (v) => setState(() => _enabled = v)),
        TextField(controller: _min, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: _tr('الحد الأدنى المقبول لكل كيلومتر', 'Minimum acceptable per kilometer'), border: const OutlineInputBorder())),
        const SizedBox(height: 12),
        TextField(controller: _discount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: _tr('خصم الشركة %', 'Company discount %'), border: const OutlineInputBorder())),
        SwitchListTile(title: Text(_tr('احتساب مسافة الوصول إلى العميل', 'Include pickup distance')), subtitle: Text(_tr('متوقف افتراضياً؛ عند تشغيله تُضاف مسافة الوصول إلى مسافة الرحلة', 'Off by default; when enabled, pickup distance is added to trip distance')), value: _includePickupDistance, onChanged: (v) => setState(() => _includePickupDistance = v)),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _save, icon: const Icon(Icons.save), label: Text(_tr('حفظ الإعدادات', 'Save settings')))),
      ]))),
    ]),
  );
}

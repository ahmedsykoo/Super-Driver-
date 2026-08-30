import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/trip_data.dart';
import '../services/accessibility_listener.dart';
import '../services/price_calculator.dart';
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
          actions: [
            IconButton(
              icon: const Icon(Icons.bug_report),
              tooltip: 'Debug',
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
      const Text('إعدادات خدمات أوبر', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
      const SizedBox(height: 8),
      _appCard('UberX', Icons.local_taxi, Colors.black87, settingsKey: 'uberx'),
      _appCard('UberX Saver', Icons.savings, Colors.black87, settingsKey: 'uberx_saver'),
      _appCard('UberX أولوية', Icons.bolt, Colors.black87, settingsKey: 'uberx_priority'),
      _appCard('Intercity', Icons.route, Colors.black87, settingsKey: 'intercity'),
      const SizedBox(height: 10),
      _appCard('Uber (افتراضي)', Icons.taxi_alert, Colors.blueGrey, settingsKey: 'uber'),
    ]);
  }

  Widget _appCard(String app, IconData icon, Color color, {required String settingsKey}) => Card(
    child: ListTile(
      leading: CircleAvatar(backgroundColor: color, child: Icon(icon, color: Colors.white)),
      title: Text('إعدادات $app', style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: const Text('الحد الأدنى، الخصم، ومسافة الوصول'),
      trailing: const Icon(Icons.chevron_left),
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => AppSettingsPage(appName: app, settingsKey: settingsKey),
        ),
      ),
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
  final String? settingsKey;
  const AppSettingsPage({super.key, required this.appName, this.settingsKey});

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
}            "\\s*\\(\\s*(?:المسافة|مسافة|distance)\\s+" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    )

    // Pickup labels are kept separate from trip-distance labels so the
    // parser does not accidentally swallow the trip distance.
    private val pickupArOnDistance = Pattern.compile(
        "على\\s+بعد\\s+[0-9٠-٩۰-۹]+\\s*د\\s*" +
            "\\(?\\s*" + number + "\\s*\\)?\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArMinutesOnly = Pattern.compile(
        "على\\s+بعد\\s+[0-9٠-٩۰-۹]+\\s*(?:د(?:قيقة)?|دق|د)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArYibad = Pattern.compile(
        "يبعد\\s+" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArBaad = Pattern.compile(
        "بعد\\s+عنك\\s+" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupEn = Pattern.compile(
        "(?:pickup|pick[\\s-]?up)\\s*(?:distance)?\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )

    // Bare "X km" fallback where the trip distance is next to the price.
    private val bareDistance = Pattern.compile(
        "~?\\s*" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )

    // Price patterns – labelled fare first, then a bare currency amount.
    private val priceByLabel = Pattern.compile(
        "(?:السعر|سعر\\s+الرحلة|المجموع|الإجمالي|المبلغ|القبول\\s+مقابل|" +
            "total|fare|trip\\s+price|price)\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)",
        Pattern.CASE_INSENSITIVE
    )
    private val priceAfterNumber = Pattern.compile(
        "(?<![0-9٠-٩۰-۹])" + number +
            "\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)",
        Pattern.CASE_INSENSITIVE
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 120
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        applyMonitoringState(isMonitoringStored())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!isMonitoringStored()) {
            hideOverlay()
            return
        }
        if (!isSupportedRideApp(packageName) || !isAppEnabled(packageName)) {
            // Ignore system/status-bar accessibility events while the active window
            // is still a supported ride app; otherwise the result would flash.
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (activePackage == null || !isSupportedRideApp(activePackage)) hideOverlay()
            return
        }

        val text = collectCurrentWindowText()
        latestText = text
        val trip = parseTrip(text, packageName)
        if (trip != null) {
            mainHandler.removeCallbacks(clearStaleResult)
            showResult(trip.first, trip.second, packageName)
        } else {
            // No trip found – just clear the stale result after 5s so
            // the overlay doesn't linger on a trip that's already
            // been accepted/rejected by the driver.
            mainHandler.removeCallbacks(clearStaleResult)
            mainHandler.postDelayed(clearStaleResult, 5000L)
        }
    }

    private var lastPolledText = ""
    private val pollIntervalMs = 1000L
    private val pollRunnable: Runnable = object : Runnable {
        override fun run() {
            try {
                pollForUpdates()
            } finally {
                if (isMonitoringStored()) {
                    mainHandler.postDelayed(this, pollIntervalMs)
                }
            }
        }
    }

    /**
     * Polls the active window's accessibility tree every
     * [pollIntervalMs] and re-runs the parser when the text changes.
     * Necessary because Uber's live-offer card updates in place – the
     * Accessibility tree may not fire a TYPE_WINDOW_CONTENT_CHANGED
     * event for the new offer, so without polling we would only see
     * the historical trip the user happened to have open when the
     * service started.
     */
    private fun pollForUpdates() {
        if (!isMonitoringStored()) return
        // Read every window's text (not just the active one). The
        // offer card may live in a non-active window (a system
        // dialog, a heads-up notification, a chat bubble…) so the
        // active package check would miss it. We try every text we
        // can find, and if any of it contains a price + distance
        // for an Uber offer, we display the overlay.
        val text = collectAllVisibleText()
        if (text.isBlank()) return
        // Always re-parse every poll – the cost is tiny (a few
        // regex matches on a string of a few hundred chars) and
        // skipping when "the text didn't change" caused us to miss
        // live offers that re-used the same surrounding text but
        // changed the price or the distance.
        latestText = text
        lastPolledText = text
        val trip = parseTrip(text, "com.ubercab.driver")
            ?: parseTrip(text, "com.uber.client")
            ?: return
        mainHandler.removeCallbacks(clearStaleResult)
        showResult(trip.first, trip.second, "com.ubercab.driver")
    }

    /**
     * Collects text from every accessibility window currently shown
     * on screen, regardless of which package owns it. This is the
     * "look everywhere" version of [collectCurrentWindowText] used
     * by the polling loop so a live offer card sitting in a
     * non-active window (e.g. a dialog) is not missed.
     */
    private fun collectAllVisibleText(): String {
        val out = StringBuilder()
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            try {
                collectText(activeRoot, out, 0)
            } finally {
                activeRoot.recycle()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    try {
                        collectText(root, out, 0)
                    } finally {
                        root.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
        return out.toString().trim()
    }

    private fun startPolling() {
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, pollIntervalMs)
    }

    private fun stopPolling() {
        mainHandler.removeCallbacks(pollRunnable)
    }

    private fun requestLiveOcr(packageName: String, accessibilityText: String) {
        // OCR was previously used as a fallback when the accessibility
        // tree didn't contain the trip distance. It took a screenshot
        // of the live offer card and ran ML Kit text recognition on
        // it. The cost (CPU, memory, screenshot permission, and the
        // 3-second throttle that sometimes lagged the overlay) was
        // much higher than the benefit: in practice the Uber
        // accessibility tree always contains the offer text, and
        // when it didn't, the polling loop (pollForUpdates) catches
        // the next update within 1.5 s anyway.
        //
        // The function is kept as a no-op so callers don't need to
        // be updated; it can be reintroduced later if a specific
        // layout requires it.
    }

    private fun isSupportedRideApp(packageName: String): Boolean {
        // Uber-only mode while the Uber flow is being tuned.
        return packageName == "com.ubercab.driver" ||
            packageName == "com.uber.client"
    }

    private fun settingsKey(packageName: String): String = "uber"

    private fun isAppEnabled(packageName: String): Boolean {
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        return prefs.all["flutter.enabled_${settingsKey(packageName)}"] as? Boolean ?: true
    }

    private fun collectCurrentWindowText(): String {
        val out = StringBuilder()
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            try {
                collectText(activeRoot, out, 0)
            } finally {
                activeRoot.recycle()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    try {
                        collectText(root, out, 0)
                    } finally {
                        root.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
        return out.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 80) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectText(child, out, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun parseTrip(raw: String, packageName: String): Pair<Double, Double>? {
        val text = normalizeDigits(raw)
        val price = findPrice(text) ?: return null
        // Pickup distance is ALWAYS added to the trip distance so the
        // price/km reflects the total distance the driver will travel
        // (pickup + trip). The per-app includePickupDistance setting is
        // kept for back-compat with the settings UI but is no longer
        // consulted here.
        val distance = extractTripDistance(text, includePickupDistance = true) ?: return null
        if (price <= 0.0 || distance <= 0.0) return null
        return Pair(price, distance)
    }

    /**
     * Returns the total distance the driver will travel to fulfil this
     * trip: the pickup distance to the rider PLUS the trip distance to
     * the destination. If only one is available, that value is used as-is.
     */
    private fun extractTripDistance(text: String, includePickupDistance: Boolean): Double? {
        val pickup = pickupMax(text)

        // 1. Strong trip labels win outright.
        maxFrom(tripDistanceLabeledStrong, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }
        maxFrom(tripDistanceParen, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }
        maxFrom(tripDistanceAny, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }

        // 2. Uber's duration block – the inner distance is the trip.
        maxFrom(tripInDurationAr, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }

        // 3. Bare distance fallback, used only when it is not part of a pickup label.
        val pricePos = firstPricePos(text)
        val bare = pickBareClosestToPrice(text, pricePos, pickup)
        if (bare != null) {
            return combineWithPickup(bare, pickup, includePickupDistance)
        }

        // 4. Last resort: if we have a pickup but no trip, use the pickup.
        if (includePickupDistance) return pickup
        return null
    }

    /**
     * Adds the pickup distance to the trip distance when the user opted
     * in (or by default — pickup is now always added to the trip).
     */
    private fun combineWithPickup(trip: Double, pickup: Double?, includePickupDistance: Boolean): Double {
        if (includePickupDistance && pickup != null && pickup > 0) return trip + pickup
        return trip
    }

    private data class Hit(val value: Double, val start: Int, val end: Int)

    private fun collect(re: Pattern, text: String): List<Hit> {
        val out = mutableListOf<Hit>()
        val m = re.matcher(text)
        while (m.find()) {
            val v = m.group(1)?.replace(',', '.')?.toDoubleOrNull()
            if (v != null && v > 0) out.add(Hit(v, m.start(), m.end()))
        }
        return out
    }

    private fun maxFrom(re: Pattern, text: String): Double? {
        var best: Double? = null
        for (h in collect(re, text)) {
            if (best == null || h.value > best) best = h.value
        }
        return best
    }

    private fun pickupMax(text: String): Double? {
        var best: Double? = null
        // Patterns that already include the number
        for (re in arrayOf(pickupArOnDistance, pickupArYibad, pickupArBaad, pickupEn)) {
            for (h in collect(re, text)) {
                if (best == null || h.value > best) best = h.value
            }
        }
        // "على بعد 5 د" alone (without the distance) – we can't read the
        // distance from it directly, so skip.
        return best
    }

    private fun firstPricePos(text: String): Int? {
        val pm = priceByLabel.matcher(text)
        if (pm.find()) return pm.start()
        val am = priceAfterNumber.matcher(text)
        if (am.find()) return am.start()
        return null
    }

    private fun pickBareClosestToPrice(text: String, pricePos: Int?, ignorePickup: Double?): Double? {
        val pickupRanges = collect(pickupArOnDistance, text) +
            collect(pickupArYibad, text) +
            collect(pickupArBaad, text) +
            collect(pickupEn, text)
        val bare = collect(bareDistance, text).filter { b ->
            pickupRanges.none { it.start < b.end && b.start < it.end }
        }
        if (bare.isEmpty()) return null
        if (ignorePickup != null) {
            // The caller asked for pickup+pickup-distance mode; the bare
            // fallback is irrelevant.
            return null
        }
        if (pricePos == null) {
            // No price anchor – take the smallest remaining distance.
            var v = bare.first().value
            for (h in bare) if (h.value < v) v = h.value
            return v
        }
        var best = bare.first()
        for (b in bare) {
            val gapBest = kotlin.math.abs(best.start - pricePos)
            val gapHere = kotlin.math.abs(b.start - pricePos)
            if (gapHere < gapBest || (gapHere == gapBest && b.value < best.value)) {
                best = b
            }
        }
        return best.value
    }

    private fun findPrice(text: String): Double? {
        val labeled = priceByLabel.matcher(text)
        if (labeled.find()) return labeled.group(1)?.replace(',', '.')?.toDoubleOrNull()
        // No labelled fare – use the FIRST bare "<number> EGP" match.
        // Taking the first match reads the headline offer price.
        val after = priceAfterNumber.matcher(text)
        if (after.find()) return after.group(1)?.replace(',', '.')?.toDoubleOrNull()
        return null
    }

    private fun normalizeDigits(value: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val out = StringBuilder(value.length)
        for (ch in value) {
            val a = arabic.indexOf(ch)
            val p = persian.indexOf(ch)
            when {
                a >= 0 -> out.append(('0'.code + a).toChar())
                p >= 0 -> out.append(('0'.code + p).toChar())
                ch == '،' || ch == '٫' -> out.append('.')
                ch == '٬' -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun isMonitoringStored(): Boolean =
        getSharedPreferences("super_driver", MODE_PRIVATE).getBoolean("monitoring_enabled", false)

    private fun applyMonitoringState(enabled: Boolean) {
        showStatusOverlay(enabled)
        if (!enabled) {
            overlayView?.let { view ->
                try {
                    if (view.parent != null) windowManager?.removeView(view)
                } catch (_: Exception) { }
                overlayView = null
                resultParams = null
            }
            stopPolling()
        } else {
            startPolling()
        }
    }

    private fun showStatusOverlay(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        val view = statusOverlayView ?: TextView(this).apply {
            text = "SD"
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(18, 14, 18, 14)
            elevation = 12f
        }.also { statusOverlayView = it }
        view.text = if (enabled) "SD ✓" else "SD"
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) Color.rgb(20, 150, 75) else Color.rgb(205, 45, 45))
        }
        if (view.parent == null) {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val saved = getSharedPreferences("super_driver", MODE_PRIVATE)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                y = saved.getInt("status_y", 180)
                x = saved.getInt("status_x", 18)
            }
            statusParams = params
            attachDrag(view, params, "status")
            try {
                windowManager?.addView(view, params)
            } catch (_: Exception) {
                statusOverlayView = null
            }
        }
    }

    private fun showResult(price: Double, distance: Double, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val settingsKey = settingsKey(packageName)
        val minPrice = readDouble(
            prefs.all["flutter.minPrice_$settingsKey"] ?: prefs.all["flutter.minPrice"],
            7.5
        ).coerceAtLeast(0.0)
        val discount = readDouble(
            prefs.all["flutter.discount_$settingsKey"]
                ?: prefs.all["flutter.discount_${settingsKey}_percent"]
                ?: prefs.all["flutter.uberDiscount"],
            0.0
        ).coerceIn(0.0, 100.0)

        // Apply the company percentage to the fare first, then divide by
        // the configured trip distance. Pickup distance is excluded by default.
        val fareAfterDiscount = price * (1.0 - discount / 100.0)
        val net = fareAfterDiscount / distance
        val suitable = net >= minPrice
        val appLabel = "Uber"

        val signature = "$packageName|${fmt(price)}|${fmt(distance)}|${fmt(net)}|$suitable"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1000L) return
        lastSignature = signature
        lastShownAt = now

        val root = overlayView as? LinearLayout ?: createOverlay().also { overlayView = it }
        val title = root.findViewWithTag<TextView>("title")
        val details = root.findViewWithTag<TextView>("details")

        title.text = if (suitable) "✓ مناسب • $appLabel" else "✕ غير مناسب • $appLabel"
        details.text = if (suitable) {
            "السعر مناسب\n${fmt(net)} ج.م/كم بعد الخصم"
        } else {
            "السعر غير مناسب\nالمطلوب ${fmt(minPrice)} ج.م/كم\nالحالي ${fmt(net)} ج.م/كم بعد الخصم"
        }
        title.setTextColor(Color.WHITE)
        details.setTextColor(Color.WHITE)
        root.setBackgroundColor(if (suitable) Color.rgb(20, 132, 72) else Color.rgb(198, 48, 48))
        root.visibility = View.VISIBLE

        if (root.parent == null) {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val saved = getSharedPreferences("super_driver", MODE_PRIVATE)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                y = saved.getInt("result_y", 130)
                x = saved.getInt("result_x", 16)
            }
            resultParams = params
            attachDrag(root, params, "result")
            try {
                windowManager?.addView(root, params)
            } catch (_: Exception) {
                overlayView = null
            }
        }
    }

    private fun createOverlay(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(26, 14, 26, 14)
            elevation = 12f
        }
        val title = TextView(this).apply {
            tag = "title"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val details = TextView(this).apply {
            tag = "details"
            textSize = 14f
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 0)
        }
        container.addView(title, LinearLayout.LayoutParams(-2, -2))
        container.addView(details, LinearLayout.LayoutParams(-2, -2))
        return container
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams, key: String) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 4 || kotlin.math.abs(dy) > 4) moved = true
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        getSharedPreferences("super_driver", MODE_PRIVATE).edit()
                            .putInt("${key}_x", params.x).putInt("${key}_y", params.y).apply()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun readDouble(value: Any?, fallback: Double): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.replace("%", "").replace(',', '.').trim().toDoubleOrNull() ?: fallback
        else -> fallback
    }

    private fun hideOverlay() {
        mainHandler.removeCallbacks(clearStaleResult)
        overlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) {
                // The Android window may already have been removed.
            }
        }
        overlayView = null
        lastSignature = ""
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        statusOverlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) { }
        }
        statusOverlayView = null
        latestText = ""
        latestOcrText = ""
        instance = null
        super.onDestroy()
    }
}

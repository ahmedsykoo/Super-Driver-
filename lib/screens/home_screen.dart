import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/trip_data.dart';
import '../services/accessibility_listener.dart';
import '../services/price_calculator.dart';
import '../services/trip_history.dart';
import 'debug_screen.dart';

class HomeScreen extends StatefulWidget {
  final ValueChanged<Locale> onLocaleChange;
  final VoidCallback onThemeToggle;
  final bool isDark;

  const HomeScreen({
    super.key,
    required this.onLocaleChange,
    required this.onThemeToggle,
    required this.isDark,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  TripData? _trip;
  AccessibilityStatus _status = const AccessibilityStatus(overlay: false, accessibility: false, monitoring: false);
  double _minPrice = 7.5;
  double _discount = 0;
  final _price = TextEditingController();
  final _distance = TextEditingController();
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: Colors.red.shade800,
          behavior: SnackBarBehavior.floating,
          content: Text(_tr('تعذر تغيير حالة المتابعة', 'Could not change monitoring state')),
        ),
      );
    }
    await _refreshStatus();
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
    if (price == null || distance == null || price <= 0 || distance <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: Colors.amber.shade900,
          behavior: SnackBarBehavior.floating,
          content: Text(_tr('يرجى إدخال السعر والمسافة أولاً', 'Please enter fare and distance first')),
        ),
      );
      return;
    }
    final trip = TripData(price: price, distance: distance, timestamp: DateTime.now());
    setState(() => _trip = trip);
    await _recordTrip(trip);
    _price.clear();
    _distance.clear();
  }

  Future<void> _clearHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Theme.of(context).cardColor,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            const Icon(Icons.delete_sweep_rounded, color: Colors.redAccent),
            const SizedBox(width: 8),
            Text(_tr('مسح سجل الرحلات؟', 'Clear trip history?')),
          ],
        ),
        content: Text(_tr('سيتم مسح جميع الرحلات المحفوظة على هذا الجهاز نهائياً.', 'All recorded trips on this device will be deleted permanently.')),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_tr('إلغاء', 'Cancel'), style: const TextStyle(color: Color(0xFF94A3B8))),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red.shade700),
            onPressed: () => Navigator.pop(context, true),
            child: Text(_tr('مسح الآن', 'Clear Now')),
          ),
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
    _price.dispose();
    _distance.dispose();
    super.dispose();
  }

  String _tr(String ar, String en) => isArabic ? ar : en;

  @override
  Widget build(BuildContext context) {
    final isDark = widget.isDark;
    final cardBg = isDark ? const Color(0xFF1E293B) : Colors.white;
    final borderColor = isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0);

    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              // Header section
              _buildHeader(isDark),
              const SizedBox(height: 14),

              // Main Service Status Card (حالة الخدمة)
              _buildServiceStatusCard(cardBg, borderColor),
              const SizedBox(height: 16),

              // Live Trip Result Card
              _buildLiveTripCard(cardBg, borderColor),
              const SizedBox(height: 16),

              // Quick Fare Calculator
              _buildManualCalculatorCard(cardBg, borderColor),
              const SizedBox(height: 16),

              // Uber Categories Section
              _buildUberCategoriesSection(cardBg, borderColor),
              const SizedBox(height: 16),

              // Trip History Section
              if (_history.isNotEmpty) _buildHistoryCard(cardBg, borderColor),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // App Title & Subtitle + Logo
        Row(
          children: [
            Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFFEAB308).withOpacity(0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Image.asset(
                  'assets/images/logo.png',
                  width: 52,
                  height: 52,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => Container(
                    width: 52,
                    height: 52,
                    color: const Color(0xFF0284C7),
                    child: const Icon(Icons.local_taxi_rounded, color: Colors.white, size: 28),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Super Driver',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.5,
                  ),
                ),
                Text(
                  _tr('قرار الرحلة في لحظة', 'Trip decision in an instant'),
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                  ),
                ),
              ],
            ),
          ],
        ),

        // Action Buttons: Theme Mode Toggle & Debug
        Row(
          children: [
            IconButton(
              icon: const Icon(Icons.bug_report_rounded, color: Color(0xFF0284C7), size: 22),
              tooltip: _tr('تشخيص', 'Debug'),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const DebugScreen()),
              ),
            ),
            InkWell(
              onTap: widget.onThemeToggle,
              borderRadius: BorderRadius.circular(10),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF1E293B) : const Color(0xFFE2E8F0),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
                      size: 16,
                      color: isDark ? const Color(0xFFFDE047) : const Color(0xFF0F172A),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      isDark ? _tr('الوضع الفاتح', 'Light Mode') : _tr('الوضع الداكن', 'Dark Mode'),
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: isDark ? Colors.white : const Color(0xFF0F172A),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildServiceStatusCard(Color cardBg, Color borderColor) {
    final permissionsGranted = _status.overlay && _status.accessibility;
    final isRunning = _status.monitoring && permissionsGranted;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: borderColor, width: 1.5),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(widget.isDark ? 0.25 : 0.05),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header: "حالة الخدمة"
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _tr('حالة الخدمة', 'Service Status'),
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: widget.isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                ),
              ),
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isRunning ? const Color(0xFF10B981) : Colors.redAccent,
                  boxShadow: [
                    BoxShadow(
                      color: (isRunning ? const Color(0xFF10B981) : Colors.redAccent).withOpacity(0.6),
                      blurRadius: 8,
                      spreadRadius: 2,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Main Headline with green/red indicator
          Row(
            children: [
              Container(
                width: 18,
                height: 18,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isRunning
                      ? const Color(0xFF22C55E)
                      : (permissionsGranted ? const Color(0xFFEF4444) : const Color(0xFFF59E0B)),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  isRunning
                      ? _tr('يعمل الآن Super Driver', 'Super Driver is Running Now')
                      : (permissionsGranted
                          ? _tr('Super Driver متوقف مؤقتًا', 'Super Driver is Paused')
                          : _tr('بانتظار استكمال التصاريح', 'Awaiting Permissions')),
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                    color: isRunning
                        ? const Color(0xFF16A34A)
                        : (permissionsGranted ? const Color(0xFFDC2626) : const Color(0xFFD97706)),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Informative notice description (matching user's screenshot layout)
          Text(
            _tr(
              'يعمل مع Uber فقط. يقرأ بيانات عروض الرحلات الظاهرة لتحليل السعر والمسافة وحساب الصافي/كم فوراً. لا يتم حفظ أي بيانات خارج عروض الرحلات.',
              'Dedicated to Uber only. Reads visible trip offer data to calculate fare, distance, and net/km instantly. No personal data is saved.',
            ),
            style: TextStyle(
              fontSize: 13,
              height: 1.5,
              color: widget.isDark ? const Color(0xFF94A3B8) : const Color(0xFF475569),
            ),
          ),
          const SizedBox(height: 16),

          // Permissions Banner (التصاريح مكتملة)
          InkWell(
            onTap: () {
              if (!_status.overlay) {
                _openOverlay();
              } else if (!_status.accessibility) {
                _openAccessibility();
              }
            },
            borderRadius: BorderRadius.circular(12),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
              decoration: BoxDecoration(
                color: permissionsGranted
                    ? const Color(0xFFECFDF5)
                    : const Color(0xFFFFFBEB),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: permissionsGranted
                      ? const Color(0xFFA7F3D0)
                      : const Color(0xFFFDE68A),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    permissionsGranted ? Icons.check_rounded : Icons.info_outline_rounded,
                    color: permissionsGranted ? const Color(0xFF059669) : const Color(0xFFD97706),
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    permissionsGranted
                        ? _tr('التصاريح مكتملة ✓', 'Permissions Complete ✓')
                        : _tr('اضغط هنا لاستكمال تصاريح الأندرويد ⚠️', 'Tap here to complete permissions ⚠️'),
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: permissionsGranted ? const Color(0xFF059669) : const Color(0xFFB45309),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 14),

          // Big Prominent Action Button (matching the orange/green toggle in screenshot)
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton(
              onPressed: _toggleMonitoring,
              style: FilledButton.styleFrom(
                backgroundColor: isRunning
                    ? const Color(0xFFEA580C) // Vibrant Orange
                    : (permissionsGranted ? const Color(0xFF16A34A) : const Color(0xFF0284C7)),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                elevation: 2,
              ),
              child: Text(
                isRunning
                    ? _tr('إيقاف التطبيق مؤقتًا', 'Pause App Temporarily')
                    : (permissionsGranted
                        ? _tr('تشغيل التطبيق الآن', 'Start App Now')
                        : _tr('استكمال التفعيل الآن', 'Complete Setup Now')),
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w900,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLiveTripCard(Color cardBg, Color borderColor) {
    final trip = _trip;
    final suitable = trip?.isSuitable(_minPrice, _discount) ?? false;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: trip == null
              ? borderColor
              : (suitable ? const Color(0xFF10B981) : Colors.redAccent),
          width: 1.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(
                    trip == null
                        ? Icons.radar_rounded
                        : (suitable ? Icons.check_circle_rounded : Icons.cancel_rounded),
                    color: trip == null
                        ? const Color(0xFF0284C7)
                        : (suitable ? const Color(0xFF10B981) : Colors.redAccent),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    trip == null
                        ? _tr('في انتظار عرض رحلة أوبر...', 'Waiting for Uber trip...')
                        : (suitable ? _tr('✅ سعر مناسب', '✅ Suitable Fare') : _tr('❌ سعر غير مناسب', '❌ Unsuitable Fare')),
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                      color: trip == null
                          ? (widget.isDark ? Colors.white : const Color(0xFF0F172A))
                          : (suitable ? const Color(0xFF16A34A) : Colors.redAccent),
                    ),
                  ),
                ],
              ),
              if (trip != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: widget.isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '${trip.pricePerKmAfterDiscount(_discount).toStringAsFixed(2)} EGP/km',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                      color: widget.isDark ? const Color(0xFFFDE047) : const Color(0xFFB45309),
                    ),
                  ),
                ),
            ],
          ),
          if (trip != null) ...[
            const SizedBox(height: 14),
            Divider(color: borderColor, height: 1),
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _metricItem(_tr('سعر الرحلة', 'Trip Fare'), '${trip.price.toStringAsFixed(2)} EGP', Icons.attach_money_rounded),
                _metricItem(_tr('المسافة', 'Distance'), '${trip.distance.toStringAsFixed(1)} km', Icons.route_rounded),
                _metricItem(_tr('الإجمالي/كم', 'Gross/km'), '${trip.pricePerKm.toStringAsFixed(2)} EGP', Icons.speed_rounded),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildManualCalculatorCard(Color cardBg, Color borderColor) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.calculate_outlined, color: Color(0xFF0284C7)),
              const SizedBox(width: 8),
              Text(
                _tr('حاسبة الأسعار السريعة', 'Quick Fare Calculator'),
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: widget.isDark ? Colors.white : const Color(0xFF0F172A),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _price,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    labelText: _tr('السعر (ج.م)', 'Fare (EGP)'),
                    prefixIcon: const Icon(Icons.money_rounded, size: 20),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: _distance,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    labelText: _tr('المسافة (كم)', 'Distance (km)'),
                    prefixIcon: const Icon(Icons.add_road_rounded, size: 20),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 44,
            child: FilledButton.icon(
              onPressed: _calculate,
              icon: const Icon(Icons.play_arrow_rounded, size: 20),
              label: Text(_tr('احسب الرحلة وأضفها للسجل', 'Calculate & Add to History')),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUberCategoriesSection(Color cardBg, Color borderColor) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              _tr('إعدادات خدمات أوبر', 'Uber Service Settings'),
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: widget.isDark ? Colors.white : const Color(0xFF0F172A),
              ),
            ),
            Text(
              _tr('8 خدمات مدعومة', '8 supported plans'),
              style: TextStyle(fontSize: 12, color: widget.isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
            ),
          ],
        ),
        const SizedBox(height: 10),

        _appCard('UberX', Icons.local_taxi_rounded, const Color(0xFF0284C7), settingsKey: 'uberx', subtitle: _tr('الرحلات العادية (الأساسية)', 'Standard trips')),
        _appCard('UberX Saver', Icons.savings_rounded, const Color(0xFF10B981), settingsKey: 'uberx_saver', subtitle: _tr('رحلات التوفير (Saver)', 'Saver trips')),
        _appCard('UberX أولوية', Icons.bolt_rounded, const Color(0xFFF59E0B), settingsKey: 'uberx_priority', subtitle: _tr('رحلات الأولوية السريعة', 'Priority trips')),
        _appCard('Uber Comfort', Icons.airline_seat_recline_extra_rounded, const Color(0xFF6366F1), settingsKey: 'comfort', subtitle: _tr('سيارات أحدث وأوسع', 'Premium & comfortable cars')),
        _appCard('UberXL', Icons.airport_shuttle_rounded, const Color(0xFF8B5CF6), settingsKey: 'uberxl', subtitle: _tr('سيارات عائلية 6 ركاب', '6-passenger family cars')),
        _appCard('Intercity (بين المدن)', Icons.alt_route_rounded, const Color(0xFF14B8A6), settingsKey: 'intercity', subtitle: _tr('سفر ورحلات بين المحافظات', 'Long-distance highway trips')),
        _appCard('Uber Connect (طرود)', Icons.inventory_2_rounded, const Color(0xFF3B82F6), settingsKey: 'connect', subtitle: _tr('توصيل الطرود والطلبات', 'Package delivery trips')),
        _appCard('Uber Moto / سكوتر', Icons.two_wheeler_rounded, const Color(0xFFF97316), settingsKey: 'scooter', subtitle: _tr('رحلات الموتوسيكل والسكوتر', 'Motorcycle & scooter trips')),
        _appCard('Uber (افتراضي)', Icons.taxi_alert_rounded, const Color(0xFF64748B), settingsKey: 'uber', subtitle: _tr('الإعدادات العامة لجميع رحلات أوبر', 'General fallback settings')),
      ],
    );
  }

  Widget _buildHistoryCard(Color cardBg, Color borderColor) {
    final total = _history.fold<double>(0, (sum, trip) => sum + trip.price);
    final average = _history.isEmpty ? 0.0 : _history.fold<double>(0, (sum, trip) => sum + trip.pricePerKm) / _history.length;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.history_rounded, color: Color(0xFF0284C7)),
                  const SizedBox(width: 8),
                  Text(
                    _tr('سجل الرحلات', 'Trip History'),
                    style: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                      color: widget.isDark ? Colors.white : const Color(0xFF0F172A),
                    ),
                  ),
                ],
              ),
              IconButton(
                onPressed: _clearHistory,
                icon: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent),
                tooltip: _tr('مسح السجل', 'Clear history'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _metricItem(_tr('الرحلات', 'Trips'), '${_history.length}', Icons.local_taxi_rounded),
              _metricItem(_tr('الإجمالي', 'Total'), '${total.toStringAsFixed(0)} EGP', Icons.account_balance_wallet_rounded),
              _metricItem(_tr('متوسط/كم', 'Avg/km'), '${average.toStringAsFixed(2)} EGP', Icons.trending_up_rounded),
            ],
          ),
          const SizedBox(height: 14),
          Divider(color: borderColor),
          ..._history.take(6).map((trip) => ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: widget.isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.route_rounded, size: 20, color: Color(0xFF0284C7)),
            ),
            title: Text(
              '${trip.price.toStringAsFixed(2)} EGP  •  ${trip.distance.toStringAsFixed(1)} km',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: widget.isDark ? Colors.white : const Color(0xFF0F172A),
              ),
            ),
            subtitle: Text(
              '${_formatDate(trip.timestamp)}  •  ${trip.pricePerKm.toStringAsFixed(2)} EGP/km',
              style: TextStyle(
                color: widget.isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                fontSize: 12,
              ),
            ),
          )),
        ],
      ),
    );
  }

  Widget _metricItem(String label, String value, IconData icon) {
    return Column(
      children: [
        Icon(icon, size: 20, color: const Color(0xFF64748B)),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: widget.isDark ? Colors.white : const Color(0xFF0F172A),
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            color: widget.isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
          ),
        ),
      ],
    );
  }

  String _formatDate(DateTime value) {
    final local = value.toLocal();
    final day = local.day.toString().padLeft(2, '0');
    final month = local.month.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '$day/$month $hour:$minute';
  }

  Widget _appCard(String app, IconData icon, Color color, {required String settingsKey, String? subtitle}) {
    final isDark = widget.isDark;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
      ),
      child: ListTile(
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: color.withOpacity(0.15),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, color: color, size: 22),
        ),
        title: Text(
          app,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 15,
            color: isDark ? Colors.white : const Color(0xFF0F172A),
          ),
        ),
        subtitle: Text(
          subtitle ?? _tr('الحد الأدنى، الخصم ومسافة الوصول', 'Min rate, discount & pickup'),
          style: TextStyle(
            fontSize: 12,
            color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
          ),
        ),
        trailing: const Icon(Icons.chevron_left_rounded, color: Color(0xFF94A3B8)),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => AppSettingsPage(appName: app, settingsKey: settingsKey, color: color, icon: icon),
          ),
        ),
      ),
    );
  }
}

class AppSettingsPage extends StatefulWidget {
  final String appName;
  final String? settingsKey;
  final Color? color;
  final IconData? icon;

  const AppSettingsPage({
    super.key,
    required this.appName,
    this.settingsKey,
    this.color,
    this.icon,
  });

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
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: const Color(0xFF10B981),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        content: Text(_tr('تم حفظ إعدادات ${widget.appName} بنجاح', '${widget.appName} settings saved')),
      ),
    );
  }

  void _setDiscount(double val) {
    setState(() {
      _discount.text = val.toString();
    });
  }

  @override
  void dispose() {
    _min.dispose();
    _discount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final themeColor = widget.color ?? const Color(0xFF0284C7);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? const Color(0xFF1E293B) : Colors.white;
    final borderColor = isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0);

    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_tr('إعدادات ${widget.appName}', '${widget.appName} Settings')),
        ),
        body: ListView(
          padding: const EdgeInsets.all(18),
          children: [
            // Service Card Header
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: borderColor),
              ),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: themeColor.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(widget.icon ?? Icons.local_taxi_rounded, color: themeColor, size: 32),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.appName,
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: isDark ? Colors.white : const Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          _enabled ? _tr('التحليل مفعّل لهذه الفئة ✓', 'Active & monitored ✓') : _tr('التحليل معطل لهذه الفئة', 'Disabled'),
                          style: TextStyle(
                            fontSize: 13,
                            color: _enabled ? const Color(0xFF10B981) : const Color(0xFF94A3B8),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Switch(
                    value: _enabled,
                    activeColor: const Color(0xFF10B981),
                    onChanged: (v) => setState(() => _enabled = v),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Form inputs Card
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: borderColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _tr('الحد الأدنى للسعر المقبول', 'Minimum Acceptable Rate'),
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                      color: isDark ? Colors.white : const Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    _tr('أي رحلة يقل سعر الكيلومتر فيها عن هذا المبلغ سيتم تصنيفها كـ "غير مناسبة"', 'Any trip below this rate will be marked as not suitable'),
                    style: TextStyle(
                      fontSize: 12,
                      color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _min,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: _tr('الحد الأدنى (ج.م/كم)', 'Min Rate (EGP/km)'),
                      prefixIcon: const Icon(Icons.attach_money_rounded),
                    ),
                  ),
                  const SizedBox(height: 20),

                  Text(
                    _tr('نسبة خصم الشركة %', 'Company Commission Discount %'),
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                      color: isDark ? Colors.white : const Color(0xFF0F172A),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    _tr('نسبة عمولة أوبر المخصومة من السعر الإجمالي', 'Uber commission percentage deducted from fare'),
                    style: TextStyle(
                      fontSize: 12,
                      color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _discount,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: _tr('نسبة الخصم %', 'Discount %'),
                      prefixIcon: const Icon(Icons.percent_rounded),
                    ),
                  ),
                  const SizedBox(height: 8),

                  // Quick chips for discount
                  Wrap(
                    spacing: 8,
                    children: [0.0, 15.0, 20.0, 22.5, 25.0, 30.0].map((rate) => ActionChip(
                      label: Text('$rate%'),
                      backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9),
                      labelStyle: const TextStyle(fontSize: 12, color: Color(0xFF0284C7)),
                      side: BorderSide(color: borderColor),
                      onPressed: () => _setDiscount(rate),
                    )).toList(),
                  ),
                  const SizedBox(height: 18),

                  Divider(color: borderColor),
                  const SizedBox(height: 8),

                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      _tr('احتساب مسافة الوصول للعميل (Pickup)', 'Include Pickup Distance'),
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                        color: isDark ? Colors.white : const Color(0xFF0F172A),
                      ),
                    ),
                    subtitle: Text(
                      _tr('عند التفعيل، تُضاف مسافة الوصول إلى مسافة الرحلة لحساب السعر/كم بدقة', 'When active, pickup distance is added to trip distance for calculation'),
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                      ),
                    ),
                    value: _includePickupDistance,
                    activeColor: const Color(0xFF10B981),
                    onChanged: (v) => setState(() => _includePickupDistance = v),
                  ),
                  const SizedBox(height: 16),

                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: FilledButton.icon(
                      onPressed: _save,
                      icon: const Icon(Icons.save_rounded, size: 20),
                      label: Text(_tr('حفظ الإعدادات', 'Save Settings')),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

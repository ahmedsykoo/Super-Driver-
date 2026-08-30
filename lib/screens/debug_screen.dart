import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/price_calculator.dart';

// Reads the latest accessibility-tree text published by the
// UberAccessibilityService via the existing MethodChannel set up by
// MainActivity.kt (channel "com.superdriver/accessibility", method
// "getAccessibilityText"). Returns "" if the service has not pushed
// any text yet or the user hasn't enabled the accessibility service.
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
}

class DebugScreen extends StatefulWidget {
  const DebugScreen({super.key});

  @override
  State<DebugScreen> createState() => _DebugScreenState();
}

class _DebugScreenState extends State<DebugScreen> {
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
    // 'both' = pickup + trip, like the live service does for all apps
    final trip = PriceCalculator.extractTripDistance(text, policy: 'both');
    String perKm = '—';
    if (price != null && trip != null && trip > 0) {
      perKm = (price / trip).toStringAsFixed(2);
    }
    if (!mounted) return;
    setState(() {
      _rawText = text;
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
        title: const Text('Debug — Accessibility Text'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _refresh,
            tooltip: 'Refresh',
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
                  _label('Raw accessibility text'),
                  const SizedBox(height: 6),
                  SelectableText(
                    _rawText.isEmpty ? '(empty — open Uber/inDrive/DiDi first, then come back)' : _rawText,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
                  ),
                  const SizedBox(height: 24),
                  _label('Extracted price (EGP)'),
                  Text(_extractedPrice, style: _valueStyle()),
                  const SizedBox(height: 16),
                  _label('Extracted trip distance (km)'),
                  Text(_extractedTrip, style: _valueStyle()),
                  const SizedBox(height: 16),
                  _label('Computed EGP/km'),
                  Text(_perKm, style: _valueStyle()),
                  const SizedBox(height: 24),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.amber.shade100,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.amber.shade700),
                    ),
                    child: const Text(
                      'How to use:\n'
                      '  1. Open Uber / inDrive / DiDi and bring up a real offer card.\n'
                      '  2. Press Home (don\'t close the app).\n'
                      '  3. Re-open Super Driver and tap Debug.\n'
                      '  4. Tap Refresh and copy the raw text.\n'
                      '  5. Send the screenshot to the developer.',
                      style: TextStyle(fontSize: 13),
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

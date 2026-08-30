class PriceCalculator {
  static const _number = r'([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)';

  static String _normalizeDigits(String value) {
    const arabic = '٠١٢٣٤٥٦٧٨٩';
    const persian = '۰۱۲۳۴۵۶۷۸۹';
    var out = value;
    for (var i = 0; i < 10; i++) {
      out = out.replaceAll(arabic[i], '$i').replaceAll(persian[i], '$i');
    }
    return out.replaceAll('،', '.').replaceAll('٫', '.').replaceAll('٬', '').replaceAll(',', '.');
  }

  static double? _parse(String value) => double.tryParse(_normalizeDigits(value));

  /// A parsed distance with its position in the text.
  static double? _firstValue(RegExp re, String text) {
    final m = re.firstMatch(text);
    if (m == null) return null;
    for (var i = 1; i <= m.groupCount; i++) {
      final v = _parse(m.group(i) ?? '');
      if (v != null && v > 0) return v;
    }
    return null;
  }

  /// All parsed (value, start, end) hits in a text.
  static List<List<double>> _allHits(RegExp re, String text) {
    final out = <List<double>>[];
    for (final m in re.allMatches(text)) {
      for (var i = 1; i <= m.groupCount; i++) {
        final v = _parse(m.group(i) ?? '');
        if (v != null && v > 0) {
          out.add([v, m.start.toDouble(), m.end.toDouble()]);
          break;
        }
      }
    }
    return out;
  }

  static double? extractPrice(String text) {
    final patterns = [
      RegExp(r'(?:السعر|سعر الرحلة|Price|Trip Price)\s*[:：]?\s*' + _number, caseSensitive: false),
      RegExp(_number + r'\s*(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)', caseSensitive: false),
      RegExp(r'(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)\s*' + _number, caseSensitive: false),
    ];
    for (final p in patterns) {
      final v = _firstValue(p, text);
      if (v != null && v > 0) return v;
    }
    return null;
  }

  static double? extractDistance(String text) {
    final patterns = [
      RegExp(r'(?:المسافة|مسافة|Distance)\s*[:：]?\s*' + _number + r'\s*(?:كم|كلم|km)', caseSensitive: false),
      RegExp(_number + r'\s*(?:كم|كلم|km)', caseSensitive: false),
    ];
    for (final p in patterns) {
      final v = _firstValue(p, text);
      if (v != null && v > 0) return v;
    }
    return null;
  }

  // ----------------------------------------------------------------
  // Structured trip-distance extraction.
  //
  // [policy] is a String (not an enum) so this file stays free of any
  // nested types that would trip the analyzer on older Dart versions.
  // Accepted values:
  //   'strict'  – only labelled trip distances (default)
  //   'pickup'  – trip, otherwise pickup
  //   'bare'    – trip, otherwise the bare "X km" closest to the price
  //   'both'    – trip + pickup (used when includePickupDistance is on)
  // ----------------------------------------------------------------
  static double? extractTripDistance(
    String text, {
    String policy = 'strict',
  }) {
    final norm = _normalizeDigits(text);

    // 1. Strong trip labels – these win outright.
    final tripStrong = _allHits(
        RegExp(
          r'(?:مسافة\s+الرحلة|trip\s+distance|route\s+distance)' +
              r'\s*[:：\-]?\s*' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)',
          caseSensitive: false,
        ),
        norm);
    if (tripStrong.isNotEmpty) {
      return _maxValue(tripStrong);
    }

    // 2. Parenthesised "(المسافة 4.5 كلم)" inside a duration block.
    final tripParen = _allHits(
        RegExp(
          r'\(\s*(?:المسافة|مسافة|distance)\s+' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)\s*\)',
          caseSensitive: false,
        ),
        norm);
    if (tripParen.isNotEmpty) {
      return _maxValue(tripParen);
    }

    // 3. Generic "المسافة 4.5 كلم" / "Distance: 3.7 km".
    final tripAny = _allHits(
        RegExp(
          r'(?:المسافة|مسافة|distance|route)\s*[:：\-]?\s*' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)',
          caseSensitive: false,
        ),
        norm);
    if (tripAny.isNotEmpty) {
      return _maxValue(tripAny);
    }

    // 4. Uber "مشوار لمدة 10 د (المسافة 4.5 كلم)" duration block.
    final dur = _allHits(
        RegExp(
          r'مشوار\s+لمدة\s+[0-9٠-٩۰-۹]+\s*(?:د(?:قيقة)?|دق|h|hr|ساعة|س(?:اعة)?)' +
              r'\s*\(\s*(?:المسافة|مسافة|distance)\s+' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)\s*\)',
          caseSensitive: false,
        ),
        norm);
    if (dur.isNotEmpty) {
      return _maxValue(dur);
    }

    // 5. Pickup labels – never the trip unless policy == 'both' or 'pickup'.
    final pickupAr = _allHits(
        RegExp(
          r'(?:على\s+بعد|يبعد|يَبْعُد|الوصول\s+إلى|بعد\s+عنك)' +
              r'[^0-9٠-٩۰-۹]*' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)',
          caseSensitive: false,
        ),
        norm);
    final pickupEn = _allHits(
        RegExp(
          r'(?:pickup|pick[\s-]?up)\s*(?:distance)?\s*[:：\-]?\s*' +
              _number +
              r'\s*(?:كم|كلم|km|ميل|mi)',
          caseSensitive: false,
        ),
        norm);
    final pickups = <List<double>>[]
      ..addAll(pickupAr)
      ..addAll(pickupEn);

    // 6. Bare "X km" – a fallback for inDrive / DiDi offer rows. We keep
    //    the position so we can pick the one closest to the price.
    final bareAll = _allHits(
        RegExp(
          r'~?\s*' + _number + r'\s*(?:كم|كلم|km|ميل|mi)',
          caseSensitive: false,
        ),
        norm);
    final bare = <List<double>>[];
    for (final b in bareAll) {
      var overlap = false;
      for (final p in pickups) {
        if (b[1] < p[2] && p[1] < b[2]) {
          overlap = true;
          break;
        }
      }
      if (!overlap) bare.add(b);
    }

    if (policy == 'strict') return null;
    if (policy == 'pickup') {
      if (pickups.isEmpty) return null;
      return _maxValue(pickups);
    }
    if (policy == 'both') {
      if (pickups.isEmpty) return null;
      var sum = 0.0;
      for (final p in pickups) {
        sum += p[0];
      }
      return sum;
    }
    // 'bare'
    if (bare.isEmpty) return null;

    // Find the price position so we can anchor the bare distance to it.
    final priceLabelMatch = RegExp(
      r'(?:السعر|سعر\s+الرحلة|القبول\s+مقابل|fare|trip\s+price|price)' +
          r'\s*[:：\-]?\s*' +
          _number +
          r'\s*(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)',
      caseSensitive: false,
    ).firstMatch(norm);
    int? pricePos;
    if (priceLabelMatch != null) {
      pricePos = priceLabelMatch.start;
    } else {
      final afterPrices = _allHits(
          RegExp(
            r'(?<![0-9٠-٩۰-۹])' + _number +
                r'\s*(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)',
            caseSensitive: false,
          ),
          norm);
      if (afterPrices.isNotEmpty) pricePos = afterPrices.first[1].toInt();
    }

    List<double>? best;
    for (final b in bare) {
      if (best == null) {
        best = b;
        continue;
      }
      if (pricePos != null) {
        final gapBest = (best[1] - pricePos).abs();
        final gapHere = (b[1] - pricePos).abs();
        if (gapHere < gapBest || (gapHere == gapBest && b[0] < best[0])) {
          best = b;
        }
      } else if (b[0] < best[0]) {
        best = b;
      }
    }
    return best?[0];
  }

  static double _maxValue(List<List<double>> hits) {
    var v = hits.first[0];
    for (final h in hits) {
      if (h[0] > v) v = h[0];
    }
    return v;
  }
}

class PriceCalculator {
  static const _number = r'([0-9]+(?:\.[0-9]+)?)';
  static const _currency = r'(?:EGP|LE|L\.E\.?|ج\s*[\.،]?\s*م\s*[\.،]?|جنيه|جنيه\s*مصر[يى])';
  static const _unit = r'(?:كم|كلم|كيلومتر|كيلو|كم\.|كلم\.|km|kms|mi|miles?)';
  static const _dur = r'(?:د(?:قيقة|قائق)?|دق|h|hr|hrs|hours?|ساعة|ساعات|س|min(?:utes?)?)';

  static String normalizeText(String value) {
    const arabicDigits = '٠١٢٣٤٥٦٧٨٩';
    const persianDigits = '۰۱۲۳۴۵۶۷۸۹';
    final out = StringBuffer();

    for (var i = 0; i < value.length; i++) {
      final ch = value[i];
      final aIdx = arabicDigits.indexOf(ch);
      if (aIdx >= 0) {
        out.write(aIdx);
        continue;
      }
      final pIdx = persianDigits.indexOf(ch);
      if (pIdx >= 0) {
        out.write(pIdx);
        continue;
      }
      if (ch == '،' || ch == '٫') {
        out.write('.');
        continue;
      }
      if (ch == '٬') {
        continue; // Thousand separator
      }
      // Strip invisible Bidi & Zero-width characters & tatweel
      final code = ch.codeUnitAt(0);
      if (code == 0x200E ||
          code == 0x200F ||
          code == 0x061C ||
          (code >= 0x202A && code <= 0x202E) ||
          (code >= 0x2066 && code <= 0x2069) ||
          code == 0x200B ||
          code == 0xFEFF ||
          code == 0x0640 ||
          (code >= 0x064B && code <= 0x065F)) {
        continue;
      }
      // Normalize Unicode spaces
      if (code == 0x00A0 || code == 0x202F || (code >= 0x2000 && code <= 0x200A)) {
        out.write(' ');
        continue;
      }
      out.write(ch);
    }
    return out.toString();
  }

  static double? _parse(String value) => double.tryParse(value);

  /// All parsed hits: [value, start, end]
  static List<List<double>> _allHits(RegExp re, String text) {
    final out = <List<double>>[];
    for (final m in re.allMatches(text)) {
      for (var i = 1; i <= m.groupCount; i++) {
        final valStr = m.group(i);
        if (valStr != null) {
          final v = _parse(valStr);
          if (v != null && v > 0) {
            out.add([v, m.start.toDouble(), m.end.toDouble()]);
            break;
          }
        }
      }
    }
    return out;
  }

  static double? extractPrice(String text) {
    final norm = normalizeText(text);

    final pricePatterns = [
      RegExp(
        r'(?:القبول\s+مقابل|قبول\s+مقابل|قبول\s+المشوار\s+مقابل|القبول|قبول|السعر|سعر\s+(?:الرحلة|المشوار)|المجموع|الإجمالي|المبلغ|الأجرة|الاجرة|أجرة\s+الرحلة|اجرة\s+الرحلة|fare|trip\s+price|price|total|accept\s+for)\s*[:：\-]?\s*(?:' +
            _currency +
            r'\s*)?' +
            _number +
            r'(?:\s*' +
            _currency +
            r')?',
        caseSensitive: false,
      ),
      RegExp(_currency + r'\s*[:：\-]?\s*' + _number, caseSensitive: false),
      RegExp(_number + r'\s*' + _currency, caseSensitive: false),
    ];

    for (final p in pricePatterns) {
      final hits = _allHits(p, norm);
      if (hits.isNotEmpty) {
        return hits.first[0];
      }
    }
    return null;
  }

  static double? extractDistance(String text) {
    return extractTripDistance(text, policy: 'strict') ??
        extractTripDistance(text, policy: 'bare');
  }

  // ----------------------------------------------------------------
  // Structured trip-distance extraction.
  //
  // Accepted policy values:
  //   'strict'  – only labelled trip distances
  //   'pickup'  – only pickup distance
  //   'both'    – trip + pickup (when includePickupDistance is enabled)
  //   'bare'    – trip, otherwise bare fallback
  // ----------------------------------------------------------------
  static double? extractTripDistance(
    String text, {
    String policy = 'strict',
  }) {
    final norm = normalizeText(text);

    // 1. Pickup patterns
    final pickupPatterns = [
      RegExp(
        r'(?:على\s+بعد|يبعد|يَبْعُد|بعد\s+عنك|الوصول\s+إلى|استلام|البيك\s*اب|pickup|pick[\s-]?up)\s*(?:distance)?\s*[:：\-]?\s*(?:[0-9]+\s*' +
            _dur +
            r'\s*)?\(?\s*' +
            _number +
            r'\s*' +
            _unit +
            r'\)?',
        caseSensitive: false,
      ),
      RegExp(
        r'(?:[0-9]+\s*' +
            _dur +
            r'\s*)?\(?\s*' +
            _number +
            r'\s*' +
            _unit +
            r'\)?\s*(?:away|pickup|pick[\s-]?up|على\s+بعد|يبعد|بعد\s+عنك)',
        caseSensitive: false,
      ),
    ];

    final pickupHits = <List<double>>[];
    for (final p in pickupPatterns) {
      pickupHits.addAll(_allHits(p, norm));
    }
    final pickupVal = pickupHits.isNotEmpty ? _maxValue(pickupHits) : null;

    // 2. Trip patterns
    final tripPatterns = [
      // Strong label
      RegExp(
        r'(?:مسافة\s+الرحلة|مسافة\s+المشوار|المشوار|الرحلة|trip\s+distance|route\s+distance|dropoff|drop-off)\s*[:：\-]?\s*' +
            _number +
            r'\s*' +
            _unit,
        caseSensitive: false,
      ),
      // Trip with duration prefix (مشوار لمدة 21 د (16.9 كلم))
      RegExp(
        r'(?:مشوار|رحلة|مشوار\s+لمدة|رحلة\s+لمدة|trip)\s*(?:لمدة\s+)?[0-9]+\s*' +
            _dur +
            r'\s*\(?\s*(?:(?:المسافة|مسافة|distance)\s+)?' +
            _number +
            r'\s*' +
            _unit +
            r'\)?',
        caseSensitive: false,
      ),
      // Trip with duration and trip suffix (18 min (12.4 km) trip)
      RegExp(
        r'(?:[0-9]+\s*' +
            _dur +
            r'\s*)?\(?\s*(?:(?:المسافة|مسافة|distance)\s+)?' +
            _number +
            r'\s*' +
            _unit +
            r'\)?\s*(?:trip|مشوار|رحلة|dropoff|drop-off)',
        caseSensitive: false,
      ),
      // Duration followed by parenthesized distance: 21 د (16.9 كلم)
      RegExp(
        r'[0-9]+\s*' +
            _dur +
            r'\s*\(\s*(?:(?:المسافة|مسافة|distance)\s+)?' +
            _number +
            r'\s*' +
            _unit +
            r'\s*\)',
        caseSensitive: false,
      ),
      // Generic "المسافة 16.9 كم"
      RegExp(
        r'(?:المسافة|مسافة|distance|route)\s*[:：\-]?\s*' +
            _number +
            r'\s*' +
            _unit,
        caseSensitive: false,
      ),
    ];

    final tripHits = <List<double>>[];
    for (final tp in tripPatterns) {
      for (final h in _allHits(tp, norm)) {
        final overlaps = pickupHits.any((p) =>
            (p[1] <= h[1] && h[1] < p[2]) || (h[1] <= p[1] && p[1] < h[2]));
        if (!overlaps) {
          tripHits.add(h);
        }
      }
      if (tripHits.isNotEmpty) break;
    }

    final tripVal = tripHits.isNotEmpty ? _maxValue(tripHits) : null;

    if (policy == 'strict') {
      return tripVal;
    }
    if (policy == 'pickup') {
      return pickupVal;
    }
    if (policy == 'both') {
      if (tripVal != null && pickupVal != null) {
        return tripVal + pickupVal;
      }
      return tripVal ?? pickupVal;
    }

    // 'bare' policy fallback
    if (tripVal != null) return tripVal;

    final barePattern = RegExp(
      r'~?\s*' + _number + r'\s*' + _unit,
      caseSensitive: false,
    );
    final bareHits = _allHits(barePattern, norm).where((h) {
      return !pickupHits.any((p) =>
          (p[1] <= h[1] && h[1] < p[2]) || (h[1] <= p[1] && p[1] < h[2]));
    }).toList();

    if (bareHits.isNotEmpty) {
      return bareHits.last[0];
    }

    return pickupVal;
  }

  static double _maxValue(List<List<double>> hits) {
    var v = hits.first[0];
    for (final h in hits) {
      if (h[0] > v) v = h[0];
    }
    return v;
  }
}

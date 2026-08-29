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

  static double? extractPrice(String text) {
    final patterns = [
      RegExp(r'(?:السعر|سعر الرحلة|Price|Trip Price)\s*[:：]?\s*' + _number, caseSensitive: false),
      RegExp(_number + r'\s*(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)', caseSensitive: false),
      RegExp(r'(?:ج\s*\.?\s*م|ج\.م|EGP|جنيه)\s*' + _number, caseSensitive: false),
    ];
    for (final p in patterns) {
      final m = p.firstMatch(text);
      if (m != null) {
        for (var i = 1; i <= m.groupCount; i++) {
          final v = _parse(m.group(i) ?? '');
          if (v != null && v > 0) return v;
        }
      }
    }
    return null;
  }

  static double? extractDistance(String text) {
    final patterns = [
      RegExp(r'(?:المسافة|مسافة|Distance)\s*[:：]?\s*' + _number + r'\s*(?:كم|كلم|km)', caseSensitive: false),
      RegExp(_number + r'\s*(?:كم|كلم|km)', caseSensitive: false),
    ];
    for (final p in patterns) {
      final m = p.firstMatch(text);
      if (m != null) {
        final v = _parse(m.group(1) ?? '');
        if (v != null && v > 0) return v;
      }
    }
    return null;
  }
}

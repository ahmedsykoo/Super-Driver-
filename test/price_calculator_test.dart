import 'package:flutter_test/flutter_test.dart';
import 'package:super_driver/services/price_calculator.dart';

void main() {
  test('extracts English price and distance', () {
    const text = 'Trip Price: 93.38 EGP Distance: 3.7 km';
    expect(PriceCalculator.extractPrice(text), 93.38);
    expect(PriceCalculator.extractDistance(text), 3.7);
  });

  test('extracts Arabic digits', () {
    const text = 'السعر: ٩٣٫٣٨ جنيه المسافة: ٣٫٧ كم';
    expect(PriceCalculator.extractPrice(text), 93.38);
    expect(PriceCalculator.extractDistance(text), 3.7);
  });

  test('extracts fare from an Arabic ride offer containing pickup and trip distances', () {
    const text = '١٥٢ ج.م على بعد ١٦ د (١٣٫٤ كلم) مشوار لمدة ٢١ د (المسافة ١٦٫٩ كلم)';
    expect(PriceCalculator.extractPrice(text), 152);
    expect(PriceCalculator.extractDistance(text), 16.9);
  });

  test('supports strict and pickup-aware trip distance policies', () {
    const text = '152 ج.م على بعد 16 د (13.4 كلم) مشوار لمدة 21 د (المسافة 16.9 كلم)';
    expect(PriceCalculator.extractTripDistance(text), 16.9);
    expect(PriceCalculator.extractTripDistance(text, policy: 'both'), closeTo(30.3, 0.0001));
  });

  test('selects a bare trip distance without confusing pickup distance', () {
    const text = '100 EGP pickup 3 km 8 km';
    expect(PriceCalculator.extractTripDistance(text, policy: 'strict'), isNull);
    expect(PriceCalculator.extractTripDistance(text, policy: 'pickup'), 3);
    expect(PriceCalculator.extractTripDistance(text, policy: 'bare'), 8);
  });

}

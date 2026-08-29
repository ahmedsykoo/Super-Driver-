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
}

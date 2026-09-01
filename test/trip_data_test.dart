import 'package:flutter_test/flutter_test.dart';
import 'package:super_driver/models/trip_data.dart';

void main() {
  test('calculates price per km', () {
    final trip = TripData(price: 93.38, distance: 3.7, timestamp: DateTime(2026));
    expect(trip.pricePerKm, closeTo(25.2378, 0.0001));
  });

  test('clamps discount to 0..100', () {
    final trip = TripData(
      price: 100,
      distance: 10,
      timestamp: DateTime(2026),
    );
    expect(trip.pricePerKmAfterDiscount(150), 0);
    expect(trip.pricePerKmAfterDiscount(-10), 10);
  });

  test('serializes and restores a trip record', () {
    final original = TripData(price: 120.5, distance: 9.25, timestamp: DateTime(2026, 9, 1, 12, 30));
    final restored = TripData.fromJson(original.toJson());
    expect(restored.price, original.price);
    expect(restored.distance, original.distance);
    expect(restored.timestamp, original.timestamp);
  });

}

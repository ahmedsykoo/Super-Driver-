class TripData {
  final double price;
  final double distance;
  final DateTime timestamp;

  const TripData({
    required this.price,
    required this.distance,
    required this.timestamp,
  });

  double get pricePerKm => distance > 0 ? price / distance : 0.0;

  double pricePerKmAfterDiscount(double discountPercent) =>
      pricePerKm * (1 - discountPercent.clamp(0, 100).toDouble() / 100);

  bool isSuitable(double minPrice, double discountPercent) =>
      pricePerKmAfterDiscount(discountPercent) >= minPrice;
}

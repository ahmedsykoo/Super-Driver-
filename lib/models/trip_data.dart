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

  Map<String, dynamic> toJson() => {
        'price': price,
        'distance': distance,
        'timestamp': timestamp.toIso8601String(),
      };

  factory TripData.fromJson(Map<String, dynamic> json) {
    final timestamp = DateTime.tryParse(json['timestamp']?.toString() ?? '');
    return TripData(
      price: (json['price'] as num?)?.toDouble() ?? 0,
      distance: (json['distance'] as num?)?.toDouble() ?? 0,
      timestamp: timestamp ?? DateTime.now(),
    );
  }
}

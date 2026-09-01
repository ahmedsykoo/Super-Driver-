import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/trip_data.dart';

class TripHistoryStore {
  static const _storageKey = 'trip_history';
  static const _maxRecords = 100;

  static Future<List<TripData>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null || raw.isEmpty) return [];

    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];
      final records = decoded
          .whereType<Map>()
          .map((item) => TripData.fromJson(Map<String, dynamic>.from(item)))
          .where((trip) => trip.price > 0 && trip.distance > 0)
          .toList();
      records.sort((a, b) => b.timestamp.compareTo(a.timestamp));
      return records.take(_maxRecords).toList();
    } on FormatException {
      return [];
    } on TypeError {
      return [];
    }
  }

  static Future<List<TripData>> add(TripData trip) async {
    final records = await load();
    if (records.isNotEmpty &&
        records.first.price == trip.price &&
        records.first.distance == trip.distance &&
        trip.timestamp.difference(records.first.timestamp).abs().inSeconds < 120) {
      return records;
    }

    final updated = [trip, ...records].take(_maxRecords).toList();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _storageKey,
      jsonEncode(updated.map((item) => item.toJson()).toList()),
    );
    return updated;
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_storageKey);
  }
}

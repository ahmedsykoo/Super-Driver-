import 'package:flutter/services.dart';

class AccessibilityStatus {
  final bool overlay;
  final bool accessibility;
  final bool monitoring;

  const AccessibilityStatus({
    required this.overlay,
    required this.accessibility,
    required this.monitoring,
  });

  bool get ready => overlay && accessibility && monitoring;
}

class AccessibilityListener {
  static const _channel = MethodChannel('com.superdriver/accessibility');

  static Future<bool> isOverlayPermissionGranted() async {
    try {
      return await _channel.invokeMethod<bool>('isOverlayPermissionGranted') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> openOverlaySettings() async {
    try {
      return await _channel.invokeMethod<bool>('openOverlaySettings') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> isAccessibilityEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> openAccessibilitySettings() async {
    try {
      return await _channel.invokeMethod<bool>('openAccessibilitySettings') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> isMonitoringEnabled() async {
    try {
      return await _channel.invokeMethod<bool>('isMonitoringEnabled') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> setMonitoringEnabled(bool enabled) async {
    try {
      return await _channel.invokeMethod<bool>(
        'setMonitoringEnabled',
        {'enabled': enabled},
      ) ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<AccessibilityStatus> getStatus() async {
    final overlay = await isOverlayPermissionGranted();
    final accessibility = await isAccessibilityEnabled();
    final monitoring = await isMonitoringEnabled();
    return AccessibilityStatus(
      overlay: overlay,
      accessibility: accessibility,
      monitoring: monitoring,
    );
  }

  static Future<String> getScreenText() async {
    try {
      return await _channel.invokeMethod<String>('getAccessibilityText') ?? '';
    } on PlatformException {
      return '';
    }
  }
}

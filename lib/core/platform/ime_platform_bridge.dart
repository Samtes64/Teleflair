import 'package:flutter/services.dart';

class ImePlatformBridge {
  static const MethodChannel _channel = MethodChannel('com.teleboard.teleboard/ime_control');

  /// Checks whether Teleboard keyboard is enabled in Android System Settings.
  static Future<bool> isImeEnabled() async {
    try {
      final res = await _channel.invokeMethod<bool>('isImeEnabled');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Checks whether Teleboard keyboard is currently selected as the active input method.
  static Future<bool> isImeSelected() async {
    try {
      final res = await _channel.invokeMethod<bool>('isImeSelected');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Launches Android's Manage Keyboards settings screen.
  static Future<void> openImeSettings() async {
    try {
      await _channel.invokeMethod('openImeSettings');
    } catch (_) {}
  }

  /// Shows the system input method picker dialog to select active keyboard.
  static Future<void> showImePicker() async {
    try {
      await _channel.invokeMethod('showImePicker');
    } catch (_) {}
  }

  /// Checks whether Teleboard's Smart Context Detection accessibility service is enabled.
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final res = await _channel.invokeMethod<bool>('isAccessibilityEnabled');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Launches Android's Accessibility settings screen.
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } catch (_) {}
  }
}

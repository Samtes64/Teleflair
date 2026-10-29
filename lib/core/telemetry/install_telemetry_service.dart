import 'dart:convert';
import 'dart:io' show Platform;
import 'dart:math';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class InstallTelemetryService {
  static const String keyInstallReported = 'telemetry_install_reported';
  static const String keyInstallId = 'telemetry_install_id';
  static const String keyInstallReportedAt = 'telemetry_install_reported_at';
  static const String defaultWorkerUrl = 'https://teleboard-delivery.samteleboard.workers.dev';

  /// Generates a high-entropy anonymous device activation identifier.
  static String generateAnonymousInstallId() {
    final random = Random.secure();
    final values = List<int>.generate(16, (i) => random.nextInt(256));
    return values.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  /// Checks if this installation has already reported, and if not, sends
  /// an anonymous first-launch telemetry ping to the Cloudflare Worker.
  static Future<bool> reportInstallIfNeeded({
    http.Client? client,
    SharedPreferences? preferences,
  }) async {
    try {
      final prefs = preferences ?? await SharedPreferences.getInstance();
      final alreadyReported = prefs.getBool(keyInstallReported) ?? false;
      if (alreadyReported) {
        return true;
      }

      var installId = prefs.getString(keyInstallId);
      if (installId == null || installId.isEmpty) {
        installId = generateAnonymousInstallId();
        await prefs.setString(keyInstallId, installId);
      }

      final workerBaseUrl = prefs.getString('cloudflare_worker_url') ?? defaultWorkerUrl;
      final cleanBaseUrl = workerBaseUrl.trim().replaceAll(RegExp(r'/+$'), '');
      final endpoint = Uri.parse('$cleanBaseUrl/api/telemetry/install');

      String platformName = 'android';
      String osVersion = '';
      try {
        platformName = Platform.operatingSystem;
        osVersion = Platform.operatingSystemVersion;
      } catch (_) {}

      final payload = jsonEncode({
        'installId': installId,
        'platform': platformName,
        'osVersion': osVersion,
        'appVersion': '1.0.0',
        'timestamp': DateTime.now().toUtc().toIso8601String(),
      });

      final httpClient = client ?? http.Client();
      try {
        final response = await httpClient
            .post(
              endpoint,
              headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
              },
              body: payload,
            )
            .timeout(const Duration(seconds: 8));

        if (response.statusCode == 200 || response.statusCode == 201) {
          await prefs.setBool(keyInstallReported, true);
          await prefs.setString(
            keyInstallReportedAt,
            DateTime.now().toUtc().toIso8601String(),
          );
          return true;
        }
      } finally {
        if (client == null) {
          httpClient.close();
        }
      }
    } catch (_) {
      // Non-blocking: will cleanly retry on next app launch
    }
    return false;
  }

  /// Returns current telemetry registration status.
  static Future<Map<String, dynamic>> getTelemetryStatus() async {
    final prefs = await SharedPreferences.getInstance();
    return {
      'reported': prefs.getBool(keyInstallReported) ?? false,
      'installId': prefs.getString(keyInstallId) ?? 'Pending generation',
      'reportedAt': prefs.getString(keyInstallReportedAt) ?? 'Not reported',
    };
  }
}

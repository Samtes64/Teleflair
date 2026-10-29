import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/core/telemetry/install_telemetry_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('InstallTelemetryService', () {
    test('generateAnonymousInstallId returns 32-character hex string', () {
      final id1 = InstallTelemetryService.generateAnonymousInstallId();
      final id2 = InstallTelemetryService.generateAnonymousInstallId();

      expect(id1.length, equals(32));
      expect(id2.length, equals(32));
      expect(id1, isNot(equals(id2)));
      expect(RegExp(r'^[a-f0-9]{32}$').hasMatch(id1), isTrue);
    });

    test('reportInstallIfNeeded sends install payload and persists reported flag', () async {
      SharedPreferences.setMockInitialValues({
        'cloudflare_worker_url': 'https://custom-worker.workers.dev',
      });
      final prefs = await SharedPreferences.getInstance();

      Map<String, dynamic>? receivedBody;
      Uri? requestedUri;

      final mockClient = MockClient((request) async {
        requestedUri = request.url;
        receivedBody = jsonDecode(request.body) as Map<String, dynamic>;
        return http.Response(
          jsonEncode({'success': true, 'recorded': true, 'totalInstalls': 1}),
          201,
          headers: {'content-type': 'application/json'},
        );
      });

      final success = await InstallTelemetryService.reportInstallIfNeeded(
        client: mockClient,
        preferences: prefs,
      );

      expect(success, isTrue);
      expect(requestedUri.toString(), equals('https://custom-worker.workers.dev/api/telemetry/install'));
      expect(receivedBody, isNotNull);
      expect(receivedBody!['installId'], isNotEmpty);
      expect(receivedBody!['appVersion'], equals('1.0.0'));

      expect(prefs.getBool(InstallTelemetryService.keyInstallReported), isTrue);
      expect(prefs.getString(InstallTelemetryService.keyInstallReportedAt), isNotNull);

      // Verify second call is a no-op and does not send network request
      bool secondRequestSent = false;
      final secondClient = MockClient((request) async {
        secondRequestSent = true;
        return http.Response('{}', 200);
      });

      final secondSuccess = await InstallTelemetryService.reportInstallIfNeeded(
        client: secondClient,
        preferences: prefs,
      );

      expect(secondSuccess, isTrue);
      expect(secondRequestSent, isFalse);
    });

    test('getTelemetryStatus returns accurate preferences status', () async {
      SharedPreferences.setMockInitialValues({
        InstallTelemetryService.keyInstallReported: true,
        InstallTelemetryService.keyInstallId: 'sample_id_12345',
        InstallTelemetryService.keyInstallReportedAt: '2026-10-29T21:00:00Z',
      });

      final status = await InstallTelemetryService.getTelemetryStatus();
      expect(status['reported'], isTrue);
      expect(status['installId'], equals('sample_id_12345'));
      expect(status['reportedAt'], equals('2026-10-29T21:00:00Z'));
    });
  });
}

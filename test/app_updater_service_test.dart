import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:teleboard/core/updater/app_updater_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AppUpdaterService', () {
    test('AppUpdateInfo correctly parses update with higher version code', () {
      final json = {
        'version': '1.0.1',
        'versionCode': 2,
        'downloadUrl': 'https://example.com/teleflair.apk',
        'releaseNotes': 'Bug fixes and performance upgrades',
      };

      final info = AppUpdateInfo.fromJson(json, 1);
      expect(info.hasUpdate, isTrue);
      expect(info.version, equals('1.0.1'));
      expect(info.versionCode, equals(2));
      expect(info.downloadUrl, equals('https://example.com/teleflair.apk'));
      expect(info.releaseNotes, equals('Bug fixes and performance upgrades'));
    });

    test('AppUpdateInfo detects when already on latest version', () {
      final json = {
        'version': '1.0.0',
        'versionCode': 1,
        'downloadUrl': 'https://example.com/teleflair.apk',
        'releaseNotes': 'Initial release',
      };

      final info = AppUpdateInfo.fromJson(json, 1);
      expect(info.hasUpdate, isFalse);
    });

    test('checkForUpdate fetches and parses update payload correctly', () async {
      final mockClient = MockClient((request) async {
        expect(request.url.path, equals('/api/app/latest'));
        return http.Response(
          jsonEncode({
            'success': true,
            'name': 'Teleflair',
            'version': '1.0.2',
            'versionCode': 3,
            'downloadUrl': 'https://example.com/update.apk',
            'releaseNotes': 'Direct seamless update installation',
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      final update = await AppUpdaterService.checkForUpdate(
        client: mockClient,
        workerUrl: 'https://test-worker.workers.dev',
      );

      expect(update, isNotNull);
      expect(update!.hasUpdate, isTrue);
      expect(update.version, equals('1.0.2'));
      expect(update.versionCode, equals(3));
    });
  });
}

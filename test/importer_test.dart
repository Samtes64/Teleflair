import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/features/sticker_packs/application/telegram_importer.dart';

class FakePathProviderPlatform extends Fake
    with MockPlatformInterfaceMixin
    implements PathProviderPlatform {
  final Directory tempDir;
  FakePathProviderPlatform(this.tempDir);

  @override
  Future<String?> getApplicationDocumentsPath() async => tempDir.path;
  @override
  Future<String?> getTemporaryPath() async => tempDir.path;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late Directory tempDir;
  late FakePathProviderPlatform fakePathProvider;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('teleboard_test_');
    fakePathProvider = FakePathProviderPlatform(tempDir);
    PathProviderPlatform.instance = fakePathProvider;
    SharedPreferences.setMockInitialValues({});

    final db = await DatabaseService.instance.database;
    await db.delete('stickers');
    await db.delete('sticker_packs');
    await db.delete('favorites');
    await db.delete('recents');
    await db.delete('delivery_logs');
  });

  tearDown(() async {
    try {
      if (await tempDir.exists()) {
        await tempDir.delete(recursive: true);
      }
    } catch (_) {}
  });

  group('Telegram Pack Link Extraction', () {
    test('Extracts pack name from Meme Man link', () {
      expect(
        TelegramImporter.extractPackName('https://t.me/addstickers/MemeManMemesta'),
        equals('MemeManMemesta'),
      );
    });

    test('Extracts pack name from https://t.me/addstickers/<pack>', () {
      expect(
        TelegramImporter.extractPackName('https://t.me/addstickers/AnimalsCool_v1'),
        equals('AnimalsCool_v1'),
      );
    });

    test('Extracts pack name from t.me/addstickers/<pack>', () {
      expect(
        TelegramImporter.extractPackName('t.me/addstickers/DogeMeme_2024'),
        equals('DogeMeme_2024'),
      );
    });

    test('Extracts pack name from tg://addstickers?set=<pack>', () {
      expect(
        TelegramImporter.extractPackName('tg://addstickers?set=RetroVibe'),
        equals('RetroVibe'),
      );
    });

    test('Accepts valid raw pack identifiers', () {
      expect(
        TelegramImporter.extractPackName('MinimalArt_stickers'),
        equals('MinimalArt_stickers'),
      );
    });

    test('Rejects invalid URLs and malicious inputs', () {
      expect(TelegramImporter.extractPackName(''), isNull);
      expect(TelegramImporter.extractPackName('https://evil.com/malware.apk'), isNull);
      expect(TelegramImporter.extractPackName('t.me/not_stickers/foo'), isNull);
      expect(TelegramImporter.extractPackName('ab'), isNull); // too short
    });
  });

  group('Telegram Import Authentication & Fallback Rules', () {
    test('Throws TelegramImportException when importing real pack without token or proxy', () async {
      final importer = TelegramImporter();
      expect(
        () => importer.importPack(packIdentifier: 'MemeManMemesta'),
        throwsA(isA<TelegramImportException>().having(
          (e) => e.message,
          'message',
          contains('Bot Token is required'),
        )),
      );
    });

    test('Successfully imports MemeManMemesta via Telegram Bot API mock', () async {
      final fakeWebpBytes = [82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80]; // RIFF....WEBP

      final mockClient = MockClient((request) async {
        final uri = request.url.toString();

        if (uri.contains('/getStickerSet?name=MemeManMemesta')) {
          return http.Response(
            jsonEncode({
              'ok': true,
              'result': {
                'name': 'MemeManMemesta',
                'title': 'Meme Man (By @Memesta team)',
                'is_animated': false,
                'is_video': false,
                'stickers': [
                  {
                    'file_id': 'file_meme_1',
                    'emoji': '😎',
                    'width': 512,
                    'height': 512,
                    'is_animated': false,
                    'is_video': false,
                  },
                  {
                    'file_id': 'file_meme_2',
                    'emoji': '🤔',
                    'width': 512,
                    'height': 512,
                    'is_animated': false,
                    'is_video': false,
                  },
                ],
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        } else if (uri.contains('/getFile?file_id=file_meme_1')) {
          return http.Response(
            jsonEncode({
              'ok': true,
              'result': {
                'file_id': 'file_meme_1',
                'file_path': 'stickers/mememan_1.webp',
                'file_size': fakeWebpBytes.length,
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        } else if (uri.contains('/getFile?file_id=file_meme_2')) {
          return http.Response(
            jsonEncode({
              'ok': true,
              'result': {
                'file_id': 'file_meme_2',
                'file_path': 'stickers/mememan_2.webp',
                'file_size': fakeWebpBytes.length,
              },
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        } else if (uri.contains('/file/bot') && uri.contains('.webp')) {
          return http.Response.bytes(fakeWebpBytes, 200);
        }

        return http.Response('Not Found', 404);
      });

      final importer = TelegramImporter(httpClient: mockClient);
      final pack = await importer.importPack(
        packIdentifier: 'https://t.me/addstickers/MemeManMemesta',
        botToken: '123456789:ABC_TEST_TOKEN',
      );

      expect(pack.name, equals('MemeManMemesta'));
      expect(pack.title, equals('Meme Man (By @Memesta team)'));
      expect(pack.totalStickers, equals(2));
      expect(pack.sourceType, equals('telegram'));

      final stickers = await DatabaseService.instance.getStickersByPack(pack.id);
      expect(stickers.length, equals(2));
      expect(stickers[0].emoji, equals('😎'));
      expect(stickers[1].emoji, equals('🤔'));
      expect(File(stickers[0].filePath).existsSync(), isTrue);
    });

    test('Throws descriptive error when Telegram Bot Token is invalid (HTTP 401)', () async {
      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'ok': false,
            'error_code': 401,
            'description': 'Unauthorized',
          }),
          401,
        );
      });

      final importer = TelegramImporter(httpClient: mockClient);
      expect(
        () => importer.importPack(
          packIdentifier: 'UnseenPackName',
          botToken: 'invalid_token',
        ),
        throwsA(isA<TelegramImportException>().having(
          (e) => e.message,
          'message',
          contains('Invalid Telegram Bot Token'),
        )),
      );
    });

    test('Throws descriptive error when Sticker Pack does not exist (STICKERSET_INVALID)', () async {
      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'ok': false,
            'error_code': 400,
            'description': 'Bad Request: STICKERSET_INVALID',
          }),
          400,
        );
      });

      final importer = TelegramImporter(httpClient: mockClient);
      expect(
        () => importer.importPack(
          packIdentifier: 'NonExistentPack123',
          botToken: 'valid_token_123',
        ),
        throwsA(isA<TelegramImportException>().having(
          (e) => e.message,
          'message',
          contains('does not exist on Telegram'),
        )),
      );
    });

    test('Imports pack via backend proxy endpoint correctly', () async {
      final fakeWebpBytes = [82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80];

      final mockClient = MockClient((request) async {
        final uri = request.url.toString();
        if (uri.contains('/api/stickers/pack?name=ProxyPack')) {
          return http.Response(
            jsonEncode({
              'success': true,
              'name': 'ProxyPack',
              'title': 'Proxy Pack Title',
              'stickers': [
                {
                  'fileId': 'proxy_f1',
                  'emoji': '🚀',
                  'width': 512,
                  'height': 512,
                  'downloadUrl': 'https://proxy.workers.dev/stickers/raw_1.webp',
                }
              ],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        } else if (uri.contains('/stickers/raw_1.webp')) {
          return http.Response.bytes(fakeWebpBytes, 200);
        }

        return http.Response('Not Found', 404);
      });

      final importer = TelegramImporter(httpClient: mockClient);
      final pack = await importer.importPack(
        packIdentifier: 'ProxyPack',
        backendProxyUrl: 'https://proxy.workers.dev',
      );

      expect(pack.name, equals('ProxyPack'));
      expect(pack.title, equals('Proxy Pack Title'));
      expect(pack.totalStickers, equals(1));
      expect(pack.sourceType, equals('telegram'));
    });
  });
}

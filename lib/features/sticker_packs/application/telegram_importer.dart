import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/features/stickers/domain/models.dart';

class TelegramImportException implements Exception {
  final String message;
  const TelegramImportException(this.message);

  @override
  String toString() => 'TelegramImportException: $message';
}

class TelegramRateLimitException extends TelegramImportException {
  final int? retryAfterSeconds;
  const TelegramRateLimitException(super.message, {this.retryAfterSeconds});

  @override
  String toString() => 'TelegramRateLimitException: $message';
}

class TelegramImporter {
  final http.Client httpClient;
  final DatabaseService dbService;

  TelegramImporter({
    http.Client? httpClient,
    DatabaseService? dbService,
  })  : httpClient = httpClient ?? http.Client(),
        dbService = dbService ?? DatabaseService.instance;

  /// Extracts the canonical sticker pack short name from a URL or raw identifier.
  static String? extractPackName(String input) {
    final trimmed = input.trim();
    if (trimmed.isEmpty) return null;

    // Pattern 1: t.me/addstickers/<name> or https://t.me/addstickers/<name>
    final tmeMatch = RegExp(r'(?:https?:\/\/)?t\.me\/addstickers\/([a-zA-Z0-9_]+)', caseSensitive: false)
        .firstMatch(trimmed);
    if (tmeMatch != null) return tmeMatch.group(1);

    // Pattern 2: tg://addstickers?set=<name>
    final tgMatch = RegExp(r'tg:\/\/addstickers\?set=([a-zA-Z0-9_]+)', caseSensitive: false)
        .firstMatch(trimmed);
    if (tgMatch != null) return tgMatch.group(1);

    // Pattern 3: raw identifier (letters, numbers, underscores, min 3 chars)
    if (RegExp(r'^[a-zA-Z0-9_]{3,64}$').hasMatch(trimmed)) {
      return trimmed;
    }

    return null;
  }

  /// Downloads and imports a sticker pack via Telegram Bot API or backend proxy.
  Future<StickerPack> importPack({
    required String packIdentifier,
    String? botToken,
    String? backendProxyUrl,
    bool forceReload = false,
    void Function(int current, int total)? onProgress,
  }) async {
    final packName = extractPackName(packIdentifier);
    if (packName == null) {
      throw const TelegramImportException(
        'Invalid Telegram sticker pack URL or identifier. Example: https://t.me/addstickers/MemeManMemesta',
      );
    }

    // Resolve credentials from SharedPreferences if not passed directly
    if (botToken == null || botToken.trim().isEmpty || backendProxyUrl == null || backendProxyUrl.trim().isEmpty) {
      try {
        final prefs = await SharedPreferences.getInstance();
        botToken ??= prefs.getString('telegram_bot_token');
        backendProxyUrl ??= prefs.getString('cloudflare_worker_url');
      } catch (_) {}
    }

    // Check if pack is already imported
    final existing = await dbService.getPackByName(packName);
    if (existing != null) {
      // If it was an old dummy fallback or user requested forceReload, clean it up
      if (forceReload || existing.sourceType == 'starter' || existing.sourceType == 'bundled') {
        await dbService.deletePack(existing.id);
      } else {
        final existingStickers = await dbService.getStickersByPack(existing.id);
        if (existingStickers.isNotEmpty) {
          return existing;
        }
        await dbService.deletePack(existing.id);
      }
    }

    final trimmedToken = botToken?.trim();
    final trimmedProxy = backendProxyUrl?.trim();

    if (trimmedToken != null && trimmedToken.isNotEmpty) {
      return await _importViaBotApi(packName, trimmedToken, onProgress);
    } else if (trimmedProxy != null && trimmedProxy.isNotEmpty && !trimmedProxy.contains('example.workers.dev')) {
      return await _importViaBackendProxy(packName, trimmedProxy, trimmedToken, onProgress);
    } else {
      throw const TelegramImportException(
        'A Telegram Bot Token is required to fetch real stickers from Telegram.\n\n'
        'How to get a free token in 30 seconds:\n'
        '1. Open Telegram & search @BotFather\n'
        '2. Send /newbot and choose any name\n'
        '3. Copy the HTTP API token and enter it in Settings or on the Import screen.',
      );
    }
  }

  Future<StickerPack> _importViaBotApi(
    String packName,
    String botToken,
    void Function(int current, int total)? onProgress,
  ) async {
    final url = Uri.parse('https://api.telegram.org/bot$botToken/getStickerSet?name=$packName');
    final response = await httpClient.get(url);

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const TelegramImportException(
        'Invalid Telegram Bot Token. Please check that you entered the correct token provided by @BotFather.',
      );
    }

    if (response.statusCode == 429) {
      throw const TelegramRateLimitException(
        'Telegram rate limit reached (HTTP 429). Too many requests sent to Telegram API.',
      );
    }

    if (response.statusCode != 200) {
      try {
        final errData = jsonDecode(response.body) as Map<String, dynamic>;
        final desc = errData['description'] as String? ?? '';
        if (desc.contains('STICKERSET_INVALID')) {
          throw TelegramImportException('Sticker pack "$packName" does not exist on Telegram. Please verify the URL.');
        }
        throw TelegramImportException('Telegram API error: $desc (HTTP ${response.statusCode})');
      } catch (e) {
        if (e is TelegramImportException) rethrow;
        throw TelegramImportException('Failed to fetch sticker set from Telegram: HTTP ${response.statusCode}');
      }
    }

    final data = jsonDecode(response.body) as Map<String, dynamic>;
    if (data['ok'] != true || data['result'] == null) {
      final desc = data['description'] ?? 'Sticker set not found';
      throw TelegramImportException('Telegram API error: $desc');
    }

    final result = data['result'] as Map<String, dynamic>;
    final title = result['title'] as String? ?? packName;
    final stickersJson = result['stickers'] as List<dynamic>? ?? [];

    if (stickersJson.isEmpty) {
      throw TelegramImportException('Sticker pack "$title" contains no stickers');
    }

    final docsDir = await getApplicationDocumentsDirectory();
    final packDir = Directory(p.join(docsDir.path, 'stickers', packName));
    if (!await packDir.exists()) {
      await packDir.create(recursive: true);
    }

    final packId = 'pack_$packName';
    final now = DateTime.now();

    final total = stickersJson.length;
    var completed = 0;
    final indexedStickers = <int, StickerItem>{};

    // Parallel download worker pool with concurrency of 4
    final queue = List.generate(total, (i) => i);

    Future<void> downloadWorker() async {
      while (queue.isNotEmpty) {
        final index = queue.removeAt(0);
        final s = stickersJson[index] as Map<String, dynamic>;

        try {
          final item = await _downloadSingleSticker(
            stickerJson: s,
            index: index,
            packId: packId,
            packName: packName,
            packDir: packDir,
            botToken: botToken,
            now: now,
          );
          if (item != null) {
            indexedStickers[index] = item;
          }
        } catch (_) {}

        completed++;
        onProgress?.call(completed, total);
      }
    }

    final workerCount = total > 4 ? 4 : total;
    await Future.wait(List.generate(workerCount, (_) => downloadWorker()));

    final downloadedStickers = (indexedStickers.keys.toList()..sort()).map((k) => indexedStickers[k]!).toList();

    if (downloadedStickers.isEmpty) {
      throw TelegramImportException('Failed to download sticker files for "$title".');
    }

    final pack = StickerPack(
      id: packId,
      name: packName,
      title: title,
      sourceType: 'telegram',
      totalStickers: downloadedStickers.length,
      installedAt: now,
    );

    await dbService.insertPack(pack);
    await dbService.insertStickers(downloadedStickers);

    return pack;
  }

  Future<StickerItem?> _downloadSingleSticker({
    required Map<String, dynamic> stickerJson,
    required int index,
    required String packId,
    required String packName,
    required Directory packDir,
    required String botToken,
    required DateTime now,
  }) async {
    final fileId = stickerJson['file_id'] as String?;
    if (fileId == null) return null;

    final emoji = stickerJson['emoji'] as String? ?? '⭐';
    final isAnimated = stickerJson['is_animated'] as bool? ?? false;
    final isVideo = stickerJson['is_video'] as bool? ?? false;
    final width = stickerJson['width'] as int? ?? 512;
    final height = stickerJson['height'] as int? ?? 512;

    // 1. Get file path from Telegram
    final getFileUri = Uri.parse('https://api.telegram.org/bot$botToken/getFile?file_id=$fileId');
    final fileRes = await httpClient.get(getFileUri);
    if (fileRes.statusCode != 200) return null;

    final fileData = jsonDecode(fileRes.body) as Map<String, dynamic>;
    final tgFilePath = fileData['result']?['file_path'] as String?;
    if (tgFilePath == null) return null;

    // 2. Download raw file bytes
    final downloadUri = Uri.parse('https://api.telegram.org/file/bot$botToken/$tgFilePath');
    final downloadRes = await httpClient.get(downloadUri);
    if (downloadRes.statusCode != 200) return null;

    final bytes = downloadRes.bodyBytes;
    if (bytes.isEmpty) return null;

    final ext = p.extension(tgFilePath).isNotEmpty ? p.extension(tgFilePath) : '.webp';
    final localFilePath = p.join(packDir.path, 'sticker_${index + 1}$ext');
    final localFile = File(localFilePath);
    await localFile.writeAsBytes(bytes, flush: true);

    // 3. For animated or video stickers, also download thumbnail if available
    var displayFilePath = localFilePath;
    if (isAnimated || isVideo) {
      final thumb = stickerJson['thumbnail'] as Map<String, dynamic>?;
      if (thumb != null && thumb['file_id'] != null) {
        try {
          final thumbId = thumb['file_id'] as String;
          final thumbFileRes = await httpClient.get(
            Uri.parse('https://api.telegram.org/bot$botToken/getFile?file_id=$thumbId'),
          );
          if (thumbFileRes.statusCode == 200) {
            final thumbData = jsonDecode(thumbFileRes.body) as Map<String, dynamic>;
            final thumbPath = thumbData['result']?['file_path'] as String?;
            if (thumbPath != null) {
              final thumbDownRes = await httpClient.get(
                Uri.parse('https://api.telegram.org/file/bot$botToken/$thumbPath'),
              );
              if (thumbDownRes.statusCode == 200 && thumbDownRes.bodyBytes.isNotEmpty) {
                final thumbLocalPath = p.join(packDir.path, 'sticker_${index + 1}_thumb.webp');
                await File(thumbLocalPath).writeAsBytes(thumbDownRes.bodyBytes, flush: true);
                displayFilePath = thumbLocalPath;
              }
            }
          }
        } catch (_) {}
      }
    }

    final contentHash = sha256.convert(bytes).toString();

    return StickerItem(
      id: '${packId}_$index',
      packId: packId,
      fileId: fileId,
      emoji: emoji,
      filePath: displayFilePath,
      width: width,
      height: height,
      fileSize: bytes.length,
      mimeType: isVideo ? 'video/webm' : (isAnimated ? 'application/x-tgsticker' : 'image/webp'),
      isAnimated: isAnimated || isVideo,
      contentHash: contentHash,
      addedAt: now,
    );
  }

  Future<StickerPack> _importViaBackendProxy(
    String packName,
    String backendProxyUrl,
    String? botToken,
    void Function(int current, int total)? onProgress,
  ) async {
    var url = '$backendProxyUrl/api/stickers/pack?name=$packName';
    if (botToken != null && botToken.isNotEmpty) {
      url += '&botToken=${Uri.encodeComponent(botToken)}';
    }

    final response = await httpClient.get(Uri.parse(url));
    if (response.statusCode == 429) {
      int? retryAfter;
      try {
        final errData = jsonDecode(response.body) as Map<String, dynamic>;
        retryAfter = errData['retryAfterSeconds'] as int?;
      } catch (_) {}
      throw TelegramRateLimitException(
        'Telegram rate limit reached (HTTP 429). The shared community bot is experiencing high traffic.',
        retryAfterSeconds: retryAfter,
      );
    }

    if (response.statusCode != 200) {
      try {
        final errData = jsonDecode(response.body) as Map<String, dynamic>;
        if (errData['isRateLimit'] == true || (errData['error'] as String? ?? '').contains('429')) {
          throw TelegramRateLimitException(
            'Telegram rate limit reached (HTTP 429). The shared community bot is experiencing high traffic.',
            retryAfterSeconds: errData['retryAfterSeconds'] as int?,
          );
        }
        throw TelegramImportException('Backend proxy error: ${errData['error'] ?? response.statusCode}');
      } catch (e) {
        if (e is TelegramImportException) rethrow;
        throw TelegramImportException('Backend proxy error: HTTP ${response.statusCode}');
      }
    }

    final data = jsonDecode(response.body) as Map<String, dynamic>;
    if (data['success'] != true) {
      throw TelegramImportException(data['error'] ?? 'Backend proxy returned unsuccessful response');
    }

    final title = data['title'] as String? ?? packName;
    final stickersList = data['stickers'] as List<dynamic>? ?? [];

    if (stickersList.isEmpty) {
      throw TelegramImportException('Backend proxy returned 0 stickers for pack "$title"');
    }

    final docsDir = await getApplicationDocumentsDirectory();
    final packDir = Directory(p.join(docsDir.path, 'stickers', packName));
    if (!await packDir.exists()) {
      await packDir.create(recursive: true);
    }

    final packId = 'pack_$packName';
    final now = DateTime.now();
    final downloadedStickers = <StickerItem>[];

    for (var i = 0; i < stickersList.length; i++) {
      final s = stickersList[i] as Map<String, dynamic>;
      final downloadUrl = s['downloadUrl'] as String?;
      if (downloadUrl == null) continue;

      try {
        final res = await httpClient.get(Uri.parse(downloadUrl));
        if (res.statusCode != 200 || res.bodyBytes.isEmpty) continue;

        final bytes = res.bodyBytes;
        final filePath = p.join(packDir.path, 'sticker_${i + 1}.webp');
        await File(filePath).writeAsBytes(bytes, flush: true);

        final contentHash = sha256.convert(bytes).toString();

        downloadedStickers.add(StickerItem(
          id: '${packId}_$i',
          packId: packId,
          fileId: s['fileId'] as String? ?? 'file_$i',
          emoji: s['emoji'] as String? ?? '⭐',
          filePath: filePath,
          width: s['width'] as int? ?? 512,
          height: s['height'] as int? ?? 512,
          fileSize: bytes.length,
          mimeType: (s['isVideo'] == true)
              ? 'video/webm'
              : (s['isAnimated'] == true ? 'application/x-tgsticker' : 'image/webp'),
          isAnimated: s['isAnimated'] == true || s['isVideo'] == true,
          contentHash: contentHash,
          addedAt: now,
        ));
      } catch (_) {}

      onProgress?.call(i + 1, stickersList.length);
    }

    if (downloadedStickers.isEmpty) {
      throw TelegramImportException('Failed to download sticker files for "$title".');
    }

    final pack = StickerPack(
      id: packId,
      name: packName,
      title: title,
      sourceType: 'telegram',
      totalStickers: downloadedStickers.length,
      installedAt: now,
    );

    await dbService.insertPack(pack);
    await dbService.insertStickers(downloadedStickers);

    return pack;
  }
}

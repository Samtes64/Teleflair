import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppUpdateInfo {
  final String version;
  final int versionCode;
  final String downloadUrl;
  final String releaseNotes;
  final bool hasUpdate;

  const AppUpdateInfo({
    required this.version,
    required this.versionCode,
    required this.downloadUrl,
    required this.releaseNotes,
    required this.hasUpdate,
  });

  factory AppUpdateInfo.fromJson(Map<String, dynamic> json, int currentVersionCode) {
    final remoteVersionCode = (json['versionCode'] as num?)?.toInt() ?? 1;
    final version = json['version'] as String? ?? '1.0.0';
    final downloadUrl = json['downloadUrl'] as String? ?? '';
    final releaseNotes = json['releaseNotes'] as String? ?? '';

    return AppUpdateInfo(
      version: version,
      versionCode: remoteVersionCode,
      downloadUrl: downloadUrl,
      releaseNotes: releaseNotes,
      hasUpdate: remoteVersionCode > currentVersionCode,
    );
  }
}

class AppUpdaterService {
  static const int currentVersionCode = 1;
  static const String currentVersionName = '1.0.0';
  static const MethodChannel _channel = MethodChannel('com.teleboard.teleboard/ime_control');
  static const String defaultWorkerUrl = 'https://teleboard-delivery.samteleboard.workers.dev';

  /// Check backend for available updates
  static Future<AppUpdateInfo?> checkForUpdate({
    http.Client? client,
    String? workerUrl,
  }) async {
    try {
      String base = workerUrl ?? defaultWorkerUrl;
      if (workerUrl == null) {
        final prefs = await SharedPreferences.getInstance();
        base = prefs.getString('cloudflare_worker_url') ?? defaultWorkerUrl;
      }
      final cleanBase = base.trim().replaceAll(RegExp(r'/+$'), '');
      final uri = Uri.parse('$cleanBase/api/app/latest');

      final httpClient = client ?? http.Client();
      try {
        final response = await httpClient.get(uri).timeout(const Duration(seconds: 6));
        if (response.statusCode == 200) {
          final data = jsonDecode(response.body) as Map<String, dynamic>;
          return AppUpdateInfo.fromJson(data, currentVersionCode);
        }
      } finally {
        if (client == null) {
          httpClient.close();
        }
      }
    } catch (_) {}
    return null;
  }

  /// Download APK and invoke Android native PackageInstaller
  static Future<bool> downloadAndInstallApk({
    required String downloadUrl,
    http.Client? client,
    void Function(double progress)? onProgress,
  }) async {
    try {
      final tempDir = await getTemporaryDirectory();
      final apkFile = File('${tempDir.path}/teleflair_update.apk');

      final httpClient = client ?? http.Client();
      try {
        final request = http.Request('GET', Uri.parse(downloadUrl));
        final streamedResponse = await httpClient.send(request);

        if (streamedResponse.statusCode != 200) {
          return false;
        }

        final contentLength = streamedResponse.contentLength ?? 0;
        int receivedBytes = 0;
        final sink = apkFile.openWrite();

        await streamedResponse.stream.listen((chunk) {
          sink.add(chunk);
          receivedBytes += chunk.length;
          if (contentLength > 0 && onProgress != null) {
            onProgress(receivedBytes / contentLength);
          }
        }).asFuture();

        await sink.flush();
        await sink.close();

        // Trigger native package installation
        final success = await _channel.invokeMethod<bool>('installApk', {
          'filePath': apkFile.path,
        });

        return success ?? false;
      } finally {
        if (client == null) {
          httpClient.close();
        }
      }
    } catch (_) {
      return false;
    }
  }
}

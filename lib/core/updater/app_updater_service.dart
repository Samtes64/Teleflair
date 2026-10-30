import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppInstallStatus {
  success,
  permissionNeeded,
  downloadFailed,
  fileError,
}

class AppInstallResult {
  final AppInstallStatus status;
  final String? errorMessage;
  final String? filePath;

  const AppInstallResult({
    required this.status,
    this.errorMessage,
    this.filePath,
  });

  bool get isSuccess => status == AppInstallStatus.success;
  bool get isPermissionNeeded => status == AppInstallStatus.permissionNeeded;
}

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

  /// Checks whether app has permission to install unknown packages (Android 8.0+)
  static Future<bool> canRequestPackageInstalls() async {
    try {
      final res = await _channel.invokeMethod<bool>('canRequestPackageInstalls');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Opens the Unknown App Sources settings screen for this app
  static Future<void> openInstallPermissionSettings() async {
    try {
      await _channel.invokeMethod('openInstallPermissionSettings');
    } catch (_) {}
  }

  /// Returns cached APK file if already downloaded and valid
  static Future<File?> getCachedApk() async {
    try {
      final tempDir = await getTemporaryDirectory();
      final apkFile = File('${tempDir.path}/teleflair_update.apk');
      if (await apkFile.exists() && await apkFile.length() > 100000) {
        return apkFile;
      }
    } catch (_) {}
    return null;
  }

  /// Launches installer for an existing local APK file
  static Future<AppInstallResult> installExistingApk(String filePath) async {
    try {
      final res = await _channel.invokeMethod<dynamic>('installApk', {
        'filePath': filePath,
      });

      if (res == 'SUCCESS' || res == true) {
        return AppInstallResult(
          status: AppInstallStatus.success,
          filePath: filePath,
        );
      } else if (res == 'PERMISSION_NEEDED' || res == false) {
        return AppInstallResult(
          status: AppInstallStatus.permissionNeeded,
          errorMessage: 'Permission to install unknown apps is required',
          filePath: filePath,
        );
      } else {
        return AppInstallResult(
          status: AppInstallStatus.fileError,
          errorMessage: 'Installation response: $res',
          filePath: filePath,
        );
      }
    } on PlatformException catch (e) {
      return AppInstallResult(
        status: AppInstallStatus.fileError,
        errorMessage: e.message ?? 'Native installation error',
        filePath: filePath,
      );
    } catch (e) {
      return AppInstallResult(
        status: AppInstallStatus.fileError,
        errorMessage: e.toString(),
        filePath: filePath,
      );
    }
  }

  /// Download APK and invoke Android native PackageInstaller
  static Future<AppInstallResult> downloadAndInstallApk({
    required String downloadUrl,
    http.Client? client,
    Directory? targetDirectory,
    void Function(double progress)? onProgress,
  }) async {
    try {
      final tempDir = targetDirectory ?? await getTemporaryDirectory();
      final apkFile = File('${tempDir.path}/teleflair_update.apk');

      final httpClient = client ?? http.Client();
      try {
        final request = http.Request('GET', Uri.parse(downloadUrl));
        request.followRedirects = true;
        request.maxRedirects = 10;
        final streamedResponse = await httpClient.send(request);

        if (streamedResponse.statusCode != 200) {
          return AppInstallResult(
            status: AppInstallStatus.downloadFailed,
            errorMessage: 'Server returned HTTP ${streamedResponse.statusCode}',
          );
        }

        final contentLength = streamedResponse.contentLength ?? 0;
        int receivedBytes = 0;

        if (await apkFile.exists()) {
          try {
            await apkFile.delete();
          } catch (_) {}
        }

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

        // Verify downloaded file size
        final fileSize = await apkFile.length();
        if (fileSize < 50000) {
          return const AppInstallResult(
            status: AppInstallStatus.fileError,
            errorMessage: 'Downloaded file is invalid or incomplete',
          );
        }

        // Trigger native package installation
        return await installExistingApk(apkFile.path);
      } finally {
        if (client == null) {
          httpClient.close();
        }
      }
    } catch (e) {
      return AppInstallResult(
        status: AppInstallStatus.downloadFailed,
        errorMessage: e.toString(),
      );
    }
  }
}

import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/app/theme/design_tokens.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/features/delivery/domain/delivery_state.dart';
import 'package:teleboard/features/delivery/domain/sms_codec.dart';
import 'package:teleboard/features/stickers/domain/models.dart';
import 'package:teleboard/features/text_art/application/text_art_converter.dart';

class DeliveryScreen extends StatefulWidget {
  const DeliveryScreen({super.key});

  @override
  State<DeliveryScreen> createState() => _DeliveryScreenState();
}

class _DeliveryScreenState extends State<DeliveryScreen> {
  final DatabaseService _db = DatabaseService.instance;
  List<StickerItem> _stickers = [];
  StickerItem? _selectedSticker;
  List<DeliveryRecord> _recentLogs = [];
  bool _isLoading = true;

  // Codec & Text-Art testing state
  String? _generatedTextArt;
  List<String>? _encodedSmsChunks;
  String? _uploadedLink;
  bool _isActionRunning = false;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final recents = await _db.getRecents(limit: 10);
      List<StickerItem> items = recents;
      if (items.isEmpty) {
        final packs = await _db.getPacks();
        if (packs.isNotEmpty) {
          items = await _db.getStickersByPack(packs.first.id);
        }
      }
      final logs = await _db.getRecentDeliveries(limit: 20);

      if (mounted) {
        setState(() {
          _stickers = items;
          if (_stickers.isNotEmpty && _selectedSticker == null) {
            _selectedSticker = _stickers.first;
          }
          _recentLogs = logs;
        });
      }
    } catch (e) {
      debugPrint('Error loading delivery data: $e');
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _testTextArt() async {
    final sticker = _selectedSticker;
    if (sticker == null) return;
    final file = File(sticker.filePath);
    if (!file.existsSync()) return;

    final bytes = await file.readAsBytes();
    final textArt = TextArtConverter.convertBytes(bytes);
    setState(() {
      _generatedTextArt = textArt;
    });
  }

  Future<void> _testSmsCodec() async {
    final sticker = _selectedSticker;
    if (sticker == null) return;
    final file = File(sticker.filePath);
    if (!file.existsSync()) return;

    final bytes = await file.readAsBytes();
    final chunks = SmsCodec.encode(bytes, chunkSize: 120);
    setState(() {
      _encodedSmsChunks = chunks;
    });
  }

  Future<void> _testCloudflareUpload() async {
    final sticker = _selectedSticker;
    if (sticker == null) return;
    final file = File(sticker.filePath);
    if (!file.existsSync()) return;

    setState(() {
      _isActionRunning = true;
      _uploadedLink = null;
    });

    final prefs = await SharedPreferences.getInstance();
    final workerUrl = prefs.getString('cloudflare_worker_url') ?? 'http://127.0.0.1:8787';

    try {
      final bytes = await file.readAsBytes();
      final uri = Uri.parse('$workerUrl/api/stickers/upload?expiresInHours=24');

      final response = await http.post(
        uri,
        headers: {'Content-Type': 'image/webp'},
        body: bytes,
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 201) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        setState(() {
          _uploadedLink = data['url'] as String?;
          _isActionRunning = false;
        });
      } else {
        throw Exception('HTTP ${response.statusCode}: ${response.body}');
      }
    } catch (e) {
      // In offline/local mode without active worker, demonstrate fallback link format
      final mockToken = SmsCodec.calculateCrc32(await file.readAsBytes());
      setState(() {
        _uploadedLink = '$workerUrl/s/$mockToken (Local Demonstration)';
        _isActionRunning = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Scaffold(
      appBar: AppBar(title: const Text('Smart Delivery Studio')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
          : ListView(
              padding: const EdgeInsets.all(16.0),
              children: [
                // Delivery Pipeline Overview Card
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.bolt_rounded, size: 20, color: textColor),
                            const SizedBox(width: 8),
                            Text(
                              'Smart Delivery Engine Pipeline',
                              style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        _buildPipelineStep(1, 'Direct Rich Content Insertion', 'InputConnectionCompat.commitContent with temporary URI permission'),
                        _buildPipelineStep(2, 'Messaging App Intent Handoff', 'Targeted ACTION_SEND with secure FileProvider content:// URI'),
                        _buildPipelineStep(3, 'Cloudflare Expiring Link (SMS)', 'Ephemeral upload with 24h retention and unguessable 32-char token'),
                        _buildPipelineStep(4, 'SMS-Encoded Protocol (TB1)', 'Segmented base64url chunks with CRC32 integrity verification'),
                        _buildPipelineStep(5, 'Text-Art Silhouette Fallback', 'High contrast monochrome ASCII character silhouette in text field'),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // Interactive Test Harness
                Text(
                  'Delivery Simulator',
                  style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15),
                ),
                const SizedBox(height: 8),

                if (_stickers.isEmpty)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20.0),
                      child: Center(
                        child: Text(
                          'Import stickers to test delivery fallbacks.',
                          style: TextStyle(color: mutedColor, fontSize: 13),
                        ),
                      ),
                    ),
                  )
                else
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 56,
                                height: 56,
                                decoration: BoxDecoration(
                                  color: isDark ? const Color(0xFF121214) : const Color(0xFFEEEEF0),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: _selectedSticker != null && File(_selectedSticker!.filePath).existsSync()
                                    ? Image.file(File(_selectedSticker!.filePath), fit: BoxFit.contain)
                                    : Center(child: Text(_selectedSticker?.emoji ?? '⭐', style: const TextStyle(fontSize: 24))),
                              ),
                              const SizedBox(width: 14),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '${_selectedSticker?.emoji} Selected Sticker',
                                      style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 14),
                                    ),
                                    Text(
                                      '${_selectedSticker?.width}x${_selectedSticker?.height} • ${_selectedSticker?.mimeType}',
                                      style: TextStyle(color: mutedColor, fontSize: 12),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),

                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              OutlinedButton.icon(
                                onPressed: _testTextArt,
                                icon: const Icon(Icons.short_text, size: 16),
                                label: const Text('Generate Text-Art', style: TextStyle(fontSize: 12)),
                              ),
                              OutlinedButton.icon(
                                onPressed: _testSmsCodec,
                                icon: const Icon(Icons.sms_outlined, size: 16),
                                label: const Text('Test SMS Codec', style: TextStyle(fontSize: 12)),
                              ),
                              OutlinedButton.icon(
                                onPressed: _isActionRunning ? null : _testCloudflareUpload,
                                icon: const Icon(Icons.cloud_upload_outlined, size: 16),
                                label: const Text('Test Cloudflare Link', style: TextStyle(fontSize: 12)),
                              ),
                            ],
                          ),

                          // Text-Art Result
                          if (_generatedTextArt != null) ...[
                            const SizedBox(height: 16),
                            Text('Text-Art Output Preview:', style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 12)),
                            const SizedBox(height: 6),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF101012) : const Color(0xFFEEEEF0),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                _generatedTextArt!,
                                style: const TextStyle(fontFamily: 'monospace', fontSize: 10, height: 1.1),
                              ),
                            ),
                          ],

                          // SMS Codec Result
                          if (_encodedSmsChunks != null) ...[
                            const SizedBox(height: 16),
                            Text('SMS Codec (${_encodedSmsChunks!.length} segments):', style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 12)),
                            const SizedBox(height: 6),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF101012) : const Color(0xFFEEEEF0),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                _encodedSmsChunks!.take(2).join('\n') + (_encodedSmsChunks!.length > 2 ? '\n... (${_encodedSmsChunks!.length - 2} more)' : ''),
                                style: const TextStyle(fontFamily: 'monospace', fontSize: 11),
                              ),
                            ),
                          ],

                          // Cloudflare Link Result
                          if (_uploadedLink != null) ...[
                            const SizedBox(height: 16),
                            Text('Generated Ephemeral Link:', style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 12)),
                            const SizedBox(height: 6),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF101012) : const Color(0xFFEEEEF0),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: SelectableText(
                                _uploadedLink!,
                                style: TextStyle(color: textColor, fontSize: 12),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),

                const SizedBox(height: 24),

                // Delivery History Logs
                Text(
                  'Recent Deliveries (${_recentLogs.length})',
                  style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15),
                ),
                const SizedBox(height: 8),

                if (_recentLogs.isEmpty)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(24.0),
                      child: Center(
                        child: Text(
                          'No stickers sent yet. Use the keyboard in any messaging app!',
                          style: TextStyle(color: mutedColor, fontSize: 13),
                        ),
                      ),
                    ),
                  )
                else
                  for (final log in _recentLogs)
                    Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ListTile(
                        title: Text(
                          log.transport.name.toUpperCase(),
                          style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 13),
                        ),
                        subtitle: Text(
                          '${log.details}\n${log.targetPackage ?? "Generic Handoff"}',
                          style: TextStyle(color: mutedColor, fontSize: 11),
                        ),
                        trailing: Text(
                          log.status.name,
                          style: const TextStyle(color: Colors.greenAccent, fontSize: 12),
                        ),
                      ),
                    ),
              ],
            ),
    );
  }

  Widget _buildPipelineStep(int step, String title, String desc) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 20,
            height: 20,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
            ),
            child: Text('$step', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                Text(desc, style: TextStyle(color: isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted, fontSize: 11)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

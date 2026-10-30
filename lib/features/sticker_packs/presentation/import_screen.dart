import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:teleboard/app/theme/design_tokens.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/features/sticker_packs/application/telegram_importer.dart';
import 'package:teleboard/features/sticker_packs/domain/featured_pack.dart';
import 'package:teleboard/features/stickers/domain/models.dart';

class ImportScreen extends StatefulWidget {
  const ImportScreen({super.key});

  @override
  State<ImportScreen> createState() => _ImportScreenState();
}

class _ImportScreenState extends State<ImportScreen> {
  final TextEditingController _urlController = TextEditingController();
  final TelegramImporter _importer = TelegramImporter();
  final DatabaseService _db = DatabaseService.instance;

  String? _botToken;
  String? _backendProxyUrl;
  bool _isImporting = false;
  bool _showHowTo = false;
  double _progress = 0.0;
  String? _progressMessage;
  String? _errorMessage;
  String? _activeImportingPack;
  List<StickerPack> _installedPacks = [];

  bool _isPackInstalled(String packName) {
    return _installedPacks.any((p) => p.name.toLowerCase() == packName.toLowerCase());
  }

  @override
  void initState() {
    super.initState();
    _loadCredentials();
    _loadInstalledPacks();
  }

  Future<void> _loadCredentials() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        _botToken = prefs.getString('telegram_bot_token');
        _backendProxyUrl = prefs.getString('cloudflare_worker_url') ?? 'https://teleboard-delivery.samteleboard.workers.dev';
      });
    }
  }

  Future<void> _loadInstalledPacks() async {
    final packs = await _db.getPacks(onlyActive: false);
    if (mounted) {
      setState(() => _installedPacks = packs);
    }
  }

  Future<void> _importPack(String packInput, {bool forceReload = false}) async {
    final trimmed = packInput.trim();
    if (trimmed.isEmpty) {
      setState(() => _errorMessage = 'Please enter a Telegram sticker pack link or name.');
      return;
    }

    final packName = TelegramImporter.extractPackName(trimmed);
    if (packName == null) {
      setState(() {
        _errorMessage = 'Invalid link. Expected format: https://t.me/addstickers/PackName';
      });
      return;
    }

    setState(() {
      _isImporting = true;
      _activeImportingPack = packName;
      _errorMessage = null;
      _progress = 0.0;
      _progressMessage = 'Contacting Telegram API...';
    });

    try {
      final pack = await _importer.importPack(
        packIdentifier: packName,
        botToken: _botToken,
        backendProxyUrl: _backendProxyUrl,
        forceReload: forceReload,
        onProgress: (current, total) {
          if (mounted) {
            setState(() {
              _progress = total > 0 ? (current / total) : 0.0;
              _progressMessage = 'Downloading stickers ($current of $total)...';
            });
          }
        },
      );

      if (mounted) {
        setState(() {
          _isImporting = false;
          _progressMessage = null;
          _urlController.clear();
        });
        _loadInstalledPacks();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Successfully imported "${pack.title}" (${pack.totalStickers} stickers)!')),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isImporting = false;
          _errorMessage = e
              .toString()
              .replaceFirst('TelegramRateLimitException: ', '')
              .replaceFirst('TelegramImportException: ', '');
        });

        if (e is TelegramRateLimitException) {
          final secs = e.retryAfterSeconds ?? 30;
          _errorMessage = 'Telegram rate limit reached. Please retry in $secs seconds or configure a custom token in Settings.';
        }
      }
    } finally {
      if (mounted) {
        setState(() {
          _isImporting = false;
          _activeImportingPack = null;
        });
      }
    }
  }

  Future<void> _deletePack(StickerPack pack) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Sticker Pack'),
        content: Text('Are you sure you want to remove "${pack.title}"? Stored assets will be deleted.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await _db.deletePack(pack.id);
      _loadInstalledPacks();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Deleted pack "${pack.title}"')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;
    final cardBg = isDark ? AppColors.darkCard : AppColors.lightCard;

    return Scaffold(
      appBar: AppBar(title: const Text('Import Stickers')),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // 2. Public Link Import Card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.link_rounded, size: 20, color: textColor),
                      const SizedBox(width: 8),
                      Text(
                        'Import Public Telegram Pack',
                        style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Paste any Telegram sticker set link to download all stickers for offline keyboard typing.',
                    style: TextStyle(color: mutedColor, fontSize: 13),
                  ),
                  const SizedBox(height: 16),

                  TextField(
                    controller: _urlController,
                    enabled: !_isImporting,
                    decoration: InputDecoration(
                      hintText: 'https://t.me/addstickers/MemeManMemesta',
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.paste_rounded, size: 20),
                        onPressed: () async {
                          final data = await Clipboard.getData('text/plain');
                          if (data?.text != null) {
                            _urlController.text = data!.text!.trim();
                          }
                        },
                        tooltip: 'Paste from clipboard',
                      ),
                    ),
                    onSubmitted: (val) {
                      if (!_isImporting && val.trim().isNotEmpty) {
                        _importPack(val);
                      }
                    },
                  ),
                  const SizedBox(height: 14),

                  if (_errorMessage != null) ...[
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: Colors.redAccent.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline, size: 18, color: Colors.redAccent),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _errorMessage!,
                              style: const TextStyle(color: Colors.redAccent, fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],

                  if (_isImporting) ...[
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: _progress > 0 ? _progress : null,
                        minHeight: 6,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _progressMessage ?? 'Importing stickers...',
                      style: TextStyle(color: mutedColor, fontSize: 12),
                    ),
                    const SizedBox(height: 14),
                  ],

                  SizedBox(
                    width: double.infinity,
                    height: 44,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isDark ? AppColors.white : AppColors.black,
                        foregroundColor: isDark ? AppColors.black : AppColors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                      ),
                      onPressed: _isImporting ? null : () => _importPack(_urlController.text),
                      child: Text(
                        _isImporting ? 'Downloading...' : 'Import Pack',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 2.3. Featured Starter Pack Shortcuts (1-Tap Import)
          _buildFeaturedShortcutsCard(isDark, textColor, mutedColor, cardBg),
          const SizedBox(height: 16),

          // 2.5. Hidden / Collapsible "How to Import Stickers" Guide
          _buildHowToImportCard(isDark, textColor, mutedColor, cardBg),
          const SizedBox(height: 24),

          // 4. Installed Packs Section
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Installed Packs (${_installedPacks.length})',
                style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 15),
              ),
              if (_installedPacks.isNotEmpty)
                IconButton(
                  icon: const Icon(Icons.refresh, size: 18),
                  onPressed: _loadInstalledPacks,
                  tooltip: 'Refresh list',
                ),
            ],
          ),
          const SizedBox(height: 8),

          if (_installedPacks.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Center(
                  child: Text(
                    'No sticker packs installed yet.\nPaste a Telegram sticker pack link above to import.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: mutedColor, fontSize: 13, height: 1.4),
                  ),
                ),
              ),
            )
          else
            ..._installedPacks.map((pack) {
              final isStarter = pack.sourceType == 'starter' || pack.sourceType == 'bundled';
              return Card(
                margin: const EdgeInsets.only(bottom: 8.0),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  title: Text(
                    pack.title.isNotEmpty ? pack.title : pack.name,
                    style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  subtitle: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          isStarter ? 'STARTER' : 'TELEGRAM',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: isDark ? Colors.white70 : Colors.black87,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '${pack.totalStickers} stickers',
                        style: TextStyle(color: mutedColor, fontSize: 12),
                      ),
                    ],
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (!isStarter)
                        IconButton(
                          icon: const Icon(Icons.refresh, size: 20),
                          onPressed: _isImporting ? null : () => _importPack(pack.name, forceReload: true),
                          tooltip: 'Re-import / Update stickers',
                        ),
                      IconButton(
                        icon: const Icon(Icons.delete_outline, size: 20, color: Colors.redAccent),
                        onPressed: () => _deletePack(pack),
                        tooltip: 'Remove pack',
                      ),
                    ],
                  ),
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildHowToImportCard(bool isDark, Color textColor, Color mutedColor, Color cardBg) {
    return Card(
      color: cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => setState(() => _showHowTo = !_showHowTo),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 14.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E2638) : const Color(0xFFE8F0FE),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.lightbulb_outline_rounded,
                      size: 18,
                      color: isDark ? const Color(0xFFA8C7FA) : const Color(0xFF1A73E8),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'How to Find & Import Stickers',
                          style: TextStyle(
                            color: textColor,
                            fontWeight: FontWeight.w600,
                            fontSize: 14,
                          ),
                        ),
                        Text(
                          _showHowTo ? 'Tap to collapse guide' : 'Tap to see step-by-step instructions',
                          style: TextStyle(color: mutedColor, fontSize: 11.5),
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    _showHowTo ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded,
                    color: mutedColor,
                    size: 22,
                  ),
                ],
              ),
              if (_showHowTo) ...[
                const SizedBox(height: 14),
                Divider(height: 1, color: mutedColor.withValues(alpha: 0.15)),
                const SizedBox(height: 14),
                _buildGuideStep(
                  step: '1',
                  title: 'Find a Sticker Pack on Telegram or Web',
                  description:
                      'In any Telegram chat, tap on a sticker and choose "Add Stickers" to see its pack page. You can also explore sticker libraries like combot.org/stickers or tstickers.com.',
                  textColor: textColor,
                  mutedColor: mutedColor,
                  isDark: isDark,
                ),
                const SizedBox(height: 12),
                _buildGuideStep(
                  step: '2',
                  title: 'Copy the Link or Pack Name',
                  description:
                      'Tap "Share Sticker Pack" and copy the link (e.g. https://t.me/addstickers/pepe) or simply copy the short name (e.g. pepe).',
                  textColor: textColor,
                  mutedColor: mutedColor,
                  isDark: isDark,
                ),
                const SizedBox(height: 12),
                _buildGuideStep(
                  step: '3',
                  title: 'Paste and Tap "Import Pack"',
                  description:
                      'Paste the link or name into the box above and tap Import Pack. Teleflair automatically downloads, optimizes, and indexes all stickers.',
                  textColor: textColor,
                  mutedColor: mutedColor,
                  isDark: isDark,
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E1E24) : const Color(0xFFF1F1F4),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.offline_bolt_rounded, size: 18, color: Colors.amber),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Once imported, all stickers work 100% offline with zero latency directly on your keyboard!',
                          style: TextStyle(color: mutedColor, fontSize: 11.5, height: 1.35),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGuideStep({
    required String step,
    required String title,
    required String description,
    required Color textColor,
    required Color mutedColor,
    required bool isDark,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 22,
          height: 22,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF2D3A54) : const Color(0xFFD2E3FC),
            shape: BoxShape.circle,
          ),
          child: Text(
            step,
            style: TextStyle(
              color: isDark ? const Color(0xFFA8C7FA) : const Color(0xFF0D47A1),
              fontWeight: FontWeight.bold,
              fontSize: 11,
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TextStyle(
                  color: textColor,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                description,
                style: TextStyle(color: mutedColor, fontSize: 12, height: 1.35),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildFeaturedShortcutsCard(bool isDark, Color textColor, Color mutedColor, Color cardBg) {
    return Card(
      color: cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(18.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: const Color(0xFF5931FE).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.bolt_rounded,
                    color: Color(0xFF5931FE),
                    size: 20,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Popular Starter Packs',
                        style: TextStyle(
                          color: textColor,
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '1-tap shortcuts to popular sticker sets for quick setup:',
                        style: TextStyle(color: mutedColor, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // The 4 Featured Sticker Packs
            ...kFeaturedStickerPacks.map((pack) {
              final isInstalled = _isPackInstalled(pack.packName);
              final isCurrentlyImporting = _activeImportingPack == pack.packName;

              return Container(
                margin: const EdgeInsets.only(bottom: 8.0),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF1E1E22) : const Color(0xFFF9F9FB),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
                    width: 0.8,
                  ),
                ),
                child: Row(
                  children: [
                    // Avatar / Emoji
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: isDark ? const Color(0xFF27272A) : Colors.white,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: isDark ? const Color(0xFF3F3F46) : const Color(0xFFE4E4E7),
                        ),
                      ),
                      child: Center(
                        child: Text(pack.emoji, style: const TextStyle(fontSize: 22)),
                      ),
                    ),
                    const SizedBox(width: 12),

                    // Pack Title & Description
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Flexible(
                                child: Text(
                                  pack.title,
                                  style: TextStyle(
                                    color: textColor,
                                    fontWeight: FontWeight.w600,
                                    fontSize: 13,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF5931FE).withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  pack.category,
                                  style: const TextStyle(
                                    fontSize: 9.5,
                                    fontWeight: FontWeight.w600,
                                    color: Color(0xFF5931FE),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 3),
                          Text(
                            pack.description,
                            style: TextStyle(color: mutedColor, fontSize: 11),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 10),

                    // Action Button or Installed Badge
                    if (isInstalled)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: Colors.green.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(AppRadius.pill),
                          border: Border.all(color: Colors.green.withValues(alpha: 0.3)),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.check, size: 14, color: Colors.green),
                            SizedBox(width: 4),
                            Text(
                              'Installed',
                              style: TextStyle(
                                color: Colors.green,
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      )
                    else if (isCurrentlyImporting)
                      const SizedBox(
                        width: 28,
                        height: 28,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF5931FE)),
                        ),
                      )
                    else
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF5931FE),
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          minimumSize: Size.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                          elevation: 0,
                        ),
                        onPressed: _isImporting ? null : () => _importPack(pack.url),
                        child: const Text(
                          '1-Tap Import',
                          style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
                        ),
                      ),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}

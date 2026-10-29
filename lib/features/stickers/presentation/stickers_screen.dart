import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:teleboard/app/theme/design_tokens.dart';
import 'package:teleboard/core/storage/database_service.dart';
import 'package:teleboard/features/stickers/domain/models.dart';
import 'package:teleboard/features/text_art/application/text_art_converter.dart';

class StickersScreen extends StatefulWidget {
  const StickersScreen({super.key});

  @override
  State<StickersScreen> createState() => _StickersScreenState();
}

class _StickersScreenState extends State<StickersScreen> {
  final DatabaseService _db = DatabaseService.instance;
  List<StickerPack> _packs = [];
  List<StickerItem> _stickers = [];
  String _selectedFilter = 'all'; // 'all', 'recents', 'favorites', or packId
  String _searchQuery = '';
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final packs = await _db.getPacks();
      List<StickerItem> stickers = [];

      if (_selectedFilter == 'all') {
        for (final p in packs) {
          final items = await _db.getStickersByPack(p.id);
          stickers.addAll(items);
        }
      } else if (_selectedFilter == 'recents') {
        stickers = await _db.getRecents();
      } else if (_selectedFilter == 'favorites') {
        stickers = await _db.getFavorites();
      } else {
        stickers = await _db.getStickersByPack(_selectedFilter);
      }

      if (mounted) {
        setState(() {
          _packs = packs;
          _stickers = stickers;
        });
      }
    } catch (e) {
      debugPrint('Error loading stickers: $e');
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  List<StickerItem> get _filteredStickers {
    if (_searchQuery.trim().isEmpty) return _stickers;
    final q = _searchQuery.trim().toLowerCase();
    return _stickers.where((s) => s.emoji.contains(q) || s.packId.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Stickers'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, size: 20),
            onPressed: _loadData,
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: Column(
        children: [
          // Search Bar
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: TextField(
              decoration: InputDecoration(
                prefixIcon: Icon(Icons.search, size: 20, color: mutedColor),
                hintText: 'Search stickers by emoji or tag...',
                suffixIcon: _searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          setState(() => _searchQuery = '');
                        },
                      )
                    : null,
              ),
              onChanged: (val) {
                setState(() => _searchQuery = val);
              },
            ),
          ),

          // Horizontal Filter Chips
          SizedBox(
            height: 40,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              children: [
                _buildFilterChip('All', 'all'),
                _buildFilterChip('Recents', 'recents'),
                _buildFilterChip('Favorites', 'favorites'),
                ..._packs.map((p) => _buildFilterChip(p.title.isNotEmpty ? p.title : p.name, p.id)),
              ],
            ),
          ),
          const SizedBox(height: 8),

          // Sticker Grid
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator(strokeWidth: 2))
                : _filteredStickers.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.auto_awesome_motion_outlined, size: 48, color: mutedColor),
                            const SizedBox(height: 12),
                            Text(
                              _packs.isEmpty ? 'No sticker packs installed' : 'No stickers found',
                              style: TextStyle(color: textColor, fontWeight: FontWeight.w600, fontSize: 16),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              _packs.isEmpty
                                  ? 'Head over to the Import tab to add your favorite Telegram sticker packs'
                                  : 'Try a different filter or search term',
                              style: TextStyle(color: mutedColor, fontSize: 13),
                            ),
                          ],
                        ),
                      )
                    : GridView.builder(
                        padding: const EdgeInsets.all(16.0),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 4,
                          crossAxisSpacing: 10,
                          mainAxisSpacing: 10,
                        ),
                        itemCount: _filteredStickers.length,
                        itemBuilder: (context, index) {
                          final item = _filteredStickers[index];
                          return _buildStickerTile(item);
                        },
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String key) {
    final isSelected = _selectedFilter == key;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final activeBg = isDark ? AppColors.darkActiveTab : AppColors.lightActiveTab;
    final inactiveBg = isDark ? AppColors.darkCard : AppColors.lightCard;
    final activeText = isDark ? AppColors.darkText : AppColors.lightText;
    final inactiveText = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Padding(
      padding: const EdgeInsets.only(right: 8.0),
      child: GestureDetector(
        onTap: () {
          setState(() => _selectedFilter = key);
          _loadData();
        },
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? activeBg : inactiveBg,
            borderRadius: BorderRadius.circular(AppRadius.pill),
            border: Border.all(
              color: isSelected ? (isDark ? AppColors.white : AppColors.black) : Colors.transparent,
              width: 1,
            ),
          ),
          child: Text(
            label,
            style: TextStyle(
              color: isSelected ? activeText : inactiveText,
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              fontSize: 13,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStickerTile(StickerItem sticker) {
    return GestureDetector(
      onTap: () => _openStickerDetail(sticker),
      child: Container(
        decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark
              ? AppColors.darkCard
              : AppColors.lightCard,
          borderRadius: BorderRadius.circular(AppRadius.md),
          border: Border.all(
            color: Theme.of(context).brightness == Brightness.dark
                ? AppColors.darkBorder
                : AppColors.lightBorder,
          ),
        ),
        padding: const EdgeInsets.all(6),
        child: Stack(
          alignment: Alignment.center,
          children: [
            File(sticker.filePath).existsSync()
                ? Image.file(
                    File(sticker.filePath),
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => Text(sticker.emoji, style: const TextStyle(fontSize: 28)),
                  )
                : Text(sticker.emoji, style: const TextStyle(fontSize: 28)),
            if (sticker.isFavorite)
              const Positioned(
                top: 0,
                right: 0,
                child: Icon(Icons.star, size: 12, color: Colors.amber),
              ),
          ],
        ),
      ),
    );
  }

  void _openStickerDetail(StickerItem sticker) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _StickerDetailSheet(
        sticker: sticker,
        onFavoriteToggled: () {
          _db.toggleFavorite(sticker.id);
          _loadData();
          Navigator.pop(context);
        },
        onStickerUsed: () {
          _db.recordStickerUse(sticker.id);
          _loadData();
        },
      ),
    );
  }
}

class _StickerDetailSheet extends StatelessWidget {
  final StickerItem sticker;
  final VoidCallback onFavoriteToggled;
  final VoidCallback onStickerUsed;

  const _StickerDetailSheet({
    required this.sticker,
    required this.onFavoriteToggled,
    required this.onStickerUsed,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppColors.darkCard : AppColors.lightCard;
    final textColor = isDark ? AppColors.darkText : AppColors.lightText;
    final mutedColor = isDark ? AppColors.darkTextMuted : AppColors.lightTextMuted;

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: mutedColor.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: 20),

          // Sticker Preview Container
          Container(
            width: 180,
            height: 180,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF121214) : const Color(0xFFEEEEF0),
              borderRadius: BorderRadius.circular(16),
            ),
            child: File(sticker.filePath).existsSync()
                ? Image.file(File(sticker.filePath), fit: BoxFit.contain)
                : Center(child: Text(sticker.emoji, style: const TextStyle(fontSize: 64))),
          ),
          const SizedBox(height: 16),

          Text(
            '${sticker.emoji} Sticker Preview',
            style: TextStyle(color: textColor, fontSize: 18, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 4),
          Text(
            '${sticker.width}x${sticker.height} • ${(sticker.fileSize / 1024).toStringAsFixed(1)} KB • ${sticker.mimeType}',
            style: TextStyle(color: mutedColor, fontSize: 12),
          ),
          const SizedBox(height: 24),

          // Action: Send Sticker Primary Action
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: isDark ? AppColors.white : AppColors.black,
                foregroundColor: isDark ? AppColors.black : AppColors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
              ),
              onPressed: () {
                onStickerUsed();
                Navigator.pop(context);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Send Sticker action invoked via Smart Delivery')),
                );
              },
              icon: const Icon(Icons.send_rounded, size: 18),
              label: const Text('Send Sticker', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            ),
          ),
          const SizedBox(height: 12),

          // Secondary actions: Favorite & Text-Art
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                    side: BorderSide(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                  ),
                  onPressed: onFavoriteToggled,
                  icon: Icon(
                    sticker.isFavorite ? Icons.star : Icons.star_border,
                    size: 18,
                    color: sticker.isFavorite ? Colors.amber : textColor,
                  ),
                  label: Text(
                    sticker.isFavorite ? 'Favorited' : 'Favorite',
                    style: TextStyle(color: textColor, fontSize: 13),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
                    side: BorderSide(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                  ),
                  onPressed: () {
                    final file = File(sticker.filePath);
                    if (file.existsSync()) {
                      final bytes = file.readAsBytesSync();
                      final textArt = TextArtConverter.convertBytes(bytes);
                      Clipboard.setData(ClipboardData(text: textArt));
                      Navigator.pop(context);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Copied Text-Art silhouette to clipboard!')),
                      );
                    }
                  },
                  icon: Icon(Icons.short_text, size: 18, color: textColor),
                  label: Text('Copy Text-Art', style: TextStyle(color: textColor, fontSize: 13)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

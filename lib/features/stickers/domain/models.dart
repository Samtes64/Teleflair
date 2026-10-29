/// Represents an imported or bundled sticker pack.
class StickerPack {
  final String id;
  final String name; // Telegram short name / slug
  final String title; // Display title
  final String sourceType; // 'telegram', 'bundled', 'custom'
  final int totalStickers;
  final DateTime installedAt;
  final bool isActive;

  const StickerPack({
    required this.id,
    required this.name,
    required this.title,
    this.sourceType = 'telegram',
    required this.totalStickers,
    required this.installedAt,
    this.isActive = true,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'title': title,
    'source_type': sourceType,
    'total_stickers': totalStickers,
    'installed_at': installedAt.toIso8601String(),
    'is_active': isActive ? 1 : 0,
  };

  factory StickerPack.fromMap(Map<String, dynamic> map) => StickerPack(
    id: map['id'] as String,
    name: map['name'] as String,
    title: map['title'] as String,
    sourceType: map['source_type'] as String? ?? 'telegram',
    totalStickers: map['total_stickers'] as int? ?? 0,
    installedAt: DateTime.parse(map['installed_at'] as String),
    isActive: (map['is_active'] as int? ?? 1) == 1,
  );
}

/// Represents an individual sticker asset.
class StickerItem {
  final String id;
  final String packId;
  final String fileId;
  final String emoji;
  final String filePath;
  final int width;
  final int height;
  final int fileSize;
  final String mimeType;
  final bool isAnimated;
  final String contentHash;
  final DateTime addedAt;
  final bool isFavorite;
  final int useCount;

  const StickerItem({
    required this.id,
    required this.packId,
    required this.fileId,
    required this.emoji,
    required this.filePath,
    this.width = 512,
    this.height = 512,
    this.fileSize = 0,
    this.mimeType = 'image/webp',
    this.isAnimated = false,
    required this.contentHash,
    required this.addedAt,
    this.isFavorite = false,
    this.useCount = 0,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'pack_id': packId,
    'file_id': fileId,
    'emoji': emoji,
    'file_path': filePath,
    'width': width,
    'height': height,
    'file_size': fileSize,
    'mime_type': mimeType,
    'is_animated': isAnimated ? 1 : 0,
    'content_hash': contentHash,
    'added_at': addedAt.toIso8601String(),
  };

  factory StickerItem.fromMap(Map<String, dynamic> map, {bool isFavorite = false, int useCount = 0}) =>
      StickerItem(
        id: map['id'] as String,
        packId: map['pack_id'] as String,
        fileId: map['file_id'] as String,
        emoji: map['emoji'] as String? ?? '⭐',
        filePath: map['file_path'] as String,
        width: map['width'] as int? ?? 512,
        height: map['height'] as int? ?? 512,
        fileSize: map['file_size'] as int? ?? 0,
        mimeType: map['mime_type'] as String? ?? 'image/webp',
        isAnimated: (map['is_animated'] as int? ?? 0) == 1,
        contentHash: map['content_hash'] as String? ?? '',
        addedAt: DateTime.parse(map['added_at'] as String),
        isFavorite: isFavorite,
        useCount: useCount,
      );

  StickerItem copyWith({
    bool? isFavorite,
    int? useCount,
    String? filePath,
  }) {
    return StickerItem(
      id: id,
      packId: packId,
      fileId: fileId,
      emoji: emoji,
      filePath: filePath ?? this.filePath,
      width: width,
      height: height,
      fileSize: fileSize,
      mimeType: mimeType,
      isAnimated: isAnimated,
      contentHash: contentHash,
      addedAt: addedAt,
      isFavorite: isFavorite ?? this.isFavorite,
      useCount: useCount ?? this.useCount,
    );
  }
}

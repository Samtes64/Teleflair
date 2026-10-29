import 'dart:io';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'package:teleboard/features/delivery/domain/delivery_state.dart';
import 'package:teleboard/features/stickers/domain/models.dart';

class DatabaseService {
  static DatabaseService? _instance;
  static Database? _database;

  DatabaseService._();

  static DatabaseService get instance {
    _instance ??= DatabaseService._();
    return _instance!;
  }

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final dbDir = await getDatabasesPath();
    final dbPath = p.join(dbDir, 'teleboard.db');

    return await openDatabase(
      dbPath,
      version: 2,
      onOpen: (db) async {
        await db.execute('''
          CREATE TABLE IF NOT EXISTS user_dictionary (
            word TEXT PRIMARY KEY,
            frequency INTEGER DEFAULT 1,
            last_used INTEGER
          )
        ''');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        await db.execute('''
          CREATE TABLE IF NOT EXISTS user_dictionary (
            word TEXT PRIMARY KEY,
            frequency INTEGER DEFAULT 1,
            last_used INTEGER
          )
        ''');
      },
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE sticker_packs (
            id TEXT PRIMARY KEY,
            name TEXT UNIQUE,
            title TEXT,
            source_type TEXT,
            total_stickers INTEGER,
            installed_at TEXT,
            is_active INTEGER DEFAULT 1
          )
        ''');

        await db.execute('''
          CREATE TABLE stickers (
            id TEXT PRIMARY KEY,
            pack_id TEXT,
            file_id TEXT,
            emoji TEXT,
            file_path TEXT,
            width INTEGER,
            height INTEGER,
            file_size INTEGER,
            mime_type TEXT,
            is_animated INTEGER,
            content_hash TEXT,
            added_at TEXT,
            FOREIGN KEY(pack_id) REFERENCES sticker_packs(id) ON DELETE CASCADE
          )
        ''');

        await db.execute('''
          CREATE TABLE favorites (
            sticker_id TEXT PRIMARY KEY,
            added_at TEXT,
            FOREIGN KEY(sticker_id) REFERENCES stickers(id) ON DELETE CASCADE
          )
        ''');

        await db.execute('''
          CREATE TABLE recents (
            sticker_id TEXT PRIMARY KEY,
            used_at TEXT,
            use_count INTEGER DEFAULT 1,
            FOREIGN KEY(sticker_id) REFERENCES stickers(id) ON DELETE CASCADE
          )
        ''');

        await db.execute('''
          CREATE TABLE delivery_logs (
            id TEXT PRIMARY KEY,
            sticker_id TEXT,
            transport TEXT,
            status TEXT,
            target_package TEXT,
            details TEXT,
            timestamp TEXT
          )
        ''');

        await db.execute('CREATE INDEX idx_stickers_pack_id ON stickers(pack_id)');
        await db.execute('CREATE INDEX idx_recents_used_at ON recents(used_at DESC)');
      },
    );
  }

  // --- Sticker Packs ---

  Future<List<StickerPack>> getPacks({bool onlyActive = true}) async {
    final db = await database;
    final where = onlyActive ? 'is_active = 1' : null;
    final results = await db.query('sticker_packs', where: where, orderBy: 'installed_at DESC');
    return results.map((m) => StickerPack.fromMap(m)).toList();
  }

  Future<StickerPack?> getPackByName(String name) async {
    final db = await database;
    final results = await db.query('sticker_packs', where: 'name = ?', whereArgs: [name], limit: 1);
    if (results.isEmpty) return null;
    return StickerPack.fromMap(results.first);
  }

  Future<void> insertPack(StickerPack pack) async {
    final db = await database;
    await db.insert('sticker_packs', pack.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> deletePack(String packId) async {
    final db = await database;
    await db.transaction((txn) async {
      // Find sticker file paths to clean up
      final stickers = await txn.query('stickers', columns: ['file_path'], where: 'pack_id = ?', whereArgs: [packId]);
      for (final s in stickers) {
        final path = s['file_path'] as String?;
        if (path != null) {
          try {
            final file = File(path);
            if (await file.exists()) {
              await file.delete();
            }
          } catch (_) {}
        }
      }
      await txn.delete('favorites', where: 'sticker_id IN (SELECT id FROM stickers WHERE pack_id = ?)', whereArgs: [packId]);
      await txn.delete('recents', where: 'sticker_id IN (SELECT id FROM stickers WHERE pack_id = ?)', whereArgs: [packId]);
      await txn.delete('stickers', where: 'pack_id = ?', whereArgs: [packId]);
      await txn.delete('sticker_packs', where: 'id = ?', whereArgs: [packId]);
    });
  }

  // --- Stickers ---

  Future<void> insertSticker(StickerItem sticker) async {
    final db = await database;
    await db.insert('stickers', sticker.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> insertStickers(List<StickerItem> stickers) async {
    final db = await database;
    final batch = db.batch();
    for (final s in stickers) {
      batch.insert('stickers', s.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
    }
    await batch.commit(noResult: true);
  }

  Future<List<StickerItem>> getStickersByPack(String packId) async {
    final db = await database;
    final results = await db.rawQuery('''
      SELECT s.*, 
        CASE WHEN f.sticker_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav,
        COALESCE(r.use_count, 0) AS use_count
      FROM stickers s
      LEFT JOIN favorites f ON s.id = f.sticker_id
      LEFT JOIN recents r ON s.id = r.sticker_id
      WHERE s.pack_id = ?
      ORDER BY s.added_at ASC
    ''', [packId]);

    return results.map((m) {
      return StickerItem.fromMap(
        m,
        isFavorite: (m['is_fav'] as int? ?? 0) == 1,
        useCount: m['use_count'] as int? ?? 0,
      );
    }).toList();
  }

  Future<List<StickerItem>> getFavorites() async {
    final db = await database;
    final results = await db.rawQuery('''
      SELECT s.*, 1 AS is_fav, COALESCE(r.use_count, 0) AS use_count
      FROM stickers s
      INNER JOIN favorites f ON s.id = f.sticker_id
      LEFT JOIN recents r ON s.id = r.sticker_id
      ORDER BY f.added_at DESC
    ''');
    return results.map((m) => StickerItem.fromMap(m, isFavorite: true, useCount: m['use_count'] as int? ?? 0)).toList();
  }

  Future<void> toggleFavorite(String stickerId) async {
    final db = await database;
    final existing = await db.query('favorites', where: 'sticker_id = ?', whereArgs: [stickerId]);
    if (existing.isNotEmpty) {
      await db.delete('favorites', where: 'sticker_id = ?', whereArgs: [stickerId]);
    } else {
      await db.insert('favorites', {
        'sticker_id': stickerId,
        'added_at': DateTime.now().toIso8601String(),
      });
    }
  }

  Future<List<StickerItem>> getRecents({int limit = 30}) async {
    final db = await database;
    final results = await db.rawQuery('''
      SELECT s.*,
        CASE WHEN f.sticker_id IS NOT NULL THEN 1 ELSE 0 END AS is_fav,
        r.use_count
      FROM stickers s
      INNER JOIN recents r ON s.id = r.sticker_id
      LEFT JOIN favorites f ON s.id = f.sticker_id
      ORDER BY r.used_at DESC
      LIMIT ?
    ''', [limit]);

    return results.map((m) => StickerItem.fromMap(m, isFavorite: (m['is_fav'] as int? ?? 0) == 1, useCount: m['use_count'] as int? ?? 0)).toList();
  }

  Future<void> recordStickerUse(String stickerId) async {
    final db = await database;
    await db.rawInsert('''
      INSERT INTO recents(sticker_id, used_at, use_count)
      VALUES(?, ?, 1)
      ON CONFLICT(sticker_id) DO UPDATE SET
        used_at = excluded.used_at,
        use_count = use_count + 1
    ''', [stickerId, DateTime.now().toIso8601String()]);
  }

  // --- Delivery Logs ---

  Future<void> logDelivery(DeliveryRecord record) async {
    final db = await database;
    await db.insert('delivery_logs', record.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<List<DeliveryRecord>> getRecentDeliveries({int limit = 50}) async {
    final db = await database;
    final results = await db.query('delivery_logs', orderBy: 'timestamp DESC', limit: limit);
    return results.map((m) => DeliveryRecord.fromMap(m)).toList();
  }

  // --- Dummy Pack Cleanup ---

  Future<void> cleanupDummyPacks() async {
    final db = await database;
    await db.delete(
      'stickers',
      where: "pack_id IN ('pack_trending', 'pack_geometric', 'pack_minimalist', 'pack_abstract', 'pack_Minimalist_Icons', 'Minimalist_Icons', 'teleboard_essentials', 'starter_essentials') OR file_id LIKE 'starter_%'",
    );
    await db.delete(
      'sticker_packs',
      where: "id IN ('pack_trending', 'pack_geometric', 'pack_minimalist', 'pack_abstract', 'pack_Minimalist_Icons', 'Minimalist_Icons', 'teleboard_essentials', 'starter_essentials') OR source_type IN ('bundled', 'starter')",
    );
  }

  // --- Storage metrics ---

  Future<Map<String, dynamic>> getStorageMetrics() async {
    final db = await database;
    final packCount = Sqflite.firstIntValue(await db.rawQuery('SELECT COUNT(*) FROM sticker_packs')) ?? 0;
    final stickerCount = Sqflite.firstIntValue(await db.rawQuery('SELECT COUNT(*) FROM stickers')) ?? 0;
    final totalBytes = Sqflite.firstIntValue(await db.rawQuery('SELECT COALESCE(SUM(file_size), 0) FROM stickers')) ?? 0;

    return {
      'packCount': packCount,
      'stickerCount': stickerCount,
      'totalBytes': totalBytes,
    };
  }
}

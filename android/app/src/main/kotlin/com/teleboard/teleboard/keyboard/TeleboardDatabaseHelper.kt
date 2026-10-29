package com.teleboard.teleboard.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

data class StickerPackEntity(
    val id: String,
    val name: String,
    val title: String,
    val sourceType: String,
    val totalStickers: Int,
    val installedAt: String,
    val isActive: Boolean
)

data class StickerEntity(
    val id: String,
    val packId: String,
    val fileId: String,
    val emoji: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSize: Int,
    val mimeType: String,
    val isAnimated: Boolean,
    val contentHash: String,
    val addedAt: String
)

class TeleboardDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, "teleboard.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.setForeignKeyConstraintsEnabled(true)
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        ensureTablesExist(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureTablesExist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureTablesExist(db)
    }

    private fun ensureTablesExist(db: SQLiteDatabase) {
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sticker_packs (
                    id TEXT PRIMARY KEY,
                    name TEXT UNIQUE,
                    title TEXT,
                    source_type TEXT,
                    total_stickers INTEGER,
                    installed_at TEXT,
                    is_active INTEGER DEFAULT 1
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS stickers (
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
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS favorites (
                    sticker_id TEXT PRIMARY KEY,
                    added_at TEXT,
                    FOREIGN KEY(sticker_id) REFERENCES stickers(id) ON DELETE CASCADE
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS recents (
                    sticker_id TEXT PRIMARY KEY,
                    used_at TEXT,
                    use_count INTEGER DEFAULT 1,
                    FOREIGN KEY(sticker_id) REFERENCES stickers(id) ON DELETE CASCADE
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS delivery_logs (
                    id TEXT PRIMARY KEY,
                    sticker_id TEXT,
                    transport TEXT,
                    status TEXT,
                    target_package TEXT,
                    details TEXT,
                    timestamp TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_dictionary (
                    word TEXT PRIMARY KEY,
                    frequency INTEGER DEFAULT 1,
                    last_used INTEGER
                )
            """)
        } catch (_: Exception) {}
    }

    fun getAllPacks(): List<StickerPackEntity> {
        val list = mutableListOf<StickerPackEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM sticker_packs WHERE is_active = 1 ORDER BY installed_at DESC",
            null
        ).use { cursor ->
            val idIdx = cursor.getColumnIndex("id")
            val nameIdx = cursor.getColumnIndex("name")
            val titleIdx = cursor.getColumnIndex("title")
            val srcIdx = cursor.getColumnIndex("source_type")
            val totalIdx = cursor.getColumnIndex("total_stickers")
            val instIdx = cursor.getColumnIndex("installed_at")
            val actIdx = cursor.getColumnIndex("is_active")

            while (cursor.moveToNext()) {
                list.add(
                    StickerPackEntity(
                        id = if (idIdx >= 0) cursor.getString(idIdx) else "",
                        name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                        title = if (titleIdx >= 0) cursor.getString(titleIdx) else "",
                        sourceType = if (srcIdx >= 0) cursor.getString(srcIdx) ?: "telegram" else "telegram",
                        totalStickers = if (totalIdx >= 0) cursor.getInt(totalIdx) else 0,
                        installedAt = if (instIdx >= 0) cursor.getString(instIdx) ?: "" else "",
                        isActive = if (actIdx >= 0) cursor.getInt(actIdx) == 1 else true
                    )
                )
            }
        }
        return list
    }

    fun getPackById(packId: String): StickerPackEntity? {
        try {
            readableDatabase.rawQuery(
                "SELECT * FROM sticker_packs WHERE id = ? LIMIT 1",
                arrayOf(packId)
            ).use { cursor ->
                if (cursor.moveToNext()) {
                    val idIdx = cursor.getColumnIndex("id")
                    val nameIdx = cursor.getColumnIndex("name")
                    val titleIdx = cursor.getColumnIndex("title")
                    val srcIdx = cursor.getColumnIndex("source_type")
                    val totalIdx = cursor.getColumnIndex("total_stickers")
                    val instIdx = cursor.getColumnIndex("installed_at")
                    val actIdx = cursor.getColumnIndex("is_active")
                    return StickerPackEntity(
                        id = if (idIdx >= 0) cursor.getString(idIdx) else "",
                        name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                        title = if (titleIdx >= 0) cursor.getString(titleIdx) else "",
                        sourceType = if (srcIdx >= 0) cursor.getString(srcIdx) ?: "telegram" else "telegram",
                        totalStickers = if (totalIdx >= 0) cursor.getInt(totalIdx) else 0,
                        installedAt = if (instIdx >= 0) cursor.getString(instIdx) ?: "" else "",
                        isActive = if (actIdx >= 0) cursor.getInt(actIdx) == 1 else true
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getStickersByPack(packId: String): List<StickerEntity> {
        val list = mutableListOf<StickerEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM stickers WHERE pack_id = ? ORDER BY added_at ASC",
            arrayOf(packId)
        ).use { cursor ->
            mapStickers(cursor, list)
        }
        return list
    }

    fun getRecentStickers(limit: Int = 30): List<StickerEntity> {
        val list = mutableListOf<StickerEntity>()
        readableDatabase.rawQuery(
            """
            SELECT s.* FROM stickers s
            INNER JOIN recents r ON s.id = r.sticker_id
            ORDER BY r.used_at DESC
            LIMIT ?
            """,
            arrayOf(limit.toString())
        ).use { cursor ->
            mapStickers(cursor, list)
        }
        return list
    }

    fun getFavoriteStickers(): List<StickerEntity> {
        val list = mutableListOf<StickerEntity>()
        readableDatabase.rawQuery(
            """
            SELECT s.* FROM stickers s
            INNER JOIN favorites f ON s.id = f.sticker_id
            ORDER BY f.added_at DESC
            """,
            null
        ).use { cursor ->
            mapStickers(cursor, list)
        }
        return list
    }

    fun getAllStickers(limit: Int = 200): List<StickerEntity> {
        val list = mutableListOf<StickerEntity>()
        try {
            readableDatabase.rawQuery(
                "SELECT * FROM stickers ORDER BY added_at DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                mapStickers(cursor, list)
            }
        } catch (_: Exception) {}
        return list
    }

    fun searchStickers(query: String, limit: Int = 100): List<StickerEntity> {
        val list = mutableListOf<StickerEntity>()
        try {
            val pattern = "%${query.trim()}%"
            readableDatabase.rawQuery(
                """
                SELECT s.* FROM stickers s
                LEFT JOIN sticker_packs p ON s.pack_id = p.id
                WHERE s.emoji LIKE ? OR p.title LIKE ? OR p.name LIKE ?
                ORDER BY s.added_at DESC LIMIT ?
                """,
                arrayOf(pattern, pattern, pattern, limit.toString())
            ).use { cursor ->
                mapStickers(cursor, list)
            }
        } catch (_: Exception) {}
        return list
    }

    fun toggleFavorite(stickerId: String): Boolean {
        val db = writableDatabase
        return try {
            val isFav = readableDatabase.rawQuery(
                "SELECT 1 FROM favorites WHERE sticker_id = ? LIMIT 1",
                arrayOf(stickerId)
            ).use { it.moveToNext() }

            if (isFav) {
                db.delete("favorites", "sticker_id = ?", arrayOf(stickerId))
                false
            } else {
                val values = ContentValues().apply {
                    put("sticker_id", stickerId)
                    put("added_at", System.currentTimeMillis().toString())
                }
                db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupDummyPacks() {
        try {
            val db = writableDatabase
            db.delete(
                "stickers",
                "pack_id IN ('pack_trending', 'pack_geometric', 'pack_minimalist', 'pack_abstract', 'pack_Minimalist_Icons', 'Minimalist_Icons', 'teleboard_essentials', 'starter_essentials') OR content_hash LIKE 'starter_%' OR file_id LIKE 'starter_%'",
                null
            )
            db.delete(
                "sticker_packs",
                "id IN ('pack_trending', 'pack_geometric', 'pack_minimalist', 'pack_abstract', 'pack_Minimalist_Icons', 'Minimalist_Icons', 'teleboard_essentials', 'starter_essentials') OR source_type IN ('bundled', 'starter')",
                null
            )
            val starterDir = File(context.filesDir, "stickers/starter")
            if (starterDir.exists()) {
                starterDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    fun recordStickerUsage(stickerId: String) {
        val db = writableDatabase
        val now = System.currentTimeMillis().toString()
        db.execSQL(
            """
            INSERT INTO recents (sticker_id, used_at, use_count)
            VALUES (?, ?, 1)
            ON CONFLICT(sticker_id) DO UPDATE SET
                used_at = excluded.used_at,
                use_count = use_count + 1
            """,
            arrayOf(stickerId, now)
        )
    }

    fun logDelivery(
        id: String,
        stickerId: String,
        transport: String,
        status: String,
        targetPackage: String?,
        details: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("sticker_id", stickerId)
            put("transport", transport)
            put("status", status)
            put("target_package", targetPackage)
            put("details", details)
            put("timestamp", System.currentTimeMillis().toString())
        }
        db.insertWithOnConflict("delivery_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun mapStickers(cursor: android.database.Cursor, list: MutableList<StickerEntity>) {
        val idIdx = cursor.getColumnIndex("id")
        val packIdIdx = cursor.getColumnIndex("pack_id")
        val fileIdIdx = cursor.getColumnIndex("file_id")
        val emojiIdx = cursor.getColumnIndex("emoji")
        val pathIdx = cursor.getColumnIndex("file_path")
        val wIdx = cursor.getColumnIndex("width")
        val hIdx = cursor.getColumnIndex("height")
        val sizeIdx = cursor.getColumnIndex("file_size")
        val mimeIdx = cursor.getColumnIndex("mime_type")
        val animIdx = cursor.getColumnIndex("is_animated")
        val hashIdx = cursor.getColumnIndex("content_hash")
        val addIdx = cursor.getColumnIndex("added_at")

        while (cursor.moveToNext()) {
            val rawPath = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
            // Ensure absolute file path exists
            val filePath = if (rawPath.startsWith("/")) rawPath else File(context.filesDir, rawPath).absolutePath

            list.add(
                StickerEntity(
                    id = if (idIdx >= 0) cursor.getString(idIdx) else "",
                    packId = if (packIdIdx >= 0) cursor.getString(packIdIdx) else "",
                    fileId = if (fileIdIdx >= 0) cursor.getString(fileIdIdx) else "",
                    emoji = if (emojiIdx >= 0) cursor.getString(emojiIdx) ?: "⭐" else "⭐",
                    filePath = filePath,
                    width = if (wIdx >= 0) cursor.getInt(wIdx) else 512,
                    height = if (hIdx >= 0) cursor.getInt(hIdx) else 512,
                    fileSize = if (sizeIdx >= 0) cursor.getInt(sizeIdx) else 0,
                    mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "image/webp" else "image/webp",
                    isAnimated = if (animIdx >= 0) cursor.getInt(animIdx) == 1 else false,
                    contentHash = if (hashIdx >= 0) cursor.getString(hashIdx) ?: "" else "",
                    addedAt = if (addIdx >= 0) cursor.getString(addIdx) ?: "" else ""
                )
            )
        }
    }

    fun learnWord(word: String) {
        val clean = word.trim().lowercase().filter { it.isLetter() || it == '\'' }
        if (clean.length < 2) return

        try {
            val db = writableDatabase
            ensureTablesExist(db)
            db.execSQL("""
                INSERT INTO user_dictionary (word, frequency, last_used)
                VALUES (?, 1, ?)
                ON CONFLICT(word) DO UPDATE SET
                frequency = frequency + 1,
                last_used = ?
            """, arrayOf<Any>(clean, System.currentTimeMillis(), System.currentTimeMillis()))
        } catch (_: Exception) {}
    }

    fun getUserWords(limit: Int = 1000): List<Pair<String, Int>> {
        val list = mutableListOf<Pair<String, Int>>()
        try {
            val db = readableDatabase
            ensureTablesExist(db)
            db.rawQuery(
                "SELECT word, frequency FROM user_dictionary ORDER BY frequency DESC, last_used DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                val wIdx = cursor.getColumnIndex("word")
                val fIdx = cursor.getColumnIndex("frequency")
                while (cursor.moveToNext()) {
                    if (wIdx >= 0 && fIdx >= 0) {
                        list.add(Pair(cursor.getString(wIdx), cursor.getInt(fIdx)))
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }
}

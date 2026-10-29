package com.teleboard.teleboard.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import com.teleboard.teleboard.keyboard.context.ContextArtProfile
import com.teleboard.teleboard.keyboard.context.PlatformProfiles
import com.teleboard.teleboard.keyboard.context.SmartContextArtAdapter

enum class DeliveryMode {
    RICH_CONTENT,
    INTENT_ATTACHMENT,
    CLIPBOARD_FALLBACK,
    CLOUDFLARE_LINK,
    TEXT_ART_FALLBACK,
    FAILED
}

enum class DeliveryChoice {
    AUTO_ADAPT,
    RICH_IMAGE,
    UNIVERSAL_SMS,
    COPY_CLIPBOARD,
    TEXT_ART,
    ART_MEME_DETAIL,
    ART_TELEGRAM,
    ART_CLEAN_SMS,
    ART_ASCII
}

data class DeliveryResult(
    val success: Boolean,
    val mode: DeliveryMode,
    val details: String
)

class SmartDeliveryCoordinator(
    private val context: Context,
    private val dbHelper: TeleboardDatabaseHelper
) {

    companion object {
        const val DEFAULT_WORKER_URL = "https://teleboard-delivery.samteleboard.workers.dev"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val supportedMimeTypes = arrayOf(
        "image/webp",
        "image/png",
        "image/gif",
        "image/jpeg"
    )

    private val socialCommentApps = setOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.linkedin.android",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok
        "com.google.android.youtube",
        "com.pinterest",
        "com.snapchat.android"
    )

    fun getEffectiveWorkerUrl(prefs: SharedPreferences): String {
        val raw = prefs.getString("flutter.cloudflare_worker_url", null)
            ?: prefs.getString("cloudflare_worker_url", null)
        return if (!raw.isNullOrEmpty() && !raw.contains("example.workers.dev")) {
            raw.trim().trimEnd('/')
        } else {
            DEFAULT_WORKER_URL
        }
    }

    fun isCommentSectionOrSocialApp(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val pkg = packageName.lowercase()
        return pkg in socialCommentApps ||
               pkg.contains("chrome") ||
               pkg.contains("browser") ||
               pkg.contains("firefox") ||
               pkg.contains("opera") ||
               pkg.contains("brave")
    }

    fun isSmsMessagingApp(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val pkg = packageName.lowercase()
        return pkg == "com.google.android.apps.messaging" ||
               pkg == "com.samsung.android.messaging" ||
               pkg == "com.android.mms" ||
               pkg.contains("messaging") ||
               pkg.contains("sms") ||
               pkg.contains("mms") ||
               pkg == "com.simplemobiletools.sms.messenger" ||
               pkg == "com.moez.QKSMS"
    }

    fun sendSticker(
        sticker: StickerEntity,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        choice: DeliveryChoice = DeliveryChoice.AUTO_ADAPT,
        subContext: String? = null
    ): DeliveryResult {
        // 0a. Specific Art options
        if (choice == DeliveryChoice.ART_MEME_DETAIL) {
            val memeProfile = PlatformProfiles.TELEGRAM_MOBILE.copy(
                displayName = "Meme Detail"
            )
            return sendContextArt(sticker, inputConnection, editorInfo, subContext, overrideProfile = memeProfile, forceMemeDither = true)
        }

        if (choice == DeliveryChoice.ART_TELEGRAM) {
            return sendContextArt(sticker, inputConnection, editorInfo, subContext, overrideProfile = PlatformProfiles.TELEGRAM_MOBILE)
        }

        if (choice == DeliveryChoice.ART_CLEAN_SMS) {
            return sendContextArt(sticker, inputConnection, editorInfo, subContext, overrideProfile = PlatformProfiles.SMS_MESSAGING)
        }

        if (choice == DeliveryChoice.ART_ASCII) {
            val asciiProfile = PlatformProfiles.resolveProfile(editorInfo?.packageName, subContext)
            return sendContextArt(sticker, inputConnection, editorInfo, subContext, overrideProfile = asciiProfile, forceAscii = true)
        }

        // 0b. Explicit Text Art requested by user
        if (choice == DeliveryChoice.TEXT_ART) {
            return sendContextArt(sticker, inputConnection, editorInfo, subContext)
        }

        // 0c. Explicit Universal SMS requested by user
        if (choice == DeliveryChoice.UNIVERSAL_SMS) {
            return sendUniversalSms(sticker, inputConnection, editorInfo)
        }

        // 0d. Explicit Copy to Clipboard requested by user
        if (choice == DeliveryChoice.COPY_CLIPBOARD) {
            return copyStickerToClipboard(sticker, inputConnection, editorInfo)
        }

        // 0e. Auto-Adapt in comment sections: directly deliver as calibrated art!
        if (choice == DeliveryChoice.AUTO_ADAPT && subContext == "comment") {
            return sendContextArt(sticker, inputConnection, editorInfo, subContext)
        }

        val originalFile = File(sticker.filePath)
        if (!originalFile.exists() || originalFile.length() == 0L) {
            mainHandler.post {
                Toast.makeText(context, "Sticker asset not found: ${originalFile.name}", Toast.LENGTH_SHORT).show()
            }
            return DeliveryResult(false, DeliveryMode.FAILED, "Sticker file not found: ${sticker.filePath}")
        }

        val deliveryId = UUID.randomUUID().toString()
        val targetPackage = editorInfo?.packageName

        // Record usage immediately
        dbHelper.recordStickerUsage(sticker.id)

        // Read preferences
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val workerUrl = getEffectiveWorkerUrl(prefs)
        val allowTextArt = prefs.getBoolean("flutter.enable_text_art_fallback", false)
        val autoAdaptSms = prefs.getBoolean("flutter.auto_adapt_sms", prefs.getBoolean("auto_adapt_sms", true))

        // Ensure shareable cache file in cacheDir
        val cacheDir = File(context.cacheDir, "stickers").apply { if (!exists()) mkdirs() }
        val supportedMimesInTarget = if (editorInfo != null) EditorInfoCompat.getContentMimeTypes(editorInfo) else null
        val targetMime = findMatchingMimeType(supportedMimesInTarget, sticker.mimeType) ?: "image/png"

        val (shareFile, actualMime) = resolveShareFile(originalFile, targetMime, cacheDir)

        val contentUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        } catch (e: Exception) {
            mainHandler.post {
                Toast.makeText(context, "File provider error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return DeliveryResult(false, DeliveryMode.FAILED, "FileProvider error: ${e.message}")
        }

        // Grant URI read permission to target package
        if (!targetPackage.isNullOrEmpty() && targetPackage != context.packageName) {
            try {
                context.grantUriPermission(
                    targetPackage,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        // 1. PRIMARY: Try Direct Rich-Content Insertion (commitContent)
        var commitContentSuccess = false
        if (inputConnection != null && editorInfo != null) {
            try {
                val description = ClipDescription("Sticker", arrayOf(actualMime))
                val contentInfo = InputContentInfoCompat(contentUri, description, null)
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION

                commitContentSuccess = InputConnectionCompat.commitContent(
                    inputConnection,
                    editorInfo,
                    contentInfo,
                    flags,
                    null
                )
            } catch (_: Exception) {
                commitContentSuccess = false
            }
        }

        if (commitContentSuccess) {
            mainHandler.post {
                Toast.makeText(context, "Sticker sent!", Toast.LENGTH_SHORT).show()
            }
            dbHelper.logDelivery(
                id = deliveryId,
                stickerId = sticker.id,
                transport = "richContent",
                status = "sentToMessagingApp",
                targetPackage = targetPackage,
                details = "Directly inserted via commitContent ($actualMime)"
            )
            return DeliveryResult(true, DeliveryMode.RICH_CONTENT, "Directly inserted into app")
        }

        // 2. FALLBACK: Copy to System Clipboard for quick native paste
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, "Sticker", contentUri)
            clipboard.setPrimaryClip(clip)

            // Attempt automated paste into the active input connection
            inputConnection?.performContextMenuAction(android.R.id.paste)
        } catch (_: Exception) {}

        // If direct image insertion failed, automatically adapt to platform-calibrated Art!
        return sendContextArt(sticker, inputConnection, editorInfo, subContext)
    }

    fun sendContextArt(
        sticker: StickerEntity,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        subContext: String? = null,
        overrideProfile: ContextArtProfile? = null,
        forceAscii: Boolean = false,
        forceMemeDither: Boolean = true
    ): DeliveryResult {
        val originalFile = File(sticker.filePath)
        if (!originalFile.exists() || originalFile.length() == 0L) {
            mainHandler.post {
                Toast.makeText(context, "Sticker asset not found: ${originalFile.name}", Toast.LENGTH_SHORT).show()
            }
            return DeliveryResult(false, DeliveryMode.FAILED, "Sticker file not found")
        }

        val targetPackage = editorInfo?.packageName
        val profile = overrideProfile ?: PlatformProfiles.resolveProfile(targetPackage, subContext)
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val artStyle = prefs.getString("flutter.keyboard_art_style", "braille") ?: "braille"
        val useAscii = forceAscii || (artStyle.lowercase() == "ascii")

        // Theme Polarity: Always Dark Theme Art by default (Standard for Telegram & mobile chat bubbles)
        val themePref = prefs.getString("flutter.keyboard_art_theme", "dark") ?: "dark"
        val isDarkMode = themePref != "light"

        val artString = SmartContextArtAdapter.convertStickerToArt(
            imageFile = originalFile,
            profile = profile,
            useAsciiMode = useAscii,
            isDarkMode = isDarkMode,
            useMemeDither = forceMemeDither
        )
        if (artString.isEmpty()) {
            return DeliveryResult(false, DeliveryMode.FAILED, "Failed to render art")
        }

        if (inputConnection != null) {
            inputConnection.commitText(artString, 1)
            mainHandler.post {
                Toast.makeText(context, "Sent as ${profile.displayName}", Toast.LENGTH_SHORT).show()
            }
            dbHelper.recordStickerUsage(sticker.id)
            dbHelper.logDelivery(
                id = UUID.randomUUID().toString(),
                stickerId = sticker.id,
                transport = "contextArt",
                status = "sentToMessagingApp",
                targetPackage = targetPackage,
                details = "Art delivered for ${profile.id} (${artString.length} chars)"
            )
            return DeliveryResult(true, DeliveryMode.TEXT_ART_FALLBACK, "Delivered context art for ${profile.displayName}")
        }

        return DeliveryResult(false, DeliveryMode.FAILED, "No active input connection")
    }

    fun copyStickerToClipboard(
        sticker: StickerEntity,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): DeliveryResult {
        val originalFile = File(sticker.filePath)
        if (!originalFile.exists()) {
            return DeliveryResult(false, DeliveryMode.FAILED, "Sticker file not found")
        }
        val cacheDir = File(context.cacheDir, "stickers").apply { if (!exists()) mkdirs() }
        val (shareFile, _) = resolveShareFile(originalFile, "image/png", cacheDir)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Sticker", contentUri)
        clipboard.setPrimaryClip(clip)
        mainHandler.post {
            Toast.makeText(context, "Sticker copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        dbHelper.recordStickerUsage(sticker.id)
        return DeliveryResult(true, DeliveryMode.CLIPBOARD_FALLBACK, "Copied sticker to clipboard")
    }

    private fun resolveShareFile(originalFile: File, targetMime: String, cacheDir: File): Pair<File, String> {
        val requiresPng = targetMime.equals("image/png", ignoreCase = true)

        if (requiresPng || originalFile.extension.equals("webp", ignoreCase = true)) {
            // Check if PNG version exists or convert
            val pngFile = File(cacheDir, "${originalFile.nameWithoutExtension}_share.png")
            if (!pngFile.exists() || pngFile.length() == 0L) {
                try {
                    val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
                    if (bitmap != null) {
                        pngFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        bitmap.recycle()
                    }
                } catch (_: Exception) {}
            }

            if (pngFile.exists() && pngFile.length() > 0) {
                return Pair(pngFile, "image/png")
            }
        }

        // Fallback to copying original file directly to cache
        val copyFile = File(cacheDir, "share_${originalFile.name}")
        try {
            originalFile.copyTo(copyFile, overwrite = true)
        } catch (_: Exception) {}

        return Pair(if (copyFile.exists()) copyFile else originalFile, "image/webp")
    }

    private fun uploadAndPasteCloudflareLink(
        workerUrl: String,
        stickerFile: File,
        deliveryId: String,
        stickerId: String,
        targetPackage: String?,
        inputConnection: InputConnection
    ) {
        executor.execute {
            try {
                val cleanUrl = workerUrl.trimEnd('/')
                val uploadEndpoint = URL("$cleanUrl/api/stickers/upload")
                val connection = (uploadEndpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "image/png")
                }

                val bytes = stickerFile.readBytes()
                connection.outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                }

                if (connection.responseCode in 200..299) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val publicUrl = json.optString("url")

                    if (publicUrl.isNotEmpty()) {
                        mainHandler.post {
                            inputConnection.commitText(publicUrl, 1)
                            Toast.makeText(context, "Sticker link inserted into comment!", Toast.LENGTH_SHORT).show()
                        }

                        dbHelper.logDelivery(
                            id = deliveryId,
                            stickerId = stickerId,
                            transport = "cloudflareLink",
                            status = "sentToMessagingApp",
                            targetPackage = targetPackage,
                            details = "Uploaded to Cloudflare: $publicUrl"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun sendUniversalSms(
        sticker: StickerEntity,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): DeliveryResult {
        val originalFile = File(sticker.filePath)
        if (!originalFile.exists() || originalFile.length() == 0L) {
            mainHandler.post {
                Toast.makeText(context, "Sticker asset not found: ${originalFile.name}", Toast.LENGTH_SHORT).show()
            }
            return DeliveryResult(false, DeliveryMode.FAILED, "Sticker file not found: ${sticker.filePath}")
        }

        val deliveryId = UUID.randomUUID().toString()
        val targetPackage = editorInfo?.packageName
        dbHelper.recordStickerUsage(sticker.id)

        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val workerUrl = getEffectiveWorkerUrl(prefs)

        val pack = dbHelper.getPackById(sticker.packId)
        val packTitle = pack?.title?.takeIf { it.isNotEmpty() } ?: pack?.name?.takeIf { it.isNotEmpty() } ?: "Sticker"
        val emojiPrefix = ""

        val cacheDir = File(context.cacheDir, "stickers").apply { if (!exists()) mkdirs() }
        val (shareFile, _) = resolveShareFile(originalFile, "image/png", cacheDir)

        // Always put image in clipboard so user can paste if desired
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, "Sticker", contentUri)
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}

        val hasConfiguredWorker = workerUrl.isNotEmpty()

        if (hasConfiguredWorker && inputConnection != null) {
            mainHandler.post {
                Toast.makeText(context, "Universal SMS: Preparing link...", Toast.LENGTH_SHORT).show()
            }
            uploadAndPasteUniversalSms(
                workerUrl = workerUrl,
                stickerFile = shareFile,
                deliveryId = deliveryId,
                stickerId = sticker.id,
                targetPackage = targetPackage,
                inputConnection = inputConnection,
                emojiPrefix = emojiPrefix,
                packTitle = packTitle
            )
            return DeliveryResult(true, DeliveryMode.CLOUDFLARE_LINK, "Sent via Universal SMS with Cloudflare link")
        } else if (inputConnection != null) {
            val textCommit = "$emojiPrefix[Sticker: $packTitle] "
            inputConnection.commitText(textCommit, 1)
            mainHandler.post {
                Toast.makeText(context, "Sticker inserted for SMS (Universal fallback)", Toast.LENGTH_SHORT).show()
            }
            dbHelper.logDelivery(
                id = deliveryId,
                stickerId = sticker.id,
                transport = "smsCodec",
                status = "sentToMessagingApp",
                targetPackage = targetPackage,
                details = "Committed universal text format ($textCommit)"
            )
            return DeliveryResult(true, DeliveryMode.TEXT_ART_FALLBACK, "Inserted universal text")
        }

        return DeliveryResult(false, DeliveryMode.FAILED, "No active input connection")
    }

    private fun uploadAndPasteUniversalSms(
        workerUrl: String,
        stickerFile: File,
        deliveryId: String,
        stickerId: String,
        targetPackage: String?,
        inputConnection: InputConnection,
        emojiPrefix: String,
        packTitle: String
    ) {
        executor.execute {
            try {
                val cleanUrl = workerUrl.trimEnd('/')
                val uploadEndpoint = URL("$cleanUrl/api/stickers/upload")
                val connection = (uploadEndpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "image/png")
                }

                val bytes = stickerFile.readBytes()
                connection.outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                }

                if (connection.responseCode in 200..299) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val publicUrl = json.optString("url")

                    if (publicUrl.isNotEmpty()) {
                        val fullMessage = "$publicUrl\n$emojiPrefix[Sticker: $packTitle]"
                        mainHandler.post {
                            inputConnection.commitText(fullMessage, 1)
                            Toast.makeText(context, "Universal SMS ready! Works on all phones.", Toast.LENGTH_SHORT).show()
                        }

                        dbHelper.logDelivery(
                            id = deliveryId,
                            stickerId = stickerId,
                            transport = "cloudflareLink",
                            status = "sentToMessagingApp",
                            targetPackage = targetPackage,
                            details = "Universal SMS uploaded: $fullMessage"
                        )
                    }
                } else {
                    mainHandler.post {
                        inputConnection.commitText("$emojiPrefix[Sticker: $packTitle] ", 1)
                        Toast.makeText(context, "Sticker inserted as universal text", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    inputConnection.commitText("$emojiPrefix[Sticker: $packTitle] ", 1)
                    Toast.makeText(context, "Sticker inserted as universal text", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun generateTextArtSilhouette(file: File, width: Int = 24, height: Int = 12): String {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val palette = "@%#*+=-:. "
            val builder = StringBuilder()

            for (y in 0 until height) {
                var lineHasContent = false
                val lineSb = StringBuilder()
                for (x in 0 until width) {
                    val pixel = scaled.getPixel(x, y)
                    val alpha = (pixel shr 24) and 0xff
                    if (alpha < 60) {
                        lineSb.append(" ")
                        continue
                    }
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    val idx = (lum * (palette.length - 1)).toInt().coerceIn(0, palette.length - 1)
                    lineSb.append(palette[idx])
                    lineHasContent = true
                }
                if (lineHasContent) {
                    builder.append(lineSb.toString().trimEnd()).append("\n")
                }
            }
            scaled.recycle()
            bitmap.recycle()
            builder.toString().trimEnd()
        } catch (_: Exception) {
            ""
        }
    }

    private fun findMatchingMimeType(
        supportedInTarget: Array<String>?,
        stickerMime: String
    ): String? {
        if (supportedInTarget.isNullOrEmpty()) {
            // Default to PNG which is universal across Android apps
            return "image/png"
        }

        // Exact match
        for (supported in supportedInTarget) {
            if (supported.equals(stickerMime, ignoreCase = true)) {
                return supported
            }
        }

        // Standard image format fallbacks (PNG/JPEG)
        for (supported in supportedInTarget) {
            if (supported.equals("image/png", ignoreCase = true)) {
                return "image/png"
            }
            if (supported.equals("image/*", ignoreCase = true)) {
                return "image/png"
            }
            if (supported.equals("image/webp", ignoreCase = true)) {
                return "image/webp"
            }
        }

        return "image/png"
    }
}

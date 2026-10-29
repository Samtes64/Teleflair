package com.teleboard.teleboard.keyboard.context

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance native Kotlin art generation engine.
 * Faithfully ported and enhanced from the production ASCII & Braille Art Generator.
 * Features:
 * - Content Bounding Box Auto-Cropping (eliminates empty space padding & distortion)
 * - Sauvola Integral Local Adaptive Thresholding (O(W*H) runtime via 2D summed-area tables)
 * - Floyd-Steinberg Error Diffusion Dithering for photorealistic meme & portrait clarity
 * - 3x3 Sobel Contour Extraction with 2px outer boundary noise suppression
 * - Blank line trimming (guarantees zero empty new lines on top/bottom)
 * - Space-locking using Unicode Braille Blank U+2800
 */
object SmartContextArtAdapter {

    // Unicode Braille base code point
    private const val BRAILLE_BASE = 0x2800

    // Unicode Braille pattern blank (U+2800) - visual blank space immune to carrier/app collapsing
    const val BRAILLE_BLANK = '\u2800'

    // Classic Monospace ASCII palette ordered from lightest to densest for Dark Mode
    private const val ASCII_PALETTE = " .:-=+*#%@"

    /**
     * Converts an image file into platform-calibrated art string.
     */
    fun convertStickerToArt(
        imageFile: File,
        profile: ContextArtProfile,
        useAsciiMode: Boolean = false,
        isDarkMode: Boolean = true,
        useMemeDither: Boolean = true
    ): String {
        if (!imageFile.exists() || imageFile.length() == 0L) return ""

        val originalBitmap = try {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } catch (_: Exception) {
            null
        } ?: return ""

        val artString = if (useAsciiMode) {
            generateAsciiArt(originalBitmap, profile, isDarkMode)
        } else {
            generateBrailleArt(originalBitmap, profile, isDarkMode, useMemeDither)
        }

        originalBitmap.recycle()
        return artString
    }

    /**
     * Automatically crops transparent outer padding around sticker graphics.
     * Ensures 100% of the target Braille columns are allocated to the actual artwork,
     * maximizing detail for meme expressions and eliminating empty blank lines.
     */
    fun cropTransparentBorders(bitmap: Bitmap, alphaThreshold: Int = 20): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOff + x] ushr 24) and 0xff
                if (alpha > alphaThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // If completely blank or already tight, return original
        if (maxX < minX || maxY < minY) {
            return bitmap
        }

        // Add 2-pixel safety margin within bounds
        minX = max(0, minX - 2)
        minY = max(0, minY - 2)
        maxX = min(w - 1, maxX + 2)
        maxY = min(h - 1, maxY + 2)

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1

        if (cropW == w && cropH == h) {
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
    }

    /**
     * Generates high-density Unicode 2x4 Braille Art with Sobel edge contours,
     * content auto-cropping, and either Sauvola adaptive or Floyd-Steinberg meme dithering.
     */
    fun generateBrailleArt(
        bitmap: Bitmap,
        profile: ContextArtProfile,
        isDarkMode: Boolean = true,
        useMemeDither: Boolean = true
    ): String {
        // Crop transparent margins so the actual artwork gets 100% resolution
        val cropped = cropTransparentBorders(bitmap)
        val targetCols = profile.targetColumns
        val aspect = profile.aspectRatioCompensation
        val origW = cropped.width.toFloat()
        val origH = cropped.height.toFloat()

        // Exact aspect ratio and column calculation matching web generator
        val charRows = max(2, ((origH / origW) * targetCols * aspect).roundToInt())
        val dotsW = targetCols * 2
        val dotsH = charRows * 4

        val scaled = Bitmap.createScaledBitmap(cropped, dotsW, dotsH, true)
        val pixels = IntArray(dotsW * dotsH)
        scaled.getPixels(pixels, 0, dotsW, 0, 0, dotsW, dotsH)
        scaled.recycle()
        if (cropped != bitmap) {
            cropped.recycle()
        }

        return generateBrailleArtFromPixels(pixels, dotsW, dotsH, profile, isDarkMode, useMemeDither)
    }

    /**
     * Pure algorithmic Braille Art generation from raw ARGB pixel array.
     * Operates without Android framework dependencies for maximum testability and speed.
     */
    fun generateBrailleArtFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: ContextArtProfile,
        isDarkMode: Boolean = true,
        useMemeDither: Boolean = true
    ): String {
        val charCols = width / 2
        val charRows = height / 4

        // 1. Contrast & Channel Adjustment
        val contrastFactor = computeContrastFactor(profile.contrast)
        val brightness = profile.brightness
        val lumBuffer = FloatArray(width * height)
        val alphaMask = IntArray(width * height)

        // Theme Polarity:
        // Dark theme: Braille dots are rendered with bright text color on dark background (effectiveInvert = false)
        // Light theme: Braille dots are rendered with dark text color on white background (effectiveInvert = true)
        val effectiveInvert = !isDarkMode

        var minLum = 255f
        var maxLum = 0f
        var validPixelCount = 0

        for (i in 0 until (width * height)) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xff
            alphaMask[i] = a

            if (a < profile.bgSuppressionThreshold) {
                lumBuffer[i] = if (effectiveInvert) 255f else 0f
            } else {
                val r = adjustChannel((pixel shr 16) and 0xff, contrastFactor, brightness)
                val g = adjustChannel((pixel shr 8) and 0xff, contrastFactor, brightness)
                val b = adjustChannel(pixel and 0xff, contrastFactor, brightness)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                lumBuffer[i] = lum
                if (lum < minLum) minLum = lum
                if (lum > maxLum) maxLum = lum
                validPixelCount++
            }
        }

        // 2. Dynamic Range Autocontrast Stretching for Foreground Subject
        if (validPixelCount > 0 && maxLum > minLum + 12f) {
            val range = maxLum - minLum
            for (i in 0 until (width * height)) {
                if (alphaMask[i] >= profile.bgSuppressionThreshold) {
                    val stretched = ((lumBuffer[i] - minLum) / range) * 255f
                    lumBuffer[i] = stretched.coerceIn(0f, 255f)
                }
            }
        }

        // 3. Sobel 3x3 Edge & Contour Detection (with 2px border artifact suppression)
        val edges = computeSobelEdges(lumBuffer, width, height, profile.edgeStrength)

        // 4. Binary Dot Decision Matrix
        val dotMatrix = if (useMemeDither) {
            // Ultra-Clarity Meme Mode: Otsu Optimal Threshold + Floyd-Steinberg Error Diffusion Dithering
            val otsuThreshold = computeOtsuThreshold(lumBuffer, width * height)
            computeFloydSteinbergDither(
                lumBuffer = lumBuffer,
                w = width,
                h = height,
                threshold = otsuThreshold,
                edges = edges,
                effectiveInvert = effectiveInvert,
                alphaMask = alphaMask,
                bgAlphaThreshold = profile.bgSuppressionThreshold
            )
        } else {
            // High-Contrast Graphic Mode: Sauvola Local Adaptive Thresholding with Flat Denoise
            computeAdaptiveSauvola(
                lumBuffer = lumBuffer,
                w = width,
                h = height,
                radius = 3,
                kFactor = 0.20f,
                bgSuppress = profile.bgSuppressPercent,
                edges = edges,
                effectiveInvert = effectiveInvert,
                alphaMask = alphaMask,
                bgAlphaThreshold = profile.bgSuppressionThreshold
            )
        }

        // 4. Map 2x4 dot cells to Unicode Braille codepoints
        val lines = ArrayList<String>(charRows)

        for (by in 0 until charRows) {
            val sb = StringBuilder(charCols)
            val startY = by * 4

            for (bx in 0 until charCols) {
                val startX = bx * 2
                var mask = 0

                // Standard Unicode 2x4 Braille Dot Matrix:
                // Dot 1: (0, 0) -> bit 0 (0x01)
                if (isDotActive(dotMatrix, width, height, startX, startY)) mask = mask or 0x01
                // Dot 2: (0, 1) -> bit 1 (0x02)
                if (isDotActive(dotMatrix, width, height, startX, startY + 1)) mask = mask or 0x02
                // Dot 3: (0, 2) -> bit 2 (0x04)
                if (isDotActive(dotMatrix, width, height, startX, startY + 2)) mask = mask or 0x04
                // Dot 4: (1, 0) -> bit 3 (0x08)
                if (isDotActive(dotMatrix, width, height, startX + 1, startY)) mask = mask or 0x08
                // Dot 5: (1, 1) -> bit 4 (0x10)
                if (isDotActive(dotMatrix, width, height, startX + 1, startY + 1)) mask = mask or 0x10
                // Dot 6: (1, 2) -> bit 5 (0x20)
                if (isDotActive(dotMatrix, width, height, startX + 1, startY + 2)) mask = mask or 0x20
                // Dot 7: (0, 3) -> bit 6 (0x40)
                if (isDotActive(dotMatrix, width, height, startX, startY + 3)) mask = mask or 0x40
                // Dot 8: (1, 3) -> bit 7 (0x80)
                if (isDotActive(dotMatrix, width, height, startX + 1, startY + 3)) mask = mask or 0x80

                if (mask == 0) {
                    // Blank dot cell: U+2800 prevents space collapsing across carrier SMS and social apps
                    if (profile.lockBrailleBlank) {
                        sb.append(BRAILLE_BLANK)
                    } else {
                        sb.append(' ')
                    }
                } else {
                    sb.append((BRAILLE_BASE + mask).toChar())
                }
            }
            lines.add(sb.toString())
        }

        // 5. Enforce platform character safety limits and strip blank line padding
        var formattedArt = assembleLines(lines, profile.wrapInCodeBlock)
        while (formattedArt.length > profile.maxCharLimit && lines.size > 2) {
            lines.removeAt(lines.lastIndex)
            formattedArt = assembleLines(lines, profile.wrapInCodeBlock)
        }

        return formattedArt
    }

    /**
     * Generates Classic Monospace ASCII art from Bitmap.
     */
    fun generateAsciiArt(bitmap: Bitmap, profile: ContextArtProfile, isDarkMode: Boolean = true): String {
        val cropped = cropTransparentBorders(bitmap)
        val targetCols = profile.targetColumns
        val aspect = profile.aspectRatioCompensation
        val origW = cropped.width.toFloat()
        val origH = cropped.height.toFloat()

        val rows = max(2, ((origH / origW) * targetCols * aspect).roundToInt())

        val scaled = Bitmap.createScaledBitmap(cropped, targetCols, rows, true)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, targetCols, 0, 0, targetCols, rows)
        scaled.recycle()
        if (cropped != bitmap) {
            cropped.recycle()
        }

        return generateAsciiArtFromPixels(pixels, targetCols, rows, profile, isDarkMode)
    }

    /**
     * Pure algorithmic ASCII Art generation from raw ARGB pixel array.
     */
    fun generateAsciiArtFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        profile: ContextArtProfile,
        isDarkMode: Boolean = true
    ): String {
        val lines = ArrayList<String>(height)
        val paletteLen = ASCII_PALETTE.length
        val contrastFactor = computeContrastFactor(profile.contrast)
        val brightness = profile.brightness
        val effectiveInvert = !isDarkMode

        for (y in 0 until height) {
            val sb = StringBuilder(width)
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val alpha = (pixel shr 24) and 0xff
                if (alpha < profile.bgSuppressionThreshold) {
                    sb.append(if (profile.lockBrailleBlank) BRAILLE_BLANK else ' ')
                    continue
                }

                val r = adjustChannel((pixel shr 16) and 0xff, contrastFactor, brightness)
                val g = adjustChannel((pixel shr 8) and 0xff, contrastFactor, brightness)
                val b = adjustChannel(pixel and 0xff, contrastFactor, brightness)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                var normLum = (lum / 255.0f).coerceIn(0f, 1f)
                if (effectiveInvert) normLum = 1.0f - normLum

                val idx = (normLum * (paletteLen - 1)).roundToInt().coerceIn(0, paletteLen - 1)
                val ch = ASCII_PALETTE[idx]
                sb.append(if (ch == ' ' && profile.lockBrailleBlank) BRAILLE_BLANK else ch)
            }
            lines.add(sb.toString())
        }

        return assembleLines(lines, profile.wrapInCodeBlock)
    }

    fun computeContrastFactor(contrast: Float): Float {
        return (259f * (contrast + 255f)) / (255f * (259f - contrast))
    }

    fun adjustChannel(valByte: Int, factor: Float, brightness: Float): Float {
        val adjusted = factor * (valByte - 128f) + 128f + brightness
        return adjusted.coerceIn(0f, 255f)
    }

    /**
     * Sobel 3x3 Edge & Contour Detection with 2-pixel border suppression
     * to eliminate artificial box outlines around the sticker asset.
     */
    fun computeSobelEdges(lumBuffer: FloatArray, w: Int, h: Int, strength: Float): ByteArray {
        val edgeBuffer = ByteArray(w * h)
        if (strength <= 0f) return edgeBuffer

        val threshold = max(28f, (100f - strength) * 3.2f + 25f)

        for (y in 2 until (h - 2)) {
            for (x in 2 until (w - 2)) {
                val p00 = lumBuffer[(y - 1) * w + (x - 1)]
                val p01 = lumBuffer[(y - 1) * w + x]
                val p02 = lumBuffer[(y - 1) * w + (x + 1)]
                val p10 = lumBuffer[y * w + (x - 1)]
                val p12 = lumBuffer[y * w + (x + 1)]
                val p20 = lumBuffer[(y + 1) * w + (x - 1)]
                val p21 = lumBuffer[(y + 1) * w + x]
                val p22 = lumBuffer[(y + 1) * w + (x + 1)]

                val gx = -p00 + p02 - 2f * p10 + 2f * p12 - p20 + p22
                val gy = -p00 - 2f * p01 - p02 + p20 + 2f * p21 + p22
                val mag = sqrt(gx * gx + gy * gy)

                if (mag > threshold) {
                    edgeBuffer[y * w + x] = 1
                }
            }
        }
        return edgeBuffer
    }

    /**
     * Integral Image Adaptive Local Thresholding (Sauvola & Bradley with Flat Denoise).
     * Computes local mean and standard deviation in O(W*H) time using 2D summed-area tables.
     */
    fun computeAdaptiveSauvola(
        lumBuffer: FloatArray,
        w: Int,
        h: Int,
        radius: Int = 3,
        kFactor: Float = 0.20f,
        bgSuppress: Float = 48f,
        edges: ByteArray?,
        effectiveInvert: Boolean,
        alphaMask: IntArray?,
        bgAlphaThreshold: Int = 24
    ): BooleanArray {
        val stride = w + 1
        val I = DoubleArray((w + 1) * (h + 1))
        val I2 = DoubleArray((w + 1) * (h + 1))

        for (y in 0 until h) {
            var rowSum = 0.0
            var rowSumSq = 0.0
            val yOff = (y + 1) * stride
            val prevYOff = y * stride
            val lumOff = y * w

            for (x in 0 until w) {
                val v = lumBuffer[lumOff + x].toDouble()
                rowSum += v
                rowSumSq += v * v
                I[yOff + x + 1] = I[prevYOff + x + 1] + rowSum
                I2[yOff + x + 1] = I2[prevYOff + x + 1] + rowSumSq
            }
        }

        val dotActive = BooleanArray(w * h)
        val R = max(2, radius)
        val minStd = (bgSuppress / 100.0) * 20.0

        for (y in 0 until h) {
            val y0 = max(0, y - R)
            val y1 = min(h - 1, y + R)

            for (x in 0 until w) {
                val idx = y * w + x

                if (alphaMask != null && alphaMask[idx] < bgAlphaThreshold) {
                    dotActive[idx] = false
                    continue
                }

                if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2) {
                    dotActive[idx] = false
                    continue
                }

                if (edges != null && edges[idx].toInt() == 1) {
                    dotActive[idx] = true
                    continue
                }

                val x0 = max(0, x - R)
                val x1 = min(w - 1, x + R)
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)

                val sum = I[(y1 + 1) * stride + (x1 + 1)] -
                          I[(y1 + 1) * stride + x0] -
                          I[y0 * stride + (x1 + 1)] +
                          I[y0 * stride + x0]

                val sumSq = I2[(y1 + 1) * stride + (x1 + 1)] -
                            I2[(y1 + 1) * stride + x0] -
                            I2[y0 * stride + (x1 + 1)] +
                            I2[y0 * stride + x0]

                val mean = sum / count
                val variance = max(0.0, (sumSq / count) - (mean * mean))
                val std = sqrt(variance)

                if (std < minStd) {
                    dotActive[idx] = false
                    continue
                }

                val T = mean * (1.0 + kFactor * (std / 128.0 - 1.0))
                val pix = lumBuffer[idx]

                dotActive[idx] = if (!effectiveInvert) {
                    pix >= T
                } else {
                    pix < T
                }
            }
        }

        return dotActive
    }

    /**
     * High-Precision Floyd-Steinberg Error Diffusion Dithering with Sobel Contour Locking.
     * Produces photorealistic shading gradients and fine detail for memes, portraits, and photos.
     */
    fun computeFloydSteinbergDither(
        lumBuffer: FloatArray,
        w: Int,
        h: Int,
        threshold: Int,
        edges: ByteArray?,
        effectiveInvert: Boolean,
        alphaMask: IntArray?,
        bgAlphaThreshold: Int = 24
    ): BooleanArray {
        val dotActive = BooleanArray(w * h)
        val errors = FloatArray(w * h) { lumBuffer[it] }

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val idx = rowOff + x

                // Suppress transparent background
                if (alphaMask != null && alphaMask[idx] < bgAlphaThreshold) {
                    dotActive[idx] = false
                    continue
                }

                // Suppress outer 2px boundary
                if (x <= 1 || x >= w - 2 || y <= 1 || y >= h - 2) {
                    dotActive[idx] = false
                    continue
                }

                val oldVal = errors[idx]
                val isEdge = (edges != null && edges[idx].toInt() == 1)

                // High-fidelity contour locking:
                // Edge pixels (pupils, mouth lines, silhouette) use ground-truth luminance
                // to prevent neighboring error diffusion from blurring sharp facial features.
                val valToCompare = if (isEdge) lumBuffer[idx] else oldVal
                val active = if (!effectiveInvert) valToCompare >= threshold else valToCompare < threshold
                dotActive[idx] = active

                val newVal = if (active) (if (!effectiveInvert) 255f else 0f) else (if (!effectiveInvert) 0f else 255f)
                val err = oldVal - newVal

                // Distribute error to neighboring pixels (Floyd-Steinberg kernel)
                if (x + 1 < w) errors[idx + 1] += err * (7f / 16f)
                if (x - 1 >= 0 && y + 1 < h) errors[idx + w - 1] += err * (3f / 16f)
                if (y + 1 < h) errors[idx + w] += err * (5f / 16f)
                if (x + 1 < w && y + 1 < h) errors[idx + w + 1] += err * (1f / 16f)
            }
        }

        return dotActive
    }

    /**
     * Otsu's Automatic Optimal Thresholding for high-contrast binarization.
     */
    fun computeOtsuThreshold(lumBuffer: FloatArray, length: Int): Int {
        val hist = IntArray(256)
        var total = 0
        for (i in 0 until length) {
            val v = lumBuffer[i].roundToInt().coerceIn(0, 255)
            hist[v]++
            total++
        }
        if (total == 0) return 128

        var sum = 0.0
        for (t in 0 until 256) sum += t * hist[t]

        var sumB = 0.0
        var wB = 0
        var varMax = 0.0
        var optimal = 128

        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                optimal = t
            }
        }
        return optimal
    }

    private fun isDotActive(matrix: BooleanArray, w: Int, h: Int, x: Int, y: Int): Boolean {
        if (x < 0 || x >= w || y < 0 || y >= h) return false
        return matrix[y * w + x]
    }

    private fun assembleLines(lines: MutableList<String>, wrapInCodeBlock: Boolean): String {
        // Strip completely empty or blank lines from the top
        while (lines.isNotEmpty() && isBlankLine(lines.first())) {
            lines.removeAt(0)
        }
        // Strip completely empty or blank lines from the bottom
        while (lines.isNotEmpty() && isBlankLine(lines.last())) {
            lines.removeAt(lines.lastIndex)
        }

        val body = lines.joinToString("\n")
        return if (wrapInCodeBlock) {
            "```\n$body\n```"
        } else {
            body
        }
    }

    private fun isBlankLine(line: String): Boolean {
        return line.all { it == ' ' || it == BRAILLE_BLANK || it == '\t' || it == '\r' }
    }
}

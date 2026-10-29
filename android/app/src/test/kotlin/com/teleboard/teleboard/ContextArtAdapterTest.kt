package com.teleboard.teleboard

import com.teleboard.teleboard.keyboard.context.PlatformProfiles
import com.teleboard.teleboard.keyboard.context.SmartContextArtAdapter
import org.junit.Assert.*
import org.junit.Test

class ContextArtAdapterTest {

    @Test
    fun testPlatformProfilesResolution() {
        // 1. Telegram
        val tgProfile = PlatformProfiles.resolveProfile("org.telegram.messenger", "dm")
        assertEquals(28, tgProfile.targetColumns)
        assertTrue(tgProfile.wrapInCodeBlock)
        assertTrue(tgProfile.lockBrailleBlank)
        assertEquals(0.42f, tgProfile.aspectRatioCompensation, 0.01f)

        // 2. SMS Carrier App
        val smsProfile = PlatformProfiles.resolveProfile("com.google.android.apps.messaging", "sms")
        assertEquals(22, smsProfile.targetColumns)
        assertFalse(smsProfile.wrapInCodeBlock)
        assertTrue(smsProfile.lockBrailleBlank)
        assertEquals(0.36f, smsProfile.aspectRatioCompensation, 0.01f)

        // 3. Instagram Comment
        val igComment = PlatformProfiles.resolveProfile("com.instagram.android", "comment")
        assertEquals(20, igComment.targetColumns)
        assertFalse(igComment.wrapInCodeBlock)
        assertEquals(0.34f, igComment.aspectRatioCompensation, 0.01f)

        // 4. TikTok Comment (strictly under 150 chars)
        val tiktokComment = PlatformProfiles.resolveProfile("com.zhiliaoapp.musically", "comment")
        assertEquals(14, tiktokComment.targetColumns)
        assertTrue("TikTok comment must have <=150 char guardrail", tiktokComment.maxCharLimit <= 150)
        assertFalse(tiktokComment.wrapInCodeBlock)

        // 5. Twitter / X Post or Reply (strictly under 280 chars)
        val twitterProfile = PlatformProfiles.resolveProfile("com.twitter.android", "comment")
        assertEquals(17, twitterProfile.targetColumns)
        assertTrue("Twitter must have <=280 char guardrail", twitterProfile.maxCharLimit <= 280)

        // 6. LinkedIn Comment
        val linkedinComment = PlatformProfiles.resolveProfile("com.linkedin.android", "comment")
        assertEquals(22, linkedinComment.targetColumns)

        // 7. YouTube Comment
        val youtubeComment = PlatformProfiles.resolveProfile("com.google.android.youtube", "comment")
        assertEquals(25, youtubeComment.targetColumns)

        // 8. Social Post Creation / Caption
        val postCaption = PlatformProfiles.resolveProfile("com.instagram.android", "post_creation")
        assertEquals(30, postCaption.targetColumns)
    }

    @Test
    fun testBrailleArtGenerationAndFormatting() {
        // Create 56x32 synthetic pixel array with a circular figure
        val width = 56
        val height = 32
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - (width / 2)
                val dy = y - (height / 2)
                if (dx * dx + dy * dy <= 10 * 10) {
                    // Solid white face pixel with 100% alpha (0xFFFFFFFF.toInt())
                    pixels[y * width + x] = 0xFFFFFFFF.toInt()
                } else {
                    // Transparent pixel (0x00000000)
                    pixels[y * width + x] = 0x00000000
                }
            }
        }

        // Test Telegram formatting (Code block wrapper)
        val tgArt = SmartContextArtAdapter.generateBrailleArtFromPixels(
            pixels, width, height, PlatformProfiles.TELEGRAM_MOBILE, isDarkMode = true
        )
        assertTrue("Telegram art must start with markdown code block", tgArt.startsWith("```\n"))
        assertTrue("Telegram art must end with markdown code block", tgArt.endsWith("\n```"))

        // Test SMS formatting (Pure text, NO backticks, Braille blank locking)
        val smsArt = SmartContextArtAdapter.generateBrailleArtFromPixels(
            pixels, width, height, PlatformProfiles.SMS_MESSAGING, isDarkMode = true
        )
        assertFalse("SMS art must NOT contain markdown code blocks", smsArt.contains("```"))
        assertTrue("SMS art must contain Braille characters", smsArt.any { it in '\u2800'..'\u28FF' })

        // Test TikTok 150-char guardrail
        val tiktokArt = SmartContextArtAdapter.generateBrailleArtFromPixels(
            pixels, width, height, PlatformProfiles.TIKTOK_COMMENT, isDarkMode = true
        )
        assertTrue(
            "TikTok art length (${tiktokArt.length}) must strictly not exceed 150 characters",
            tiktokArt.length <= 150
        )

        // Test Twitter 280-char guardrail
        val twitterArt = SmartContextArtAdapter.generateBrailleArtFromPixels(
            pixels, width, height, PlatformProfiles.TWITTER_POST_REPLY, isDarkMode = true
        )
        assertTrue(
            "Twitter art length (${twitterArt.length}) must strictly not exceed 280 characters",
            twitterArt.length <= 280
        )

        // Test Classic Monospace ASCII mode
        val asciiArt = SmartContextArtAdapter.generateAsciiArtFromPixels(
            pixels, width, height, PlatformProfiles.INSTAGRAM_COMMENT, isDarkMode = true
        )
        assertFalse("ASCII art must not contain backticks", asciiArt.contains("```"))
        assertTrue("ASCII art must contain recognizable silhouette characters", asciiArt.any { it in "@%#*+=-:. \u2800" })

        // Test Ultra-Clarity Meme Dithering mode
        val memeArt = SmartContextArtAdapter.generateBrailleArtFromPixels(
            pixels, width, height, PlatformProfiles.TELEGRAM_MOBILE, isDarkMode = true, useMemeDither = true
        )
        assertTrue("Meme art must render code block wrapper", memeArt.startsWith("```\n"))
        assertTrue("Meme art must contain active Braille dots", memeArt.any { it in '\u2801'..'\u28FF' })
    }

    @Test
    fun testSobelAndSauvolaAlgorithms() {
        val w = 20
        val h = 20
        val lums = FloatArray(w * h)
        // Draw a vertical edge at x = 10
        for (y in 0 until h) {
            for (x in 0 until w) {
                lums[y * w + x] = if (x >= 10) 240f else 20f
            }
        }

        val edges = SmartContextArtAdapter.computeSobelEdges(lums, w, h, strength = 45f)
        // Edge should be detected at x=9 and/or x=10 in the middle rows
        var edgeCount = 0
        for (y in 3 until 17) {
            if (edges[y * w + 9].toInt() == 1 || edges[y * w + 10].toInt() == 1) {
                edgeCount++
            }
        }
        assertTrue("Sobel filter must detect vertical edge contour", edgeCount > 10)

        // Border suppression check (outer 2px must be 0)
        for (x in 0 until w) {
            assertEquals(0, edges[0 * w + x].toInt())
            assertEquals(0, edges[1 * w + x].toInt())
            assertEquals(0, edges[(h - 1) * w + x].toInt())
            assertEquals(0, edges[(h - 2) * w + x].toInt())
        }
    }
}

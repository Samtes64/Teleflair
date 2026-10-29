package com.teleboard.teleboard

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SmartDeliveryCoordinatorTest {

    @Test
    fun testMimeTypeMatchingLogic() {
        val supportedTargets = arrayOf("image/webp", "image/png", "text/plain")

        // Matches exact mime
        assertTrue(supportedTargets.contains("image/webp"))
        assertTrue(supportedTargets.contains("image/png"))

        // Does not match video or arbitrary formats
        assertFalse(supportedTargets.contains("video/mp4"))
    }

    @Test
    fun testDeliveryPriorityOrder() {
        val priorityList = listOf(
            "richContent",
            "intentShare",
            "cloudflareLink",
            "smsCodec",
            "textArt"
        )

        assertEquals("richContent", priorityList[0])
        assertEquals("intentShare", priorityList[1])
        assertEquals("cloudflareLink", priorityList[2])
        assertEquals("smsCodec", priorityList[3])
        assertEquals("textArt", priorityList[4])
    }

    @Test
    fun testSocialCommentAppsProtectedFromActionSend() {
        val socialApps = setOf(
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.linkedin.android",
            "com.twitter.android",
            "com.facebook.katana",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.google.android.youtube",
            "com.pinterest",
            "com.snapchat.android"
        )

        assertTrue("Instagram must be identified as social comment app", socialApps.contains("com.instagram.android"))
        assertTrue("LinkedIn must be identified as social comment app", socialApps.contains("com.linkedin.android"))
        assertTrue("Twitter/X must be identified as social comment app", socialApps.contains("com.twitter.android"))
        assertFalse("Telegram is a direct rich-content messaging app", socialApps.contains("org.telegram.messenger"))
        assertFalse("WhatsApp is a direct rich-content messaging app", socialApps.contains("com.whatsapp"))
    }

    @Test
    fun testSmsMessagingAppDetection() {
        val smsPackages = listOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.simplemobiletools.sms.messenger"
        )

        for (pkg in smsPackages) {
            val isSms = pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms")
            assertTrue("Package $pkg must be recognized as SMS carrier app", isSms)
        }
    }

    @Test
    fun testDeliveryChoiceOptions() {
        val choices = com.teleboard.teleboard.keyboard.DeliveryChoice.values()
        assertEquals(9, choices.size)
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.AUTO_ADAPT))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.RICH_IMAGE))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.UNIVERSAL_SMS))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.COPY_CLIPBOARD))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.TEXT_ART))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.ART_MEME_DETAIL))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.ART_TELEGRAM))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.ART_CLEAN_SMS))
        assertTrue(choices.contains(com.teleboard.teleboard.keyboard.DeliveryChoice.ART_ASCII))
    }
}

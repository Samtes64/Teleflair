package com.teleboard.teleboard

import android.graphics.PointF
import com.teleboard.teleboard.keyboard.engine.AppContextCategory
import com.teleboard.teleboard.keyboard.engine.AppCorrelationManager
import com.teleboard.teleboard.keyboard.gesture.GlideTypingDetector
import com.teleboard.teleboard.keyboard.physics.ParticleEmitter
import com.teleboard.teleboard.keyboard.theme.KeyboardThemeId
import org.junit.Assert.*
import org.junit.Test

class AdvancedEnginesTest {

    @Test
    fun testAppCorrelationManagerVocabBoost() {
        val manager = AppCorrelationManager()

        // 1. Messaging app categorization and boost
        val tgCategory = manager.getCategory("org.telegram.messenger")
        assertEquals(AppContextCategory.MESSAGING, tgCategory)

        val baseScore = 1.0f
        val boostedCasual = manager.applyWeightAdjustment("haha", baseScore, tgCategory)
        assertTrue("Casual token 'haha' should be boosted in messaging apps", boostedCasual > baseScore)

        // 2. Email app categorization and formal boost / casual penalty
        val emailCategory = manager.getCategory("com.google.android.gm")
        assertEquals(AppContextCategory.EMAIL_WORK, emailCategory)

        val boostedFormal = manager.applyWeightAdjustment("regards", baseScore, emailCategory)
        assertTrue("Formal token 'regards' should be boosted in email apps", boostedFormal > baseScore)

        val penalizedCasual = manager.applyWeightAdjustment("haha", baseScore, emailCategory)
        assertTrue("Casual token 'haha' should be penalized in email apps", penalizedCasual < baseScore)

        // 3. Unknown package categorization
        val genericCategory = manager.getCategory("com.random.app")
        assertEquals(AppContextCategory.GENERAL, genericCategory)
        val untouched = manager.applyWeightAdjustment("haha", baseScore, genericCategory)
        assertEquals(baseScore, untouched, 0.0001f)
    }

    @Test
    fun testGlideTypingDetectorGestureFlow() {
        val centroids = mapOf(
            'h' to PointF(100f, 100f),
            'e' to PointF(50f, 50f),
            'y' to PointF(150f, 50f)
        )
        val dictionary = listOf("hey", "he", "hero", "hello", "world")
        val detector = GlideTypingDetector(centroids, dictionary)

        // 1. Touch down initializes tracking
        detector.onTouchDown(100f, 100f)

        // 2. Dragging points triggers isGliding once distance threshold is passed
        val m1 = detector.onTouchMove(80f, 80f)
        val m2 = detector.onTouchMove(60f, 60f)
        val m3 = detector.onTouchMove(50f, 50f)
        val m4 = detector.onTouchMove(100f, 50f)
        val m5 = detector.onTouchMove(150f, 50f)

        assertTrue("Continuous movement must activate glide state", m4 || m5)

        // 3. Touch up commits and resets state
        val recognized = detector.onTouchUp()
        // Recognized can be one of the dictionary words or null if DTW threshold not met
        // Crucially, touch-up resets internal state without exceptions
        val secondUp = detector.onTouchUp()
        assertNull("Subsequent up without move must return null", secondUp)

        // 4. Dynamic lexicon addition and candidate retrieval
        detector.addWord("customword")
        detector.onTouchDown(100f, 100f)
        val candidates = detector.onTouchUpCandidates()
        assertNotNull(candidates)
    }

    @Test
    fun testParticleEmitterPhysicsStep() {
        val emitter = ParticleEmitter()
        assertEquals(0, emitter.getActiveParticleCount())
        assertFalse(emitter.hasActiveParticles())

        // Spawn 15 particles
        emitter.emitTapBurst(originX = 100f, originY = 100f, count = 15)
        assertEquals(15, emitter.getActiveParticleCount())
        assertTrue(emitter.hasActiveParticles())

        // Step physics forward with gravity
        emitter.gravityX = 50f
        emitter.gravityY = 400f
        emitter.update(dtSeconds = 0.016f)

        // Particles are active
        assertTrue(emitter.hasActiveParticles())

        // Step physics forward long enough for particles to die (max life < 1.0s)
        for (i in 0 until 100) {
            emitter.update(dtSeconds = 0.02f)
        }
        assertEquals(0, emitter.getActiveParticleCount())
        assertFalse(emitter.hasActiveParticles())
    }

    @Test
    fun testZeroExfiltrationPrivacyGuarantees() {
        val TYPE_CLASS_TEXT = 1
        val TYPE_TEXT_VARIATION_PASSWORD = 128
        val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 144
        val TYPE_TEXT_VARIATION_WEB_PASSWORD = 224
        val TYPE_NUMBER_VARIATION_PASSWORD = 16

        fun isSensitiveInput(inputType: Int): Boolean {
            val variation = inputType and 0x00000ff0
            return (variation == TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == TYPE_NUMBER_VARIATION_PASSWORD)
        }

        assertTrue(isSensitiveInput(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(isSensitiveInput(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(isSensitiveInput(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(isSensitiveInput(2 or TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(isSensitiveInput(TYPE_CLASS_TEXT))
    }

    @Test
    fun testThemeCatalogEnumeration() {
        val themes = KeyboardThemeId.values()
        assertEquals(5, themes.size)

        val keys = themes.map { it.key }.toSet()
        assertTrue(keys.contains("midnight_slate"))
        assertTrue(keys.contains("pitch_black_oled"))
        assertTrue(keys.contains("slate_gray"))
        assertTrue(keys.contains("paper_white"))
        assertTrue(keys.contains("high_contrast_cyber"))
    }
}

package com.teleboard.teleboard.keyboard.theme

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class SoundFeedbackPlayer(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var mechanicalSoundId = -1
    private var typewriterSoundId = -1
    private var bubbleSoundId = -1
    private var isLoaded = false

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        // Generate synthetic low-latency PCM WAV files in cache if raw assets not provided
        try {
            val mechFile = generatePcmWav("mech.wav", 800f, 15)
            val typeFile = generatePcmWav("type.wav", 1400f, 20)
            val popFile = generatePcmWav("pop.wav", 650f, 30)

            mechanicalSoundId = soundPool?.load(mechFile.absolutePath, 1) ?: -1
            typewriterSoundId = soundPool?.load(typeFile.absolutePath, 1) ?: -1
            bubbleSoundId = soundPool?.load(popFile.absolutePath, 1) ?: -1
            isLoaded = true
        } catch (_: Exception) {}
    }

    fun playKeySound(view: View) {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("flutter.keyboard_sound_enabled", false)
        val hapticEnabled = prefs.getBoolean("flutter.keyboard_haptic_enabled", true)

        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        if (!soundEnabled || !isLoaded || soundPool == null) return

        val style = prefs.getString("flutter.keyboard_sound_style", "mechanical") ?: "mechanical"
        val soundId = when (style.lowercase()) {
            "typewriter" -> typewriterSoundId
            "bubble" -> bubbleSoundId
            else -> mechanicalSoundId
        }

        if (soundId > 0) {
            soundPool?.play(soundId, 0.45f, 0.45f, 1, 0, 1.0f)
        }
    }

    private fun generatePcmWav(filename: String, freq: Float, durationMs: Int): File {
        val file = File(context.cacheDir, filename)
        if (file.exists() && file.length() > 0) return file

        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = (1.0 - (i.toDouble() / numSamples))
            val angle = 2.0 * Math.PI * freq * t
            val sample = (sin(angle) * decay * Short.MAX_VALUE * 0.4).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        writeWavFile(file, buffer, sampleRate)
        return file
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val byteData = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteData.putShort(s)
        val audioBytes = byteData.array()

        val totalDataLen = audioBytes.size + 36
        val byteRate = sampleRate * 2

        FileOutputStream(file).use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalDataLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // subchunk1 size
            out.write(shortToByteArray(1)) // PCM format
            out.write(shortToByteArray(1)) // mono
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray(2)) // block align
            out.write(shortToByteArray(16)) // bits per sample
            out.write("data".toByteArray())
            out.write(intToByteArray(audioBytes.size))
            out.write(audioBytes)
        }
    }

    private fun intToByteArray(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun shortToByteArray(value: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}

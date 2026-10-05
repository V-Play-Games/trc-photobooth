package com.trc.photobooth.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.trc.photobooth.R

/**
 * Manages low-latency audio sound effects for the photo booth experience,
 * including countdown timer beeps and camera shutter snaps.
 * Uses Android's [SoundPool] for immediate, stutter-free playback.
 */
class SoundHelper(context: Context) {
    private val tag = "SoundHelper"

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("trc_photobooth_prefs", Context.MODE_PRIVATE)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
    } catch (e: Exception) {
        Log.w(tag, "Failed to initialize SoundPool: ${e.message}")
        null
    }

    private var beepSoundId: Int = 0
    private var shutterSoundId: Int = 0
    private var beepLoaded: Boolean = false
    private var shutterLoaded: Boolean = false

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("sound_effects_enabled", true)
        set(value) {
            prefs.edit().putBoolean("sound_effects_enabled", value).apply()
        }

    init {
        try {
            soundPool?.let { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        if (sampleId == beepSoundId) beepLoaded = true
                        if (sampleId == shutterSoundId) shutterLoaded = true
                    } else {
                        Log.w(tag, "Failed to load sound sample $sampleId, status=$status")
                    }
                }
                beepSoundId = pool.load(appContext, R.raw.timer_beep, 1)
                shutterSoundId = pool.load(appContext, R.raw.camera_shutter, 1)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error loading raw audio resources: ${e.message}")
        }
    }

    /**
     * Plays the audio beep sound effect on countdown timer ticks.
     */
    fun playTimerBeep(volume: Float = 1.0f) {
        if (!isSoundEnabled) return
        try {
            if (beepSoundId != 0) {
                soundPool?.play(beepSoundId, volume, volume, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            Log.d(tag, "Failed to play timer beep: ${e.message}")
        }
    }

    /**
     * Plays the realistic mechanical camera shutter snap sound effect on image click / capture.
     */
    fun playCameraShutter(volume: Float = 1.0f) {
        if (!isSoundEnabled) return
        try {
            if (shutterSoundId != 0) {
                soundPool?.play(shutterSoundId, volume, volume, 2, 0, 1.0f)
            }
        } catch (e: Exception) {
            Log.d(tag, "Failed to play camera shutter: ${e.message}")
        }
    }

    /**
     * Releases the native sound pool resources.
     */
    fun release() {
        try {
            soundPool?.release()
        } catch (e: Exception) {
            Log.d(tag, "Error releasing SoundPool: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: SoundHelper? = null

        fun getInstance(context: Context): SoundHelper {
            return instance ?: synchronized(this) {
                instance ?: SoundHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

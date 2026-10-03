package com.trc.photobooth.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class HapticHelper(context: Context) {
    private val tag = "HapticHelper"

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.w(tag, "Failed to initialize Vibrator: ${e.message}")
        null
    }

    /**
     * Subtle tick for countdown second ticks.
     */
    fun tick() {
        vibrateSafely {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(20, 60)
            }
        }
    }

    /**
     * Crisp, heavy click simulating a mechanical camera shutter snap.
     */
    fun shutterSnap() {
        vibrateSafely {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else {
                VibrationEffect.createOneShot(60, 255)
            }
        }
    }

    /**
     * Double pulse confirming capture processing is finished.
     */
    fun captureComplete() {
        vibrateSafely {
            val timings = longArrayOf(0, 35, 60, 45)
            val amplitudes = intArrayOf(0, 120, 0, 200)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        }
    }

    private inline fun vibrateSafely(effectProvider: () -> VibrationEffect) {
        try {
            if (vibrator?.hasVibrator() == true) {
                val effect = effectProvider()
                vibrator.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.d(tag, "Haptic feedback skipped: ${e.message}")
        }
    }
}

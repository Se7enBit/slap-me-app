package com.noikonomist.slapme.slap

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A sharp heavy hit followed by a short decaying rumble. Uses alarm vibration attributes so the slap
 * still buzzes when the phone is on silent.
 */
class SlapHaptics(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    /** Returns what happened, for the log. */
    fun hit(): String {
        if (!vibrator.hasVibrator()) return "no vibrator"
        val amplitude = vibrator.hasAmplitudeControl()
        val effect = if (amplitude) {
            VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(FALLBACK_TIMINGS, NO_REPEAT)
        }
        vibrate(effect)
        return if (amplitude) "waveform with amplitudes" else "on/off fallback"
    }

    /** A short buzz that comes with the warning. */
    fun buzz() {
        if (!vibrator.hasVibrator()) return
        vibrate(VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private companion object {
        const val NO_REPEAT = -1
        const val BUZZ_MS = 120L

        // off, HIT, gap, rumble decaying
        val TIMINGS = longArrayOf(0, 60, 30, 90, 90, 90)
        val AMPLITUDES = intArrayOf(0, 255, 0, 140, 90, 45)

        val FALLBACK_TIMINGS = longArrayOf(0, 70, 40, 180)
    }
}

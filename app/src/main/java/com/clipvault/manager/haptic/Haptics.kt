package com.clipvault.manager.haptic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Centralized haptic feedback. Picks the right API per Android version:
 *   - API 31+ (VibratorManager)  → predefined effects (EFFECT_CLICK, EFFECT_TICK, etc.)
 *   - API 26+                    → VibrationEffect.createOneShot fallback
 *   - Older                      → Vibrator.vibrate(long) with primitive durations
 *
 * Skips vibration entirely if the user has system haptics disabled.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val enabled: Boolean =
        Settings.System.getInt(context.contentResolver, "haptic_feedback_enabled", 1) != 0

    @SuppressLint("InlinedApi")
    /** Light tap — copy, FAB tap, chip select. */
    fun light() = vibrateCompat(
        predefined = VibrationEffect.EFFECT_CLICK,
        fallbackMs = 10L
    )

    @SuppressLint("InlinedApi")
    /** Medium tap — pin toggle, save confirmation. */
    fun medium() = vibrateCompat(
        predefined = VibrationEffect.EFFECT_HEAVY_CLICK,
        fallbackMs = 25L
    )

    @SuppressLint("InlinedApi")
    /** Strong, attention-grabbing — destructive action (delete). */
    fun heavy() = vibrateCompat(
        predefined = VibrationEffect.EFFECT_HEAVY_CLICK,
        fallbackMs = 40L,
        fallbackAmplitude = 200
    )

    /** Tiny tick — swipe crossing threshold, slider ticks. */
    fun tick() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) { /* fall through */ }
        }
        vibrateCompat(predefined = -1, fallbackMs = 5L)
    }

    /** Two quick pulses — "done" / undo restore / onboarding page forward. */
    fun success() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                return
            } catch (_: Exception) { /* fall through */ }
        }
        // Compose: two short ticks
        vibrateCompat(predefined = -1, fallbackMs = 12L)
    }

    /** Long-press style — undo confirmation, accessibility toggle. */
    fun longPress() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Use legacy HapticFeedbackConstants — for view-level events. */
    fun constant(type: Int) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateCompat(
        predefined: Int,
        fallbackMs: Long,
        fallbackAmplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE
    ) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && predefined >= 0) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(predefined))
                return
            } catch (_: Exception) { /* fall through */ }
        }
        vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, fallbackAmplitude))
    }
}
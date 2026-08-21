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

    private val enabled: Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, "haptic_feedback_enabled", 1) != 0
    }.getOrDefault(true)

    private val clickEffect: VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runCatching { VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) }.getOrNull()
        else null

    private val heavyClickEffect: VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runCatching { VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) }.getOrNull()
        else null

    private val doubleClickEffect: VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            runCatching { VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK) }.getOrNull()
        else null

    private val tickEffect: VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            runCatching { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }.getOrNull()
        else null

    // Predefined Q fallback — one-shot pulses with a constant (duration, amplitude)
    // pair per instance, so cache once. createOneShot is API 26+; minSdk is 26.
    private val lightFallback: VibrationEffect =
        VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
    private val mediumFallback: VibrationEffect =
        VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE)
    private val heavyFallback: VibrationEffect =
        VibrationEffect.createOneShot(40L, 200)
    private val tickFallback: VibrationEffect =
        VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE)
    private val successFallback: VibrationEffect =
        VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
    private val longPressEffect: VibrationEffect =
        VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE)
    private val constantEffect: VibrationEffect =
        VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)

    @SuppressLint("InlinedApi")
    /** Light tap — copy, FAB tap, chip select. */
    fun light() = vibrateCompat(clickEffect, lightFallback)

    @SuppressLint("InlinedApi")
    /** Medium tap — pin toggle, save confirmation. */
    fun medium() = vibrateCompat(heavyClickEffect, mediumFallback)

    @SuppressLint("InlinedApi")
    /** Strong, attention-grabbing — destructive action (delete). */
    fun heavy() = vibrateCompat(heavyClickEffect, heavyFallback)

    /** Tiny tick — swipe crossing threshold, slider ticks. */
    fun tick() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        tickEffect?.let {
            try {
                vibrator.vibrate(it)
                return
            } catch (_: Exception) { /* fall through */ }
        }
        vibrator.vibrate(tickFallback)
    }

    /** Two quick pulses — "done" / undo restore / onboarding page forward. */
    fun success() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        doubleClickEffect?.let {
            try {
                vibrator.vibrate(it)
                return
            } catch (_: Exception) { /* fall through */ }
        }
        vibrator.vibrate(successFallback)
    }

    /** Long-press style — undo confirmation, accessibility toggle. */
    fun longPress() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(longPressEffect)
    }

    /** Use legacy HapticFeedbackConstants — for view-level events. */
    fun constant(type: Int) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(constantEffect)
    }

    private fun vibrateCompat(predefined: VibrationEffect?, fallback: VibrationEffect) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        predefined?.let {
            try {
                vibrator.vibrate(it)
                return
            } catch (_: Exception) { /* fall through */ }
        }
        vibrator.vibrate(fallback)
    }
}
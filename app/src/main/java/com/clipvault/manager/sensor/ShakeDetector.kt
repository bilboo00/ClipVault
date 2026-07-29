package com.clipvault.manager.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Detects a "shake" gesture via the accelerometer.
 *
 * Algorithm:
 *   • Compute linear acceleration magnitude: sqrt(x² + y² + z²) − gravity (≈ 9.8)
 *   • Fire when magnitude exceeds SHAKE_THRESHOLD, then require SHAKE_COOLDOWN_MS before next.
 *
 * Flow is hot while the host (e.g. MainActivity) collects it.
 */
object ShakeDetector {

    private const val SHAKE_THRESHOLD = 13.5f  // m/s² above gravity
    private const val SHAKE_COOLDOWN_MS = 900L
    private const val GRAVITY = 9.81f

    fun shakeFlow(context: Context): Flow<Unit> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: run { close(); return@callbackFlow }

        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                val linear = magnitude - GRAVITY
                if (linear > SHAKE_THRESHOLD) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                        lastShakeAt = now
                        trySend(Unit)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
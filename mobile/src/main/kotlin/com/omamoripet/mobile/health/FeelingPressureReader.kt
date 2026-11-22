package com.omamoripet.mobile.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.omamoripet.mobile.data.FeelingPressure
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun Context.readFeelingPressure(): FeelingPressure? {
    val manager = getSystemService(SensorManager::class.java) ?: return null
    val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return null
    return withTimeoutOrNull(2_000L) {
        callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val pressure = event.values.firstOrNull()?.toDouble() ?: return
                    if (pressure !in 100.0..1_500.0) return
                    val sampleAgeMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
                    trySend(FeelingPressure(pressure, System.currentTimeMillis() - sampleAgeMs.coerceAtLeast(0)))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            val registered = manager.registerListener(
                listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper())
            )
            if (!registered) close()
            awaitClose { if (registered) manager.unregisterListener(listener) }
        }.firstOrNull()
    }
}

package com.example.stickman.game

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val EARTH_GRAVITY = Vec2(0f, 9.81f)

@Composable
internal fun rememberDeviceGravity(): State<Vec2> {
    val context = LocalContext.current
    val gravity = remember { mutableStateOf(EARTH_GRAVITY) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The gravity sensor reports the vector pointing UP (opposite the pull),
                // so negate to get "toward the ground". Device +y is up while screen y is
                // down, which flips that axis back, leaving (-x, +y).
                gravity.value = Vec2(-event.values[0], event.values[1])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    return gravity
}

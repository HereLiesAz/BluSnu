package com.hereliesaz.blusnu.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class CompassManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    fun azimuthFlow(): Flow<Float> = callbackFlow {
        if (accelerometer == null || magnetometer == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            private val gravity = FloatArray(3)
            private val geomagnetic = FloatArray(3)
            private var hasGravity = false
            private var hasGeomagnetic = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    event.values.copyInto(gravity)
                    hasGravity = true
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    event.values.copyInto(geomagnetic)
                    hasGeomagnetic = true
                }

                if (hasGravity && hasGeomagnetic) {
                    val R = FloatArray(9)
                    val I = FloatArray(9)
                    if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(R, orientation)
                        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        // Azimuth is -180 to 180. Normalize to 0-360
                        val normalizedAzimuth = (azimuth + 360) % 360
                        trySend(normalizedAzimuth)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No-op
            }
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

package com.hereliesaz.blusnu.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manager class for accessing device compass orientation (Azimuth).
 *
 * This is used for "Direction Finding" features, allowing the UI to rotate
 * arrows or maps based on which way the phone is pointing relative to North.
 */
class CompassManager(context: Context) {
    // Access the system SensorManager.
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // We need both Accelerometer and Magnetometer to calculate orientation accurately.
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /**
     * Returns a Flow that emits the current azimuth (degrees from North).
     *
     * @return Flow<Float> where Float is 0.0 to 360.0.
     */
    fun azimuthFlow(): Flow<Float> = callbackFlow {
        // If sensors are missing, close the flow immediately.
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

                // Capture latest values from sensors.
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, gravity.size)
                    hasGravity = true
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.size)
                    hasGeomagnetic = true
                }

                // If we have both gravity and geomagnetic data, we can calculate rotation.
                if (hasGravity && hasGeomagnetic) {
                    val R = FloatArray(9) // Rotation Matrix
                    val I = FloatArray(9) // Inclination Matrix

                    if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        // Computes the device's orientation based on the rotation matrix.
                        SensorManager.getOrientation(R, orientation)

                        // orientation[0] is Azimuth in radians (-PI to PI).
                        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                        // Normalize to 0-360 degrees.
                        // ((-170 + 360) % 360) -> 190
                        val normalizedAzimuth = (azimuth + 360) % 360
                        trySend(normalizedAzimuth)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No-op for now.
            }
        }

        // Register listeners with UI delay (sufficient for visual updates).
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        // Cleanup on flow cancellation.
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

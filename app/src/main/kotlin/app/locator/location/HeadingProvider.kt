package app.locator.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The direction the phone's top edge points to, in degrees clockwise from north. Faked in tests. */
fun interface HeadingProvider {
    /** Emits while collected; empty when the phone has no compass. */
    fun headings(): Flow<Float>
}

/** Rotation-vector sensor; no permission needed. */
class AndroidHeadingProvider(context: Context) : HeadingProvider {
    private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun headings(): Flow<Float> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                trySend(Geo.normalize(Math.toDegrees(orientation[0].toDouble())).toFloat())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }.conflate()
}

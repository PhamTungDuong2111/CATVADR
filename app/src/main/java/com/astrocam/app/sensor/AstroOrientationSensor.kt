package com.astrocam.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class OrientationData(
    val pitch: Float = 0f,       // Góc ngẩng bầu trời (-90° đến +90°)
    val roll: Float = 0f,        // Góc nghiêng chân trời (-180° đến +180°)
    val azimuth: Float = 0f,     // Hướng la bàn (0° - 360°)
    val isLevel: Boolean = true  // Tripod đã cân bằng đường chân trời (±1.5°)
) {
    val cardinalDirection: String
        get() = when (((azimuth + 22.5f) % 360 / 45f).toInt()) {
            0 -> "N (Bắc)"
            1 -> "NE (Đông Bắc)"
            2 -> "E (Đông)"
            3 -> "SE (Đông Nam)"
            4 -> "S (Nam)"
            5 -> "SW (Tây Nam)"
            6 -> "W (Tây)"
            7 -> "NW (Tây Bắc)"
            else -> "N (Bắc)"
        }
}

class AstroOrientationSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = if (rotationVectorSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null

    private val _orientation = MutableStateFlow(OrientationData())
    val orientation: StateFlow<OrientationData> = _orientation.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let { if (it < 0) it + 360f else it }
            val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            val isLevel = abs(rollDeg) < 1.5f || abs(abs(rollDeg) - 180f) < 1.5f

            _orientation.value = OrientationData(
                pitch = pitchDeg,
                roll = rollDeg,
                azimuth = azimuthDeg,
                isLevel = isLevel
            )
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val rollDeg = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
            val pitchDeg = Math.toDegrees(atan2(-az.toDouble(), sqrt(ax * ax + ay * ay).toDouble())).toFloat()

            _orientation.value = OrientationData(
                pitch = pitchDeg,
                roll = rollDeg,
                azimuth = 0f,
                isLevel = abs(rollDeg) < 1.5f
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

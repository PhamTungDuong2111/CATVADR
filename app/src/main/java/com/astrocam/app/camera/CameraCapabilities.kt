package com.astrocam.app.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range

data class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: Int,
    val hardwareLevelName: String,
    val isManualSensorSupported: Boolean,
    val isRawSupported: Boolean,
    val isoRange: Range<Int>,
    val exposureTimeRangeNs: Range<Long>, // Nanoseconds: 1s = 1_000_000_000L
    val minFocusDistance: Float
) {
    val maxExposureSeconds: Float
        get() = exposureTimeRangeNs.upper.toFloat() / 1_000_000_000f

    val minExposureSeconds: Float
        get() = exposureTimeRangeNs.lower.toFloat() / 1_000_000_000f

    companion object {
        fun inspect(cameraManager: CameraManager, cameraId: String): CameraCapabilities {
            val chars = cameraManager.getCameraCharacteristics(cameraId)

            val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

            val hwName = when (hwLevel) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                else -> "EXTERNAL"
            }

            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val manualSensor = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val rawSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                ?: Range(100, 3200)

            val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                ?: Range(100_000L, 1_000_000_000L) // Fallback: 0.1ms to 1s

            val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f

            return CameraCapabilities(
                cameraId = cameraId,
                hardwareLevel = hwLevel,
                hardwareLevelName = hwName,
                isManualSensorSupported = manualSensor,
                isRawSupported = rawSupported,
                isoRange = isoRange,
                exposureTimeRangeNs = expRange,
                minFocusDistance = minFocus
            )
        }
    }
}

package com.astrocam.app.camera

enum class AppCameraMode(val title: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    ASTRO("ASTRO"),
    PRO("PRO")
}

enum class FlashMode {
    OFF,
    ON,
    AUTO,
    TORCH
}

data class CameraState(
    val iso: Int = 1600,
    val exposureTimeSeconds: Float = 2.0f,
    val focusDistance: Float = 0.0f, // 0.0f = Infinity (Standard for Astrophotography)
    val isInfinityLocked: Boolean = true,
    val isCapturing: Boolean = false,
    val stackedFramesCount: Int = 0,
    val targetFramesCount: Int = 30,
    val rejectedFramesCount: Int = 0,
    val totalExposureSeconds: Float = 0.0f,
    val mode: AppCameraMode = AppCameraMode.ASTRO,
    val isRecordingVideo: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val flashMode: FlashMode = FlashMode.OFF,
    val isFrontCamera: Boolean = false
)

package com.astrocam.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import com.astrocam.app.postprocess.ImageExporter
import com.astrocam.app.stacker.StackingPipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class Camera2Controller(
    private val context: Context,
    private val onFrameAvailable: (Image) -> Unit
) {
    companion object {
        private const val TAG = "Camera2Controller"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private val isOpening = AtomicBoolean(false)
    private var activeCameraId: String? = null

    // Video Recording
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var timerJob: Job? = null

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AstroCameraThread").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(preferredCameraId: String? = null) {
        if (cameraDevice != null || isOpening.getAndSet(true)) {
            Log.w(TAG, "Camera is already open or opening, ignoring duplicate request")
            return
        }
        startBackgroundThread()
        try {
            val targetId = preferredCameraId ?: findBestBackCamera() ?: run {
                isOpening.set(false)
                return
            }
            activeCameraId = targetId

            val chars = cameraManager.getCameraCharacteristics(targetId)
            cachedSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            val isFront = (facing == CameraCharacteristics.LENS_FACING_FRONT)
            _state.value = _state.value.copy(isFrontCamera = isFront)

            val caps = CameraCapabilities.inspect(cameraManager, targetId)
            _capabilities.value = caps

            // Clamp initial state to hardware capabilities safely
            val minIso = minOf(caps.isoRange.lower, caps.isoRange.upper)
            val maxIso = maxOf(caps.isoRange.lower, caps.isoRange.upper)
            val minExp = minOf(caps.minExposureSeconds, caps.maxExposureSeconds)
            val maxExp = maxOf(caps.minExposureSeconds, caps.maxExposureSeconds)
            val clampedIso = _state.value.iso.coerceIn(minIso, maxIso)
            val clampedExp = _state.value.exposureTimeSeconds.coerceIn(minExp, maxExp)
            _state.value = _state.value.copy(iso = clampedIso, exposureTimeSeconds = clampedExp)

            cameraManager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    isOpening.set(false)
                    isRunning.set(true)
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isOpening.set(false)
                    isRunning.set(false)
                    try { camera.close() } catch (e: Exception) {}
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isOpening.set(false)
                    isRunning.set(false)
                    Log.e(TAG, "Camera device error: $error")
                    try { camera.close() } catch (e: Exception) {}
                    cameraDevice = null
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            isOpening.set(false)
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun findBestBackCamera(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private var cachedSensorOrientation: Int = 90
    fun getSensorOrientation(): Int = cachedSensorOrientation


    fun switchCamera() {
        val targetFacing = if (_state.value.isFrontCamera) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        var targetId: String? = null
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                targetId = id
                break
            }
        }
        if (targetId != null) {
            closeCamera()
            openCamera(targetId)
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return

        // Query supported output sizes for YUV_420_888 to guarantee hardware compatibility
        val chars = cameraManager.getCameraCharacteristics(camera.id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()

        val chosenSize = yuvSizes.firstOrNull { it.width == 1920 && it.height == 1080 }
            ?: yuvSizes.filter { it.width <= 1920 && it.height <= 1080 }
                .maxByOrNull { it.width * it.height }
            ?: yuvSizes.firstOrNull()
            ?: Size(1280, 720)

        Log.i(TAG, "Configuring stream with resolution: ${chosenSize.width}x${chosenSize.height}")

        imageReader = ImageReader.newInstance(
            chosenSize.width,
            chosenSize.height,
            ImageFormat.YUV_420_888,
            3 // Buffer capacity
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        onFrameAvailable(image)
                    } finally {
                        image.close()
                    }
                }
            }, backgroundHandler)
        }

        val surfaces = listOf(imageReader!!.surface)

        try {
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    updateCaptureRequest()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    fun setCameraMode(newMode: AppCameraMode) {
        if (_state.value.mode != newMode) {
            _state.value = _state.value.copy(mode = newMode)
            updateCaptureRequest()
        }
    }

    fun setFlashMode(newFlash: FlashMode) {
        _state.value = _state.value.copy(flashMode = newFlash)
        updateCaptureRequest()
    }

    fun toggleFlash() {
        val next = when (_state.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
            FlashMode.TORCH -> FlashMode.OFF
        }
        setFlashMode(next)
    }

    fun updateSettings(
        iso: Int = _state.value.iso,
        exposureTimeSeconds: Float = _state.value.exposureTimeSeconds,
        focusDistance: Float = _state.value.focusDistance,
        isInfinityLocked: Boolean = _state.value.isInfinityLocked
    ) {
        val caps = _capabilities.value
        val clampedIso = if (caps != null) iso.coerceIn(caps.isoRange.lower, caps.isoRange.upper) else iso
        val clampedExp = if (caps != null) {
            exposureTimeSeconds.coerceIn(caps.minExposureSeconds, caps.maxExposureSeconds)
        } else exposureTimeSeconds

        _state.value = _state.value.copy(
            iso = clampedIso,
            exposureTimeSeconds = clampedExp,
            focusDistance = if (isInfinityLocked) 0.0f else focusDistance,
            isInfinityLocked = isInfinityLocked
        )

        updateCaptureRequest()
    }

    fun setIso(iso: Int) = updateSettings(iso = iso)
    fun setExposureTime(seconds: Float) = updateSettings(exposureTimeSeconds = seconds)
    fun setInfinityFocusLock(locked: Boolean) = updateSettings(isInfinityLocked = locked)

    fun setCapturing(capturing: Boolean) {
        if (_state.value.isCapturing != capturing) {
            _state.value = _state.value.copy(isCapturing = capturing)
            updateCaptureRequest()
        }
    }

    private fun updateCaptureRequest() {
        val session = captureSession ?: return
        val camera = cameraDevice ?: return
        val surface = imageReader?.surface ?: return
        val caps = _capabilities.value

        try {
            val isAutoMode = (_state.value.mode == AppCameraMode.PHOTO || _state.value.mode == AppCameraMode.VIDEO)
            val isCapturingNow = _state.value.isCapturing

            // In live preview (idle viewfinder), ALWAYS use TEMPLATE_PREVIEW and auto-exposure
            // so the viewfinder is bright, smooth (30fps), and responsive for aiming and AR planets!
            val template = when {
                _state.value.mode == AppCameraMode.VIDEO -> CameraDevice.TEMPLATE_RECORD
                isCapturingNow && caps?.isManualSensorSupported == true -> CameraDevice.TEMPLATE_MANUAL
                else -> CameraDevice.TEMPLATE_PREVIEW
            }

            val builder = camera.createCaptureRequest(template).apply {
                addTarget(surface)

                if (isAutoMode || !isCapturingNow) {
                    // Standard Auto-Exposure and Continuous Auto-Focus for regular Camera mode and live preview
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                    if (_state.value.mode == AppCameraMode.ASTRO && _state.value.isInfinityLocked) {
                        // In Astro live preview, lock focus to infinity so distant stars are sharp
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
                    } else {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }

                    when (_state.value.flashMode) {
                        FlashMode.AUTO -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                        FlashMode.ON, FlashMode.TORCH -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        }
                        FlashMode.OFF -> {
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                    }
                } else {
                    // Manual Astro / Pro Controls - ONLY ACTIVE DURING ACTUAL CAPTURE / STACKING!
                    if (caps?.isManualSensorSupported == true) {
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

                        val exposureTimeNs = (_state.value.exposureTimeSeconds * 1_000_000_000L).toLong()
                        set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)
                        set(CaptureRequest.SENSOR_FRAME_DURATION, exposureTimeNs)
                        set(CaptureRequest.SENSOR_SENSITIVITY, _state.value.iso)

                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        set(CaptureRequest.LENS_FOCUS_DISTANCE, _state.value.focusDistance)

                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                        set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF)
                        set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                    } else {
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        if (caps != null && caps.minFocusDistance > 0) {
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                            set(CaptureRequest.LENS_FOCUS_DISTANCE, _state.value.focusDistance)
                        }
                    }
                }
            }

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update capture request", e)
        }
    }

    fun startRecording(onStarted: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (_state.value.isRecordingVideo) return
        val camera = cameraDevice ?: run { onError("Camera chưa sẵn sàng"); return }

        try {
            val file = File(context.cacheDir, "VID_${System.currentTimeMillis()}.mp4")
            currentRecordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasAudio) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source failed, fallback to video only", e)
                }
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (hasAudio) {
                try {
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioSamplingRate(44100)
                    recorder.setAudioEncodingBitRate(128000)
                } catch (e: Exception) {
                    Log.w(TAG, "Audio encoder config failed", e)
                }
            }
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(1920, 1080)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(10_000_000)
            val orientation = getSensorOrientation()
            recorder.setOrientationHint(if (_state.value.isFrontCamera) 270 else orientation)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()

            mediaRecorder = recorder
            val recSurface = recorder.surface

            val surfaces = listOf(imageReader!!.surface, recSurface)
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(imageReader!!.surface)
                        addTarget(recSurface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)

                    try {
                        recorder.start()
                        _state.value = _state.value.copy(isRecordingVideo = true, recordingDurationSeconds = 0)
                        timerJob?.cancel()
                        timerJob = CoroutineScope(Dispatchers.Default).launch {
                            while (isActive && _state.value.isRecordingVideo) {
                                delay(1000)
                                _state.value = _state.value.copy(
                                    recordingDurationSeconds = _state.value.recordingDurationSeconds + 1
                                )
                            }
                        }
                        onStarted()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start media recorder", e)
                        onError("Lỗi bắt đầu quay: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure video recording session")
                    onError("Không thể tạo phiên ghi hình")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording", e)
            onError("Lỗi quay video: ${e.message}")
        }
    }

    fun stopRecording(onFinished: (Uri?) -> Unit) {
        if (!_state.value.isRecordingVideo) return
        timerJob?.cancel()
        val recorder = mediaRecorder
        val file = currentRecordingFile

        _state.value = _state.value.copy(isRecordingVideo = false, recordingDurationSeconds = 0)

        try {
            recorder?.stop()
            recorder?.reset()
            recorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }

        startCaptureSession()

        if (file != null && file.exists()) {
            CoroutineScope(Dispatchers.IO).launch {
                val uri = ImageExporter.saveVideoToGallery(context, file)
                withContext(Dispatchers.Main) {
                    onFinished(uri)
                }
            }
        } else {
            onFinished(null)
        }
    }

    fun takePhoto(pipeline: StackingPipeline, onSaved: (Bitmap?, Uri?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = if (_state.value.mode == AppCameraMode.ASTRO && pipeline.isStacking()) {
                pipeline.exportCurrentStackedBitmap()
            } else {
                pipeline.exportLivePreviewBitmap()
            }
            if (bmp != null) {
                val uri = ImageExporter.saveStandardPhoto(context, bmp)
                onSaved(bmp, uri)
            } else {
                onSaved(null, null)
            }
        }
    }

    fun closeCamera() {
        isOpening.set(false)
        isRunning.set(false)
        timerJob?.cancel()
        timerJob = null
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {}

        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
}

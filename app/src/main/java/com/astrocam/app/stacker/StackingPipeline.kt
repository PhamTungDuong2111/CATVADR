package com.astrocam.app.stacker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.opengl.GLSurfaceView
import android.util.Log
import com.astrocam.app.alignment.HomographyMatrix
import com.astrocam.app.alignment.StarAlignmentEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class StackingPipeline(
    private val context: Context,
    private val alignmentEngine: StarAlignmentEngine = StarAlignmentEngine()
) {
    companion object {
        private const val TAG = "StackingPipeline"
    }

    var renderer: GlTextureRenderer? = null
    var glSurfaceView: GLSurfaceView? = null
    var currentMode: StackingMode = StackingMode.DEEP_SKY_AVERAGE

    private val _stackedFrames = MutableStateFlow(0)
    val stackedFrames: StateFlow<Int> = _stackedFrames.asStateFlow()

    private val _rejectedFrames = MutableStateFlow(0)
    val rejectedFrames: StateFlow<Int> = _rejectedFrames.asStateFlow()

    private val _totalExposureSeconds = MutableStateFlow(0f)
    val totalExposureSeconds: StateFlow<Float> = _totalExposureSeconds.asStateFlow()

    private val _isStacking = MutableStateFlow(false)
    val isStacking: StateFlow<Boolean> = _isStacking.asStateFlow()

    private val _detectedStarCount = MutableStateFlow(0)
    val detectedStarCount: StateFlow<Int> = _detectedStarCount.asStateFlow()

    private val _histogramData = MutableStateFlow<IntArray?>(null)
    val histogramData: StateFlow<IntArray?> = _histogramData.asStateFlow()

    private val isStackingActive = AtomicInteger(0) // 0 = Idle, 1 = Active
    private var directRgbaBuffer: ByteBuffer? = null
    private val glBufferPool = arrayOfNulls<ByteBuffer>(3)
    private var glBufferIndex = 0
    private var cachedYBytes: ByteArray? = null
    private var targetFrameCount = 30

    fun onGlInitialized(view: GLSurfaceView, rend: GlTextureRenderer) {
        glSurfaceView = view
        renderer = rend
    }

    fun startStacking(mode: StackingMode, targetFrames: Int = 30) {
        currentMode = mode
        targetFrameCount = targetFrames.coerceAtLeast(1)
        _stackedFrames.value = 0
        _rejectedFrames.value = 0
        _totalExposureSeconds.value = 0f
        alignmentEngine.resetReference()
        glSurfaceView?.queueEvent {
            renderer?.resetStack()
        }
        isStackingActive.set(1)
        _isStacking.value = true
        Log.i(TAG, "Started stacking in mode: $mode")
    }

    fun stopStacking() {
        isStackingActive.set(0)
        _isStacking.value = false
        Log.i(TAG, "Stopped stacking. Total stacked: ${_stackedFrames.value}")
    }

    fun isStacking(): Boolean = isStackingActive.get() == 1

    /**
     * Process incoming camera frame in YUV_420_888 format
     */
    fun processFrame(image: Image, exposureTimeSeconds: Float) {
        val view = glSurfaceView ?: return
        val gl = renderer ?: return

        try {
            val width = image.width
            val height = image.height

            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            // 1. Convert YUV to RGBA buffer (Fast C++ JNI with reused direct buffer)
            val rgbaBuffer = getOrCreateRgbaBuffer(width * height * 4)
            val nativeSuccess = alignmentEngine.convertYuvToRgba(
                yBuffer, yRowStride,
                uBuffer, uRowStride, uPixelStride,
                vBuffer, vRowStride, vPixelStride,
                rgbaBuffer, width, height
            )
            if (!nativeSuccess) {
                convertYuvToRgbaBufferFallback(image, rgbaBuffer)
            }
            rgbaBuffer.rewind()

            // 2. Compute live histogram & star count from Y plane
            yBuffer.rewind()
            val remaining = yBuffer.remaining()
            val yBytes = if (cachedYBytes != null && cachedYBytes!!.size == remaining) {
                cachedYBytes!!
            } else {
                val newArr = ByteArray(remaining)
                cachedYBytes = newArr
                newArr
            }
            yBuffer.get(yBytes)
            updateHistogramFromGrayscale(yBytes, width, height, yRowStride)

            val isCurrentlyStacking = isStacking()

            if (!isCurrentlyStacking) {
                // Live Viewfinder mode: detect stars for HUD and upload texture to GL
                val detectedStars = alignmentEngine.detectStars(
                    yBytes, width, height,
                    threshold = 3.0f, maxStars = 100, rowStride = yRowStride
                )
                _detectedStarCount.value = detectedStars.size

                val frameForGl = copyForGl(rgbaBuffer, width * height * 4)
                view.queueEvent {
                    gl.uploadInputFrame(frameForGl, width, height)
                }
                return
            }

            // 3. Stacking mode: Star Detection & Alignment
            var homography = HomographyMatrix.Identity

            if (currentMode.requiresAlignment) {
                val detectedStars = alignmentEngine.detectStars(
                    yBytes, width, height,
                    threshold = 3.0f, maxStars = 100, rowStride = yRowStride
                )
                _detectedStarCount.value = detectedStars.size

                if (!alignmentEngine.hasReference()) {
                    if (detectedStars.size >= 4) {
                        alignmentEngine.setReferenceFrame(detectedStars)
                    } else {
                        _rejectedFrames.value += 1
                        return
                    }
                } else {
                    val computedH = alignmentEngine.computeAlignment(
                        detectedStars,
                        width.toFloat(),
                        height.toFloat()
                    )
                    if (computedH == null) {
                        _rejectedFrames.value += 1
                        Log.w(TAG, "Frame rejected: Star alignment failed")
                        return
                    }
                    homography = computedH
                }
            }

            // 4. Stacking on GPU (Safely queued on GLThread)
            val nextFrameCount = _stackedFrames.value + 1
            val frameForGl = copyForGl(rgbaBuffer, width * height * 4)
            view.queueEvent {
                gl.uploadInputFrame(frameForGl, width, height)
                gl.renderStack(currentMode, homography, nextFrameCount)
                _stackedFrames.value = nextFrameCount
                _totalExposureSeconds.value += exposureTimeSeconds
                if (nextFrameCount >= targetFrameCount) {
                    isStackingActive.set(0)
                    _isStacking.value = false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame in StackingPipeline", e)
        }
    }

    /**
     * Process synthetic bitmap (used for indoor / daytime simulation mode)
     */
    fun processBitmap(bitmap: Bitmap, exposureTimeSeconds: Float) {
        val view = glSurfaceView ?: return
        val gl = renderer ?: return

        try {
            val width = bitmap.width
            val height = bitmap.height
            val rgbaBuffer = getOrCreateRgbaBuffer(width * height * 4)
            bitmap.copyPixelsToBuffer(rgbaBuffer)
            rgbaBuffer.rewind()

            // Grayscale bytes for star detection & histogram
            val grayBytes = ByteArray(width * height)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val hist = IntArray(256)
            for (i in 0 until (width * height)) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
                grayBytes[i] = lum.toByte()
                hist[lum]++
            }
            _histogramData.value = hist

            val detectedStars = alignmentEngine.detectStars(
                grayBytes, width, height,
                threshold = 2.5f, maxStars = 100, rowStride = width
            )
            _detectedStarCount.value = detectedStars.size

            if (!isStacking()) {
                val frameForGl = copyForGl(rgbaBuffer, width * height * 4)
                view.queueEvent {
                    gl.uploadInputFrame(frameForGl, width, height)
                }
                return
            }

            var homography = HomographyMatrix.Identity
            if (currentMode.requiresAlignment) {
                if (!alignmentEngine.hasReference()) {
                    if (detectedStars.size >= 3) {
                        alignmentEngine.setReferenceFrame(detectedStars)
                    } else {
                        _rejectedFrames.value += 1
                        return
                    }
                } else {
                    val computedH = alignmentEngine.computeAlignment(
                        detectedStars,
                        width.toFloat(),
                        height.toFloat()
                    )
                    if (computedH == null) {
                        _rejectedFrames.value += 1
                        return
                    }
                    homography = computedH
                }
            }

            val nextFrameCount = _stackedFrames.value + 1
            val frameForGl = copyForGl(rgbaBuffer, width * height * 4)
            view.queueEvent {
                gl.uploadInputFrame(frameForGl, width, height)
                gl.renderStack(currentMode, homography, nextFrameCount)
                _stackedFrames.value = nextFrameCount
                _totalExposureSeconds.value += exposureTimeSeconds
                if (nextFrameCount >= targetFrameCount) {
                    isStackingActive.set(0)
                    _isStacking.value = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing synthetic bitmap", e)
        }
    }

    private fun updateHistogramFromGrayscale(yBytes: ByteArray, width: Int, height: Int, rowStride: Int) {
        val hist = IntArray(256)
        val step = 8 // Sample every 8th pixel for instant performance
        for (y in 0 until height step step) {
            val rowOffset = y * rowStride
            for (x in 0 until width step step) {
                val lum = yBytes[rowOffset + x].toInt() and 0xFF
                hist[lum]++
            }
        }
        _histogramData.value = hist
    }

    suspend fun exportCurrentStackedBitmap(
        midtone: Float = 0.15f,
        blackPoint: Float = 0.02f,
        saturation: Float = 1.3f,
        applyStretch: Boolean = true
    ): Bitmap? = suspendCancellableCoroutine { cont ->
        val view = glSurfaceView
        val gl = renderer
        if (view == null || gl == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        view.queueEvent {
            try {
                val bmp = gl.readCurrentStackedBitmap(
                    midtone = midtone,
                    blackPoint = blackPoint,
                    saturation = saturation,
                    applyStretch = applyStretch
                )
                cont.resume(bmp)
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting bitmap on GL thread", e)
                cont.resume(null)
            }
        }
    }
    fun setDisplayOrientation(rotation: Int, isMirrored: Boolean) {
        val view = glSurfaceView ?: return
        val gl = renderer ?: return
        view.queueEvent {
            gl.setDisplayOrientation(rotation, isMirrored)
        }
    }

    suspend fun exportLivePreviewBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        val view = glSurfaceView
        val gl = renderer
        if (view == null || gl == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        view.queueEvent {
            try {
                val bmp = gl.readLivePreviewBitmap()
                cont.resume(bmp)
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting live preview bitmap on GL thread", e)
                cont.resume(null)
            }
        }
    }

    private fun getOrCreateRgbaBuffer(capacity: Int): ByteBuffer {
        val existing = directRgbaBuffer
        val buf = if (existing == null || existing.capacity() < capacity) {
            val created = ByteBuffer.allocateDirect(capacity)
            directRgbaBuffer = created
            created
        } else {
            existing
        }
        buf.clear()
        return buf
    }

    private fun getBufferForGl(size: Int): ByteBuffer {
        glBufferIndex = (glBufferIndex + 1) % 3
        val existing = glBufferPool[glBufferIndex]
        val buf = if (existing == null || existing.capacity() < size) {
            val created = ByteBuffer.allocateDirect(size)
            glBufferPool[glBufferIndex] = created
            created
        } else {
            existing
        }
        buf.clear()
        return buf
    }

    private fun copyForGl(source: ByteBuffer, size: Int): ByteBuffer {
        val target = getBufferForGl(size)
        val duplicate = source.duplicate()
        duplicate.rewind()
        target.put(duplicate)
        target.flip()
        return target
    }

    private fun convertYuvToRgbaBufferFallback(image: Image, rgba: ByteBuffer) {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        for (y in 0 until height) {
            val yRow = y * yRowStride
            val uvRow = (y / 2) * uvRowStride

            for (x in 0 until width) {
                val yVal = (yBuffer.get(yRow + x).toInt() and 0xFF)
                val uvIndex = uvRow + (x / 2) * uvPixelStride
                val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                val r = (yVal + (1.402f * vVal)).toInt().coerceIn(0, 255)
                val g = (yVal - (0.344136f * uVal) - (0.714136f * vVal)).toInt().coerceIn(0, 255)
                val b = (yVal + (1.772f * uVal)).toInt().coerceIn(0, 255)

                rgba.put(r.toByte())
                rgba.put(g.toByte())
                rgba.put(b.toByte())
                rgba.put(255.toByte())
            }
        }
    }
}

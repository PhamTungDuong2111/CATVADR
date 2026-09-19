package com.astrocam.app.alignment

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class StarAlignmentEngine {
    companion object {
        private const val TAG = "StarAlignmentEngine"
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("astro_native")
                isNativeLoaded = true
                Log.i(TAG, "Successfully loaded native astro_native C++ library")
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.w(TAG, "Native library not found, using Kotlin pure math fallback", e)
            }
        }
    }

    private var referenceStars: List<StarPoint> = emptyList()

    fun resetReference() {
        referenceStars = emptyList()
    }

    fun hasReference(): Boolean = referenceStars.isNotEmpty()

    fun setReferenceFrame(stars: List<StarPoint>) {
        referenceStars = stars
    }

    fun detectStars(
        grayData: ByteArray,
        width: Int,
        height: Int,
        threshold: Float = 3.5f,
        maxStars: Int = 120,
        rowStride: Int = width
    ): List<StarPoint> {
        if (isNativeLoaded) {
            try {
                val flat = nativeDetectStars(grayData, width, height, threshold, maxStars, rowStride)
                if (flat != null && flat.isNotEmpty()) {
                    val count = flat.size / 3
                    val stars = ArrayList<StarPoint>(count)
                    for (i in 0 until count) {
                        stars.add(
                            StarPoint(
                                x = flat[i * 3 + 0],
                                y = flat[i * 3 + 1],
                                brightness = flat[i * 3 + 2]
                            )
                        )
                    }
                    return stars
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in nativeDetectStars, falling back to Kotlin", e)
            }
        }

        return detectStarsKotlin(grayData, width, height, threshold, maxStars, rowStride)
    }

    fun computeAlignment(
        curStars: List<StarPoint>,
        imageWidth: Float = 1920f,
        imageHeight: Float = 1080f
    ): HomographyMatrix? {
        if (referenceStars.size < 3 || curStars.size < 3) {
            return null
        }

        if (isNativeLoaded) {
            try {
                val refFlat = FloatArray(referenceStars.size * 3)
                for (i in referenceStars.indices) {
                    refFlat[i * 3 + 0] = referenceStars[i].x
                    refFlat[i * 3 + 1] = referenceStars[i].y
                    refFlat[i * 3 + 2] = referenceStars[i].brightness
                }

                val curFlat = FloatArray(curStars.size * 3)
                for (i in curStars.indices) {
                    curFlat[i * 3 + 0] = curStars[i].x
                    curFlat[i * 3 + 1] = curStars[i].y
                    curFlat[i * 3 + 2] = curStars[i].brightness
                }

                val matrix = nativeComputeHomography(refFlat, curFlat, 3.0f, imageWidth, imageHeight)
                if (matrix != null && matrix.size == 9) {
                    return HomographyMatrix(matrix)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in nativeComputeHomography", e)
            }
        }

        return computeAlignmentKotlin(referenceStars, curStars, imageWidth, imageHeight)
    }

    // Pure Kotlin fallback detector for fast unit testing & systems without NDK built yet
    private fun detectStarsKotlin(
        grayData: ByteArray,
        width: Int,
        height: Int,
        thresholdSigma: Float,
        maxStars: Int,
        rowStride: Int = width
    ): List<StarPoint> {
        val stars = mutableListOf<StarPoint>()
        if (grayData.isEmpty() || width < 20 || height < 20) return stars

        val stride = if (rowStride > 0) rowStride else width
        var sum = 0.0
        var count = 0
        val step = 16
        for (y in step until height - step step step) {
            val rowOffset = y * stride
            for (x in step until width - step step step) {
                val v = grayData[rowOffset + x].toInt() and 0xFF
                sum += v
                count++
            }
        }
        val mean = if (count > 0) sum / count else 20.0
        val thresh = (mean + thresholdSigma * 10.0).coerceAtLeast(25.0)

        for (y in 4 until height - 4 step 2) {
            val row = y * stride
            for (x in 4 until width - 4 step 2) {
                val v = grayData[row + x].toInt() and 0xFF
                if (v <= thresh) continue

                // Check 8-neighbor local peak to avoid clustered points from same star/noise
                var isPeak = true
                for (dy in -1..1) {
                    val nRow = (y + dy) * stride
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nv = grayData[nRow + x + dx].toInt() and 0xFF
                        if (nv >= v) {
                            isPeak = false
                            break
                        }
                    }
                    if (!isPeak) break
                }

                if (isPeak) {
                    stars.add(StarPoint(x.toFloat(), y.toFloat(), v.toFloat()))
                    if (stars.size >= maxStars * 2) break
                }
            }
        }

        stars.sortByDescending { it.brightness }
        return if (stars.size > maxStars) stars.subList(0, maxStars) else stars
    }

    // Pure Kotlin fallback similarity solver (UV Normalized)
    private fun computeAlignmentKotlin(
        ref: List<StarPoint>,
        cur: List<StarPoint>,
        imageWidth: Float,
        imageHeight: Float
    ): HomographyMatrix? {
        if (ref.size < 3 || cur.size < 3) return null

        var refCx = 0f
        var refCy = 0f
        val nRef = minOf(ref.size, 15)
        for (i in 0 until nRef) {
            refCx += ref[i].x
            refCy += ref[i].y
        }
        refCx /= nRef
        refCy /= nRef

        var curCx = 0f
        var curCy = 0f
        val nCur = minOf(cur.size, 15)
        for (i in 0 until nCur) {
            curCx += cur[i].x
            curCy += cur[i].y
        }
        curCx /= nCur
        curCy /= nCur

        val dx = refCx - curCx
        val dy = refCy - curCy

        if (abs(dx) > 200f || abs(dy) > 200f) return null // Excessive movement

        // Rigid translation in UV coordinates mapping Ref -> Cur
        val txUv = -dx / imageWidth
        val tyUv = -dy / imageHeight

        return HomographyMatrix(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                txUv, tyUv, 1f
            )
        )
    }

    fun convertYuvToRgba(
        yBuf: java.nio.ByteBuffer,
        yRowStride: Int,
        uBuf: java.nio.ByteBuffer,
        uRowStride: Int,
        uPixelStride: Int,
        vBuf: java.nio.ByteBuffer,
        vRowStride: Int,
        vPixelStride: Int,
        rgbaBuf: java.nio.ByteBuffer,
        width: Int,
        height: Int
    ): Boolean {
        if (isNativeLoaded) {
            try {
                nativeYuvToRgba(
                    yBuf, yRowStride,
                    uBuf, uRowStride, uPixelStride,
                    vBuf, vRowStride, vPixelStride,
                    rgbaBuf, width, height
                )
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Native YUV to RGBA conversion failed, using fallback", e)
            }
        }
        return false
    }

    // Native JNI functions
    private external fun nativeDetectStars(
        grayData: ByteArray,
        width: Int,
        height: Int,
        threshold: Float,
        maxStars: Int,
        rowStride: Int
    ): FloatArray?

    private external fun nativeComputeHomography(
        refStarsFlat: FloatArray,
        curStarsFlat: FloatArray,
        tolerance: Float,
        imageWidth: Float,
        imageHeight: Float
    ): FloatArray?

    private external fun nativeYuvToRgba(
        yBuf: java.nio.ByteBuffer,
        yRowStride: Int,
        uBuf: java.nio.ByteBuffer,
        uRowStride: Int,
        uPixelStride: Int,
        vBuf: java.nio.ByteBuffer,
        vRowStride: Int,
        vPixelStride: Int,
        rgbaBuf: java.nio.ByteBuffer,
        width: Int,
        height: Int
    )
}

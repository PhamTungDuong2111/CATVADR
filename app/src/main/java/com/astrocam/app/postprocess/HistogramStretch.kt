package com.astrocam.app.postprocess

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

object HistogramStretch {

    data class StretchParams(
        val midtone: Float = 0.15f,
        val blackPoint: Float = 0.02f,
        val whitePoint: Float = 1.0f
    )

    /**
     * Automatically computes optimal midtone and black point from image median
     * using the PixInsight Auto-Stretch algorithm.
     */
    fun calculateAutoStretch(bitmap: Bitmap): StretchParams {
        val width = bitmap.width
        val height = bitmap.height
        val step = max(4, width / 64)

        val pixels = IntArray(width)
        val samples = mutableListOf<Float>()
        for (y in step until height - step step step) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in step until width - step step step) {
                val pixel = pixels[x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                samples.add(lum)
            }
        }

        if (samples.isEmpty()) return StretchParams()

        samples.sort()
        val median = samples[samples.size / 2]

        // Target midtone for astrophotography (usually 0.25 on stretched image)
        val targetBackground = 0.25f
        val blackPoint = max(0.0f, median - 0.04f)

        // MTF formula inverted: m = (target - 1) * median / (target * (median - 2) + 1)
        val c = max(0.001f, median - blackPoint)
        val midtone = ((targetBackground - 1f) * c / (targetBackground * (c - 2f) + 1f)).coerceIn(0.02f, 0.5f)

        return StretchParams(
            midtone = midtone,
            blackPoint = blackPoint,
            whitePoint = 1.0f
        )
    }

    /**
     * Applies non-linear MTF stretch to a Bitmap using a 256-entry lookup table for high performance (< 10ms).
     */
    fun applyStretch(source: Bitmap, params: StretchParams): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val bp = params.blackPoint
        val m = params.midtone
        val invBp = if (bp < 0.999f) 1.0f / (1.0f - bp) else 1.0f

        // Precompute 256-entry LUT
        val lut = ByteArray(256)
        for (i in 0..255) {
            val v = i / 255.0f
            val norm = max(0.0f, (v - bp) * invBp)
            val stretched = mtf(norm, m).coerceIn(0.0f, 1.0f)
            lut[i] = (stretched * 255.0f).toInt().toByte()
        }

        for (i in 0 until (width * height)) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val newR = lut[r].toInt() and 0xFF
            val newG = lut[g].toInt() and 0xFF
            val newB = lut[b].toInt() and 0xFF

            pixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * PixInsight Midtone Transfer Function
     */
    fun mtf(x: Float, m: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val denom = ((2f * m - 1f) * x) - m
        if (kotlin.math.abs(denom) < 0.00001f) return x
        return ((m - 1f) * x) / denom
    }
}

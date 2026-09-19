package com.astrocam.app.sim

import android.graphics.*
import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class SkySimulator(val width: Int = 960, val height: Int = 540) {

    private val random = Random(42)
    private var rotationAngle = 0f

    // Celestial coordinates of 70 stars (X, Y relative to center, brightness, radius)
    private class SimStar(val relX: Float, val relY: Float, val brightness: Int, val radius: Float)
    private val stars = ArrayList<SimStar>()

    init {
        // Generate random star catalog
        for (i in 0 until 80) {
            val dist = random.nextFloat() * (height * 0.7f)
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val relX = cos(angle) * dist
            val relY = sin(angle) * dist
            val brightness = 140 + random.nextInt(115) // 140 - 255
            val radius = 1.0f + random.nextFloat() * 2.2f
            stars.add(SimStar(relX, relY, brightness, radius))
        }
    }

    /**
     * Generates a realistic night sky frame with rotating stars and synthetic sensor noise
     */
    fun generateNextFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Dark sky background
        canvas.drawColor(Color.rgb(6, 9, 16))

        // 2. Subtle Milky Way nebula glow
        val nebulaPaint = Paint().apply {
            isAntiAlias = true
            val shader = LinearGradient(
                width * 0.2f, 0f, width * 0.8f, height.toFloat(),
                intArrayOf(Color.argb(18, 50, 80, 140), Color.argb(45, 120, 90, 180), Color.argb(12, 40, 70, 120)),
                floatArrayOf(0.1f, 0.5f, 0.9f),
                Shader.TileMode.CLAMP
            )
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), nebulaPaint)

        // 3. Draw rotating stars around celestial center
        val centerX = width / 2f
        val centerY = height / 2f
        rotationAngle += 0.008f // Earth rotation step

        val cosA = cos(rotationAngle)
        val sinA = sin(rotationAngle)

        val starPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        for (s in stars) {
            val rotatedX = centerX + (s.relX * cosA - s.relY * sinA)
            val rotatedY = centerY + (s.relX * sinA + s.relY * cosA)

            if (rotatedX in 0f..width.toFloat() && rotatedY in 0f..height.toFloat()) {
                // Glow halo
                starPaint.color = Color.argb(s.brightness / 3, 200, 220, 255)
                canvas.drawCircle(rotatedX, rotatedY, s.radius * 2.2f, starPaint)

                // Core star
                starPaint.color = Color.argb(s.brightness, 255, 255, 255)
                canvas.drawCircle(rotatedX, rotatedY, s.radius, starPaint)
            }
        }

        // 4. Add sensor shot noise (high ISO grain)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until (width * height) step 7) {
            val noise = (random.nextGaussian() * 14).toInt()
            if (noise > 0) {
                val p = pixels[i]
                val r = ((Color.red(p) + noise).coerceAtMost(255))
                val g = ((Color.green(p) + noise).coerceAtMost(255))
                val b = ((Color.blue(p) + noise).coerceAtMost(255))
                pixels[i] = Color.rgb(r, g, b)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return bitmap
    }
}

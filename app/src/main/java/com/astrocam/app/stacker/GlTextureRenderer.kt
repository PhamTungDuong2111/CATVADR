package com.astrocam.app.stacker

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import com.astrocam.app.alignment.HomographyMatrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GlTextureRenderer(private val context: Context) {
    companion object {
        private const val TAG = "GlTextureRenderer"

        // Fullscreen quad vertices and UV coordinates
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f,  0.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 1.0f,
            -1.0f,  1.0f,  0.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 0.0f
        )
    }

    private var vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    var displayRotation = 90
        private set
    var isDisplayMirrored = false
        private set

    private fun createDisplayBuffer(rotation: Int, isMirrored: Boolean): FloatBuffer {
        val uvs = when (rotation) {
            90 -> if (!isMirrored) {
                // Upright 90° Clockwise for standard rear camera
                floatArrayOf(1f, 1f,   1f, 0f,   0f, 1f,   0f, 0f)
            } else {
                // Mirrored (front camera)
                floatArrayOf(1f, 0f,   1f, 1f,   0f, 0f,   0f, 1f)
            }
            270 -> if (!isMirrored) {
                floatArrayOf(0f, 0f,   0f, 1f,   1f, 0f,   1f, 1f)
            } else {
                floatArrayOf(0f, 1f,   0f, 0f,   1f, 1f,   1f, 0f)
            }
            180 -> if (!isMirrored) {
                floatArrayOf(1f, 0f,   0f, 0f,   1f, 1f,   0f, 1f)
            } else {
                floatArrayOf(0f, 0f,   1f, 0f,   0f, 1f,   1f, 1f)
            }
            else -> if (!isMirrored) {
                floatArrayOf(0f, 1f,   1f, 1f,   0f, 0f,   1f, 0f)
            } else {
                floatArrayOf(1f, 1f,   0f, 1f,   1f, 0f,   0f, 0f)
            }
        }
        val data = floatArrayOf(
            -1.0f, -1.0f, uvs[0], uvs[1],
             1.0f, -1.0f, uvs[2], uvs[3],
            -1.0f,  1.0f, uvs[4], uvs[5],
             1.0f,  1.0f, uvs[6], uvs[7]
        )
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private var displayVertexBuffer: FloatBuffer = createDisplayBuffer(displayRotation, isDisplayMirrored)

    fun setDisplayOrientation(rotationDegrees: Int, isMirrored: Boolean) {
        displayRotation = rotationDegrees
        isDisplayMirrored = isMirrored
        displayVertexBuffer = createDisplayBuffer(rotationDegrees, isMirrored)
    }

    // Ping-pong FBOs for accumulation
    private val fbos = IntArray(2)
    private val fboTextures = IntArray(2)
    private var pingPongIndex = 0

    // Camera frame texture
    private val inputTexture = IntArray(1)

    // Shader Programs
    private var warpAccumulateProgram = 0
    private var starTrailsProgram = 0
    private var mtfDisplayProgram = 0

    var frameWidth = 1920
        private set
    var frameHeight = 1080
        private set

    // Offscreen export FBO
    private val exportFbo = IntArray(1)
    private val exportTexture = IntArray(1)

    fun initialize(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height

        // Clean up previous resources if re-initializing
        if (fbos[0] != 0) {
            GLES30.glDeleteFramebuffers(2, fbos, 0)
            fbos[0] = 0
            fbos[1] = 0
        }
        if (fboTextures[0] != 0) {
            GLES30.glDeleteTextures(2, fboTextures, 0)
            fboTextures[0] = 0
            fboTextures[1] = 0
        }
        if (inputTexture[0] != 0) {
            GLES30.glDeleteTextures(1, inputTexture, 0)
            inputTexture[0] = 0
        }
        if (exportFbo[0] != 0) {
            GLES30.glDeleteFramebuffers(1, exportFbo, 0)
            exportFbo[0] = 0
        }
        if (exportTexture[0] != 0) {
            GLES30.glDeleteTextures(1, exportTexture, 0)
            exportTexture[0] = 0
        }

        // 1. Create Ping-Pong FBOs
        GLES30.glGenFramebuffers(2, fbos, 0)
        GLES30.glGenTextures(2, fboTextures, 0)

        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextures[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, fboTextures[i], 0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer $i incomplete: $status")
            }
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // 2. Create Input Texture
        GLES30.glGenTextures(1, inputTexture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // 3. Compile Shaders (if not already compiled)
        if (warpAccumulateProgram == 0) {
            warpAccumulateProgram = buildProgram(
                loadShaderAsset("shaders/warp_transform.vert"),
                loadShaderAsset("shaders/live_stack_accumulate.frag")
            )
        }

        if (starTrailsProgram == 0) {
            starTrailsProgram = buildProgram(
                loadShaderAsset("shaders/passthrough.vert"),
                loadShaderAsset("shaders/star_trails_max.frag")
            )
        }

        if (mtfDisplayProgram == 0) {
            mtfDisplayProgram = buildProgram(
                loadShaderAsset("shaders/passthrough.vert"),
                loadShaderAsset("shaders/mtf_stretch_tone_map.frag")
            )
        }
    }

    fun resetStack() {
        pingPongIndex = 0
        for (i in 0..1) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[i])
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun ensureDimensions(width: Int, height: Int) {
        if (frameWidth != width || frameHeight != height) {
            initialize(width, height)
        }
    }

    fun uploadInputFrame(rgbaBytes: ByteBuffer, width: Int, height: Int) {
        ensureDimensions(width, height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgbaBytes
        )
    }

    fun renderStack(
        mode: StackingMode,
        homography: HomographyMatrix,
        frameCount: Int
    ) {
        val srcFboIndex = pingPongIndex
        val dstFboIndex = 1 - pingPongIndex

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[dstFboIndex])
        GLES30.glViewport(0, 0, frameWidth, frameHeight)

        val program = when (mode) {
            StackingMode.DEEP_SKY_AVERAGE, StackingMode.KAPPA_SIGMA -> warpAccumulateProgram
            StackingMode.STAR_TRAILS -> starTrailsProgram
        }

        GLES30.glUseProgram(program)

        // Set Vertex Attributes
        vertexBuffer.position(0)
        val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

        vertexBuffer.position(2)
        val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

        // Textures
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture[0])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uCurrentFrame"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextures[srcFboIndex])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uAccumulatorTexture"), 1)

        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uFrameCount"), frameCount)

        if (mode == StackingMode.DEEP_SKY_AVERAGE || mode == StackingMode.KAPPA_SIGMA) {
            val modeId = if (mode == StackingMode.KAPPA_SIGMA) 1 else 0
            val modeLoc = GLES30.glGetUniformLocation(program, "uStackingMode")
            if (modeLoc >= 0) GLES30.glUniform1i(modeLoc, modeId)
        }

        if (mode.requiresAlignment) {
            val matrixLoc = GLES30.glGetUniformLocation(program, "uHomographyMatrix")
            GLES30.glUniformMatrix3fv(matrixLoc, 1, false, homography.values, 0)
        }

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        pingPongIndex = dstFboIndex
    }

    fun renderToScreen(
        viewportWidth: Int,
        viewportHeight: Int,
        midtone: Float = 0.12f,
        blackPoint: Float = 0.02f,
        saturation: Float = 1.3f,
        useLiveInput: Boolean = false
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val isRotated = (displayRotation == 90 || displayRotation == 270)
        val effectiveContentWidth = if (isRotated) frameHeight else frameWidth
        val effectiveContentHeight = if (isRotated) frameWidth else frameHeight

        // Compute aspect ratio of rotated frame
        val contentAspect = effectiveContentWidth.toFloat() / effectiveContentHeight.toFloat()
        val viewAspect = viewportWidth.toFloat() / viewportHeight.toFloat()

        val vpW: Int
        val vpH: Int
        val vpX: Int
        val vpY: Int

        // Aspect fit to maintain correct geometry (stars and objects don't stretch)
        if (viewAspect > contentAspect) {
            vpH = viewportHeight
            vpW = (viewportHeight * contentAspect).toInt()
            vpX = (viewportWidth - vpW) / 2
            vpY = 0
        } else {
            vpW = viewportWidth
            vpH = (viewportWidth / contentAspect).toInt()
            vpX = 0
            vpY = (viewportHeight - vpH) / 2
        }

        GLES30.glViewport(vpX, vpY, vpW, vpH)

        GLES30.glUseProgram(mtfDisplayProgram)

        displayVertexBuffer.position(0)
        val posLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, displayVertexBuffer)

        displayVertexBuffer.position(2)
        val texLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, displayVertexBuffer)

        val textureToDisplay = if (useLiveInput) inputTexture[0] else fboTextures[pingPongIndex]

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureToDisplay)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uAccumulatedTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uApplyStretch"), if (useLiveInput) 0 else 1)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uMidtone"), midtone)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uBlackPoint"), blackPoint)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uSaturation"), saturation)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    fun readCurrentStackedBitmap(
        midtone: Float = 0.15f,
        blackPoint: Float = 0.02f,
        saturation: Float = 1.3f,
        applyStretch: Boolean = true,
        rotateUpright: Boolean = true
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4)

        if (applyStretch) {
            // Lazy initialize export FBO
            if (exportFbo[0] == 0) {
                GLES30.glGenFramebuffers(1, exportFbo, 0)
                GLES30.glGenTextures(1, exportTexture, 0)

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, exportTexture[0])
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, frameWidth, frameHeight, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
                )
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, exportFbo[0])
                GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, exportTexture[0], 0
                )
            } else {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, exportFbo[0])
            }

            GLES30.glViewport(0, 0, frameWidth, frameHeight)
            GLES30.glUseProgram(mtfDisplayProgram)

            vertexBuffer.position(0)
            val posLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aPosition")
            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

            vertexBuffer.position(2)
            val texLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aTexCoord")
            GLES30.glEnableVertexAttribArray(texLoc)
            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextures[pingPongIndex])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uAccumulatedTexture"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uApplyStretch"), 1)

            GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uMidtone"), midtone)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uBlackPoint"), blackPoint)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uSaturation"), saturation)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            GLES30.glDisableVertexAttribArray(posLoc)
            GLES30.glDisableVertexAttribArray(texLoc)

            GLES30.glReadPixels(0, 0, frameWidth, frameHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbos[pingPongIndex])
            GLES30.glReadPixels(0, 0, frameWidth, frameHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }

        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        // Correct for OpenGL coordinate system and rotate upright
        val matrix = android.graphics.Matrix().apply {
            preScale(1.0f, -1.0f)
            if (rotateUpright && displayRotation != 0) {
                postRotate(displayRotation.toFloat())
                if (isDisplayMirrored) {
                    postScale(-1f, 1f)
                }
            }
        }
        val corrected = Bitmap.createBitmap(bitmap, 0, 0, frameWidth, frameHeight, matrix, true)
        bitmap.recycle()
        return corrected
    }

    fun readLivePreviewBitmap(
        midtone: Float = 0.15f,
        blackPoint: Float = 0.02f,
        saturation: Float = 1.0f,
        rotateUpright: Boolean = true
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4)

        if (exportFbo[0] == 0) {
            GLES30.glGenFramebuffers(1, exportFbo, 0)
            GLES30.glGenTextures(1, exportTexture, 0)

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, exportTexture[0])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, frameWidth, frameHeight, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, exportFbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, exportTexture[0], 0
            )
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, exportFbo[0])
        }

        GLES30.glViewport(0, 0, frameWidth, frameHeight)
        GLES30.glUseProgram(mtfDisplayProgram)

        vertexBuffer.position(0)
        val posLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

        vertexBuffer.position(2)
        val texLoc = GLES30.glGetAttribLocation(mtfDisplayProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture[0])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uAccumulatedTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mtfDisplayProgram, "uApplyStretch"), 0)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uMidtone"), midtone)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uBlackPoint"), blackPoint)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mtfDisplayProgram, "uSaturation"), saturation)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)

        GLES30.glReadPixels(0, 0, frameWidth, frameHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val matrix = android.graphics.Matrix().apply {
            preScale(1.0f, -1.0f)
            if (rotateUpright && displayRotation != 0) {
                postRotate(displayRotation.toFloat())
                if (isDisplayMirrored) {
                    postScale(-1f, 1f)
                }
            }
        }
        val corrected = Bitmap.createBitmap(bitmap, 0, 0, frameWidth, frameHeight, matrix, true)
        bitmap.recycle()
        return corrected
    }

    private fun loadShaderAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vert)
        GLES30.glAttachShader(program, frag)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation error: $log")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}

package com.astrocam.app.ui

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.astrocam.app.stacker.GlTextureRenderer
import com.astrocam.app.stacker.StackingPipeline
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun CameraViewfinder(
    pipeline: StackingPipeline,
    midtone: Float,
    blackPoint: Float,
    rotationDegrees: Int = 90,
    isMirrored: Boolean = false,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            AstroGLSurfaceView(ctx, pipeline)
        },
        update = { view ->
            view.updateStretch(midtone, blackPoint)
            view.updateOrientation(rotationDegrees, isMirrored)
        },
        modifier = modifier.fillMaxSize()
    )
}

private class AstroGLSurfaceView(
    context: Context,
    private val pipeline: StackingPipeline
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private var rendererInstance: GlTextureRenderer? = null
    private var currentMidtone = 0.15f
    private var currentBlackPoint = 0.02f
    private var currentRotation = 90
    private var currentMirrored = false
    private var viewWidth = 1080
    private var viewHeight = 1920

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun updateStretch(midtone: Float, blackPoint: Float) {
        currentMidtone = midtone
        currentBlackPoint = blackPoint
    }

    fun updateOrientation(rotation: Int, isMirrored: Boolean) {
        currentRotation = rotation
        currentMirrored = isMirrored
        queueEvent {
            rendererInstance?.setDisplayOrientation(rotation, isMirrored)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val r = GlTextureRenderer(context)
        r.initialize(1920, 1080)
        r.setDisplayOrientation(currentRotation, currentMirrored)
        rendererInstance = r
        pipeline.onGlInitialized(this, r)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val r = rendererInstance ?: return
        val isStacking = pipeline.isStacking()
        val hasStacked = pipeline.stackedFrames.value > 0
        val useLive = !isStacking && !hasStacked

        r.renderToScreen(
            viewportWidth = viewWidth,
            viewportHeight = viewHeight,
            midtone = currentMidtone,
            blackPoint = currentBlackPoint,
            saturation = 1.3f,
            useLiveInput = useLive
        )
    }
}

package com.astrocam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.astrocam.app.camera.Camera2Controller
import com.astrocam.app.sensor.AstroOrientationSensor
import com.astrocam.app.stacker.StackingPipeline
import com.astrocam.app.ui.AstroAppScreen
import com.astrocam.app.ui.theme.AstroCamTheme

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: Camera2Controller
    private lateinit var pipeline: StackingPipeline
    private lateinit var orientationSensor: AstroOrientationSensor

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            cameraController.openCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during camera usage and long night sky exposures
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pipeline = StackingPipeline(this)
        orientationSensor = AstroOrientationSensor(this)
        cameraController = Camera2Controller(this) { image ->
            pipeline.processFrame(image, cameraController.state.value.exposureTimeSeconds)
        }

        setContent {
            AstroCamTheme {
                AstroAppScreen(
                    cameraController = cameraController,
                    pipeline = pipeline,
                    orientationSensor = orientationSensor
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationSensor.start()
        val neededPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missingPermissions = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            cameraController.openCamera()
        } else {
            requestPermissionLauncher.launch(neededPermissions)
        }
    }

    override fun onPause() {
        super.onPause()
        orientationSensor.stop()
        cameraController.closeCamera()
        pipeline.stopStacking()
    }
}

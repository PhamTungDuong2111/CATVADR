package com.astrocam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.astrocam.app.astro.AstroLocationManager
import com.astrocam.app.camera.Camera2Controller
import com.astrocam.app.sensor.AstroOrientationSensor
import com.astrocam.app.stacker.StackingPipeline
import com.astrocam.app.ui.AstroAppScreen
import com.astrocam.app.ui.theme.AstroCamTheme

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: Camera2Controller
    private lateinit var pipeline: StackingPipeline
    private lateinit var orientationSensor: AstroOrientationSensor
    private lateinit var locationManager: AstroLocationManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            cameraController.openCamera()
        }
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationManager.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during camera usage and long night sky exposures
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pipeline = StackingPipeline(this)
        orientationSensor = AstroOrientationSensor(this)
        locationManager = AstroLocationManager(this)
        cameraController = Camera2Controller(this) { image ->
            pipeline.processFrame(image, cameraController.state.value.exposureTimeSeconds)
        }

        setContent {
            AstroCamTheme {
                AstroAppScreen(
                    cameraController = cameraController,
                    pipeline = pipeline,
                    orientationSensor = orientationSensor,
                    locationManager = locationManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationSensor.start()
        locationManager.start()
        val neededPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
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
        locationManager.stop()
        cameraController.closeCamera()
        pipeline.stopStacking()
    }
}

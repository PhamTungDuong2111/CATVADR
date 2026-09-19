package com.astrocam.app.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrocam.app.camera.AppCameraMode
import com.astrocam.app.camera.Camera2Controller
import com.astrocam.app.camera.FlashMode
import com.astrocam.app.postprocess.ImageExporter
import com.astrocam.app.sensor.AstroOrientationSensor
import com.astrocam.app.sensor.OrientationData
import com.astrocam.app.sim.SkySimulator
import com.astrocam.app.stacker.StackingMode
import com.astrocam.app.stacker.StackingPipeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AstroAppScreen(
    cameraController: Camera2Controller,
    pipeline: StackingPipeline,
    orientationSensor: AstroOrientationSensor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val cameraState by cameraController.state.collectAsState()
    val stackedFrames by pipeline.stackedFrames.collectAsState()
    val rejectedFrames by pipeline.rejectedFrames.collectAsState()
    val totalExposure by pipeline.totalExposureSeconds.collectAsState()
    val isCapturing by pipeline.isStacking.collectAsState()
    val detectedStars by pipeline.detectedStarCount.collectAsState()
    val histogramData by pipeline.histogramData.collectAsState()
    val orientation by orientationSensor.orientation.collectAsState()

    var isRedMode by remember { mutableStateOf(false) }
    var showLeveler by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(false) }
    var isSimMode by remember { mutableStateOf(false) }

    var selectedMode by remember { mutableStateOf(StackingMode.DEEP_SKY_AVERAGE) }
    var targetFrames by remember { mutableStateOf(30) }
    var midtoneStretch by remember { mutableStateOf(0.18f) }
    var blackPoint by remember { mutableStateOf(0.015f) }

    // Last captured photo thumbnail
    var lastThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    // Shutter flash effect
    var isShutterFlashing by remember { mutableStateOf(false) }

    // Dialog preview state
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Theme colors
    val accentColor by animateColorAsState(
        targetValue = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF),
        animationSpec = tween(350), label = "ScreenAccent"
    )
    val bgOverlay = if (isRedMode) Color(0xCC150306) else Color(0xAA070B14)

    LaunchedEffect(isShutterFlashing) {
        if (isShutterFlashing) {
            delay(120)
            isShutterFlashing = false
        }
    }

    // Shutter pulse animation during capture
    val infiniteTransition = rememberInfiniteTransition(label = "ShutterPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val videoBlinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VideoBlink"
    )

    // Simulation Coroutine
    LaunchedEffect(isSimMode) {
        if (isSimMode) {
            val sim = SkySimulator()
            while (isActive && isSimMode) {
                val frame = sim.generateNextFrame()
                pipeline.processBitmap(frame, cameraState.exposureTimeSeconds)
                delay(500)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Live Viewfinder with GL Texture (Rotated & Scaled to upright portrait)
        CameraViewfinder(
            pipeline = pipeline,
            midtone = midtoneStretch,
            blackPoint = blackPoint,
            rotationDegrees = cameraController.getSensorOrientation(),
            isMirrored = cameraState.isFrontCamera,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Artificial Horizon Leveler & Compass Reticle Overlay
        if (showLeveler) {
            LevelerOverlay(
                orientation = orientation,
                showGrid = true,
                isRedMode = isRedMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Shutter Flash Effect
        if (isShutterFlashing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.75f))
            )
        }

        // 4. TOP HUD BAR: Minimalist & Modern Glass Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(bgOverlay)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Flash / Torch toggle + Leveler toggle + Red Mode
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle Button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            cameraController.toggleFlash()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        val flashIcon = when (cameraState.flashMode) {
                            FlashMode.OFF -> "⚡\u200D✕"
                            FlashMode.ON, FlashMode.TORCH -> "⚡"
                            FlashMode.AUTO -> "⚡A"
                        }
                        val flashColor = when (cameraState.flashMode) {
                            FlashMode.OFF -> Color.Gray
                            FlashMode.ON, FlashMode.TORCH -> Color(0xFFFFD600)
                            FlashMode.AUTO -> Color.White
                        }
                        Text(
                            text = flashIcon,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = flashColor
                        )
                    }

                    // Leveler & Grid Toggle
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLeveler = !showLeveler
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "📐",
                            fontSize = 15.sp,
                            color = if (showLeveler) accentColor else Color.Gray
                        )
                    }

                    // Red Night Mode Toggle
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isRedMode = !isRedMode
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = if (isRedMode) "🔴" else "⚪",
                            fontSize = 14.sp
                        )
                    }
                }

                // Center: Status Badge (Video Recording Timer / Stacking Progress / Mode Badge)
                Box(contentAlignment = Alignment.Center) {
                    when {
                        cameraState.isRecordingVideo -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xD0B71C1C))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(videoBlinkAlpha)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                val mins = cameraState.recordingDurationSeconds / 60
                                val secs = cameraState.recordingDurationSeconds % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        isCapturing -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.25f))
                                    .border(1.dp, accentColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🌌 $stackedFrames/$targetFrames • ${totalExposure.toInt()}s",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = when (cameraState.mode) {
                                    AppCameraMode.PHOTO -> "ẢNH • TỰ ĐỘNG"
                                    AppCameraMode.VIDEO -> "VIDEO • FHD"
                                    AppCameraMode.ASTRO -> "THIÊN VĂN"
                                    AppCameraMode.PRO -> "CHUYÊN NGHIỆP"
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Right: Histogram Toggle
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showHistogram = !showHistogram
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "📊",
                        fontSize = 15.sp,
                        color = if (showHistogram) accentColor else Color.Gray
                    )
                }
            }

            // Floating Mini Histogram (collapsible)
            AnimatedVisibility(
                visible = showHistogram,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    AstroHistogramView(
                        histogramData = histogramData,
                        isRedMode = isRedMode
                    )
                }
            }

            // In Astro mode: Star Tracker Badge & Rejection Indicator
            if (cameraState.mode == AppCameraMode.ASTRO) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgOverlay)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isSimMode) "🧪 Giả lập bầu trời" else "⭐ $detectedStars sao nhận diện",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (detectedStars >= 4) (if (isRedMode) Color(0xFFFF5252) else Color(0xFF00E5FF)) else Color.Gray
                        )
                    }

                    if (rejectedFrames > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xCC3E2723))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ Đã lọc: $rejectedFrames",
                                fontSize = 10.sp,
                                color = Color(0xFFFFB74D)
                            )
                        }
                    }
                }
            }
        }

        // 5. BOTTOM CAMERA INTERFACE
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Astro Controls Panel (Only visible in ASTRO or PRO mode)
            if (cameraState.mode == AppCameraMode.ASTRO || cameraState.mode == AppCameraMode.PRO) {
                AstroControlPanel(
                    iso = cameraState.iso,
                    exposureSeconds = cameraState.exposureTimeSeconds,
                    isInfinityLocked = cameraState.isInfinityLocked,
                    selectedMode = selectedMode,
                    isSimMode = isSimMode,
                    isRedMode = isRedMode,
                    midtoneStretch = midtoneStretch,
                    blackPoint = blackPoint,
                    targetFrames = targetFrames,
                    isCapturing = isCapturing,
                    onIsoChanged = { newIso -> cameraController.setIso(newIso) },
                    onExposureChanged = { newExp -> cameraController.setExposureTime(newExp) },
                    onInfinityToggle = { isLock -> cameraController.setInfinityFocusLock(isLock) },
                    onModeSelected = { mode -> selectedMode = mode },
                    onSimModeToggle = { sim -> isSimMode = sim },
                    onStretchChanged = { midtoneStretch = it },
                    onBlackPointChanged = { blackPoint = it },
                    onTargetFramesChanged = { targetFrames = it }
                )
            }

            // Mode Selector Carousel (Standard Android Camera Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCameraMode.values().forEach { mode ->
                    val isSelected = (cameraState.mode == mode)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .clickable(enabled = !isCapturing && !cameraState.isRecordingVideo) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                cameraController.setCameraMode(mode)
                            }
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = if (isSelected) 14.sp else 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) (if (isRedMode) Color(0xFFFF2A4B) else Color(0xFFFFD600)) else Color.White.copy(alpha = 0.6f)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isRedMode) Color(0xFFFF2A4B) else Color(0xFFFFD600))
                            )
                        }
                    }
                }
            }

            // Bottom Shutter Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isRedMode) Color(0xFF100204) else Color(0xEE080C14))
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 28.dp)
            ) {
                // LEFT: Gallery Thumbnail / Quick Preview
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (lastThumbnail != null) {
                                previewBitmap = lastThumbnail
                            } else if (stackedFrames > 0 && !isCapturing) {
                                coroutineScope.launch {
                                    val bmp = pipeline.exportCurrentStackedBitmap()
                                    if (bmp != null) {
                                        previewBitmap = bmp
                                    } else {
                                        Toast.makeText(context, "Chưa có ảnh hoàn chỉnh", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Chưa có ảnh chụp nào", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastThumbnail != null) {
                        Image(
                            bitmap = lastThumbnail!!.asImageBitmap(),
                            contentDescription = "Recent photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = "🖼️", fontSize = 22.sp)
                    }
                }

                // CENTER: Mode-Adaptive Shutter Button
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    when (cameraState.mode) {
                        // --- PHOTO MODE ---
                        AppCameraMode.PHOTO -> {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, Color.White, CircleShape)
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            isShutterFlashing = true
                                            cameraController.takePhoto(pipeline) { bmp, uri ->
                                                if (bmp != null && uri != null) {
                                                    lastThumbnail = bmp
                                                    Toast.makeText(context, "✅ Đã lưu ảnh vào Thư viện!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "❌ Lỗi chụp ảnh", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                )
                            }
                        }

                        // --- VIDEO MODE ---
                        AppCameraMode.VIDEO -> {
                            val isRecording = cameraState.isRecordingVideo
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, if (isRecording) Color.Red else Color.White, CircleShape)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRecording) {
                                    // Stop Recording (Red rounded square)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Red)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                cameraController.stopRecording { uri ->
                                                    if (uri != null) {
                                                        Toast.makeText(context, "✅ Đã lưu video vào Thư viện!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "❌ Không thể lưu video", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                    )
                                } else {
                                    // Start Recording (Red circle)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935))
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                cameraController.startRecording(
                                                    onStarted = {
                                                        Toast.makeText(context, "🔴 Đang quay video...", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }

                        // --- ASTRO MODE ---
                        AppCameraMode.ASTRO -> {
                            if (isCapturing) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.35f))
                                )
                                CircularProgressIndicator(
                                    progress = { (stackedFrames.toFloat() / targetFrames).coerceIn(0f, 1f) },
                                    modifier = Modifier.size(90.dp),
                                    color = accentColor,
                                    strokeWidth = 4.dp,
                                    trackColor = Color(0x33FFFFFF)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = if (isCapturing)
                                                listOf(Color(0xFFFF334B), Color(0xFFB71C1C))
                                            else
                                                listOf(accentColor, if (isRedMode) Color(0xFFB71C1C) else Color(0xFF0072FF))
                                        )
                                    )
                                    .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        if (isCapturing) {
                                            pipeline.stopStacking()
                                            Toast.makeText(context, "Đã hoàn tất phơi sáng!", Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                delay(300)
                                                val bmp = pipeline.exportCurrentStackedBitmap()
                                                if (bmp != null) {
                                                    lastThumbnail = bmp
                                                    previewBitmap = bmp
                                                }
                                            }
                                        } else {
                                            pipeline.startStacking(selectedMode, targetFrames)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCapturing) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                    )
                                } else {
                                    Text(
                                        text = "✦",
                                        fontSize = 26.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // --- PRO MODE ---
                        AppCameraMode.PRO -> {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, Color(0xFFFFB300), CircleShape)
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFD54F))
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            isShutterFlashing = true
                                            cameraController.takePhoto(pipeline) { bmp, uri ->
                                                if (bmp != null && uri != null) {
                                                    lastThumbnail = bmp
                                                    Toast.makeText(context, "✅ Đã chụp ảnh PRO!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "PRO",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT: Camera Switcher (Flip Camera Front/Back)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable(enabled = !isCapturing && !cameraState.isRecordingVideo) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            cameraController.switchCamera()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔄", fontSize = 22.sp)
                }
            }
        }

        // 6. Result Inspector Dialog
        previewBitmap?.let { bmp ->
            ResultPreviewDialog(
                bitmap = bmp,
                stackedCount = stackedFrames,
                rejectedCount = rejectedFrames,
                totalExposureSec = totalExposure,
                modeName = selectedMode.displayName,
                isRedMode = isRedMode,
                onDismiss = { previewBitmap = null }
            )
        }
    }
}

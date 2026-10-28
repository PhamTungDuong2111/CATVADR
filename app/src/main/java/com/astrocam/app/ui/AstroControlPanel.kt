package com.astrocam.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrocam.app.stacker.StackingMode

@Composable
fun AstroControlPanel(
    iso: Int,
    exposureSeconds: Float,
    isInfinityLocked: Boolean,
    selectedMode: StackingMode,
    isSimMode: Boolean,
    isRedMode: Boolean,
    midtoneStretch: Float,
    blackPoint: Float,
    targetFrames: Int,
    isCapturing: Boolean,
    onIsoChanged: (Int) -> Unit,
    onExposureChanged: (Float) -> Unit,
    onInfinityToggle: (Boolean) -> Unit,
    onModeSelected: (StackingMode) -> Unit,
    onSimModeToggle: (Boolean) -> Unit,
    onStretchChanged: (Float) -> Unit,
    onBlackPointChanged: (Float) -> Unit,
    onTargetFramesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf<String?>("EXPOSURE") } // "EXPOSURE", "STRETCH", null

    // Drag state to collapse or expand the entire control panel,
    // leaving only the Grip Indicator visible when collapsed.
    var isPanelCollapsed by remember { mutableStateOf(false) }
    var dragAccumPx by remember { mutableStateOf(0f) }
    val collapseThresholdPx = 40f

    val accentColor by animateColorAsState(
        targetValue = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF),
        animationSpec = tween(400), label = "AccentColor"
    )
    val panelBg by animateColorAsState(
        targetValue = if (isRedMode) Color(0xF5180407) else Color(0xEE0B101D),
        animationSpec = tween(400), label = "PanelBg"
    )
    val chipBg = if (isRedMode) Color(0x33FF2A4B) else Color(0x221E293B)
    val chipBorder = if (isRedMode) Color(0x66FF2A4B) else Color(0x3338BDF8)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(panelBg)
            .border(1.dp, chipBorder.copy(alpha = 0.4f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(top = 12.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag / Grip Indicator — touch area wider than the visible bar for easier dragging.
        // Drag down (positive dragAccumPx past threshold) collapses panel below;
        // drag up (negative dragAccumPx past threshold) expands panel back.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumPx = 0f },
                        onDragEnd = {
                            when {
                                dragAccumPx > collapseThresholdPx -> isPanelCollapsed = true
                                dragAccumPx < -collapseThresholdPx -> isPanelCollapsed = false
                            }
                            dragAccumPx = 0f
                        },
                        onDragCancel = { dragAccumPx = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumPx += dragAmount
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { isPanelCollapsed = !isPanelCollapsed },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.4f))
            )
        }

        // 1. Panel Content — collapsed/expanded according to isPanelCollapsed, controlled by Grip bar above.
        AnimatedVisibility(
            visible = !isPanelCollapsed,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
        ) {
        Column {
        Spacer(modifier = Modifier.height(10.dp))

        // Astrophotography Mode Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real Modes
            val modes = listOf(
                Pair(StackingMode.DEEP_SKY_AVERAGE, "🌌 Milky Way"),
                Pair(StackingMode.KAPPA_SIGMA, "🛰️ Satellite Removal"),
                Pair(StackingMode.STAR_TRAILS, "🌠 Star Trails")
            )

            modes.forEach { (mode, label) ->
                val isSelected = (!isSimMode && mode == selectedMode)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) accentColor else chipBg)
                        .border(1.dp, if (isSelected) accentColor else chipBorder, RoundedCornerShape(20.dp))
                        .clickable(enabled = !isCapturing) {
                            onSimModeToggle(false)
                            onModeSelected(mode)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
            }

            // Sim / Demo Mode Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSimMode) (if (isRedMode) Color(0xFFFF5252) else Color(0xFFFFD600)) else chipBg)
                    .border(1.dp, if (isSimMode) Color(0xFFFFD600) else chipBorder, RoundedCornerShape(20.dp))
                    .clickable(enabled = !isCapturing) {
                        onSimModeToggle(!isSimMode)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "🧪 Virtual Sky Demo",
                    fontSize = 12.sp,
                    fontWeight = if (isSimMode) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSimMode) Color.Black else (if (isRedMode) Color(0xFFFF8A80) else Color(0xFFFFE082))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Primary Parameter Bar: FOCUS | ISO | SHUTTER | FRAMES | EXPAND
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Focus Infinity Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isInfinityLocked) accentColor else chipBg)
                    .border(1.dp, if (isInfinityLocked) accentColor else chipBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCapturing) { onInfinityToggle(!isInfinityLocked) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FOCUS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInfinityLocked) Color.Black else Color.Gray
                    )
                    Text(
                        text = if (isInfinityLocked) "∞ Infinity" else "AF Auto",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInfinityLocked) Color.Black else Color.White
                    )
                }
            }

            // Shutter Speed Quick Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (activeTab == "EXPOSURE") accentColor.copy(alpha = 0.2f) else chipBg)
                    .border(1.dp, if (activeTab == "EXPOSURE") accentColor else chipBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCapturing) {
                        activeTab = if (activeTab == "EXPOSURE") null else "EXPOSURE"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "EXPOSURE", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${exposureSeconds.toInt()}s",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            // ISO Quick Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (activeTab == "ISO") accentColor.copy(alpha = 0.2f) else chipBg)
                    .border(1.dp, if (activeTab == "ISO") accentColor else chipBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCapturing) {
                        activeTab = if (activeTab == "ISO") null else "ISO"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "ISO", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$iso",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Target Frames Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (activeTab == "FRAMES") accentColor.copy(alpha = 0.2f) else chipBg)
                    .border(1.dp, if (activeTab == "FRAMES") accentColor else chipBorder, RoundedCornerShape(12.dp))
                    .clickable(enabled = !isCapturing) {
                        activeTab = if (activeTab == "FRAMES") null else "FRAMES"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "FRAMES", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${targetFrames}x",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Live Stretch Slider Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (activeTab == "STRETCH") accentColor else chipBg)
                    .border(1.dp, if (activeTab == "STRETCH") accentColor else chipBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        activeTab = if (activeTab == "STRETCH") null else "STRETCH"
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙️ Stretch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == "STRETCH") Color.Black else accentColor
                )
            }
        }

        // 3. Sub-drawer for chosen parameter
        AnimatedVisibility(visible = activeTab != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                when (activeTab) {
                    "EXPOSURE" -> {
                        Text(
                            text = "Shutter Speed (Exposure time per frame)",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val shutterPresets = listOf(0.5f, 1f, 2f, 4f, 8f, 15f, 30f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shutterPresets.forEach { sec ->
                                val isSelected = (exposureSeconds == sec)
                                OutlinedButton(
                                    onClick = { onExposureChanged(sec) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) accentColor else Color.Transparent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "${sec.toInt()}s",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    "ISO" -> {
                        Text(
                            text = "Sensor Sensitivity (Higher ISO reveals nebula details)",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val isoPresets = listOf(400, 800, 1600, 3200, 6400, 12800)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            isoPresets.forEach { value ->
                                val isSelected = (iso == value)
                                OutlinedButton(
                                    onClick = { onIsoChanged(value) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) accentColor else Color.Transparent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "$value",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    "FRAMES" -> {
                        Text(
                            text = "Target Stacking Frames (More frames yield cleaner noise reduction)",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val framePresets = listOf(10, 20, 30, 50, 100)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            framePresets.forEach { count ->
                                val isSelected = (targetFrames == count)
                                OutlinedButton(
                                    onClick = { onTargetFramesChanged(count) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) accentColor else Color.Transparent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "${count}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    "STRETCH" -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "✨ Nebula Midtone Stretch: ${String.format(java.util.Locale.US, "%.2f", midtoneStretch)}",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                            Slider(
                                value = midtoneStretch,
                                onValueChange = onStretchChanged,
                                valueRange = 0.02f..0.50f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🌑 Light Pollution Removal (Black Point): ${String.format(java.util.Locale.US, "%.3f", blackPoint)}",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                            Slider(
                                value = blackPoint,
                                onValueChange = onBlackPointChanged,
                                valueRange = 0.00f..0.15f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                    }
                }
            }
        }
        } // Close panel content Column (mode pills + parameter bar + sub-drawer)
        } // Close AnimatedVisibility(!isPanelCollapsed)
    }
}

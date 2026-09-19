package com.astrocam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrocam.app.sensor.OrientationData
import kotlin.math.abs

@Composable
fun LevelerOverlay(
    orientation: OrientationData,
    showGrid: Boolean,
    isRedMode: Boolean,
    modifier: Modifier = Modifier
) {
    val levelColor = if (isRedMode) Color(0xFFFF5252) else Color(0xFF00E676)
    val tiltColor = if (isRedMode) Color(0x66FF1744) else Color(0x66FFFFFF)
    val gridColor = if (isRedMode) Color(0x22FF1744) else Color(0x22FFFFFF)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // 1. Optional Rule-of-Thirds Grid
            if (showGrid) {
                // Vertical lines
                drawLine(gridColor, Offset(width / 3f, 0f), Offset(width / 3f, height), 1f)
                drawLine(gridColor, Offset(width * 2f / 3f, 0f), Offset(width * 2f / 3f, height), 1f)
                // Horizontal lines
                drawLine(gridColor, Offset(0f, height / 3f), Offset(width, height / 3f), 1f)
                drawLine(gridColor, Offset(0f, height * 2f / 3f), Offset(width, height * 2f / 3f), 1f)
            }

            // 2. Center Crosshair
            val crossSize = 16.dp.toPx()
            val crossColor = if (isRedMode) Color(0x88FF1744) else Color(0x8800E5FF)
            drawLine(crossColor, Offset(centerX - crossSize, centerY), Offset(centerX + crossSize, centerY), 1.5f)
            drawLine(crossColor, Offset(centerX, centerY - crossSize), Offset(centerX, centerY + crossSize), 1.5f)

            // 3. Electronic Horizon Leveler Bar (Rotates with roll)
            val horizonColor = if (orientation.isLevel) levelColor else tiltColor
            val barLength = 70.dp.toPx()
            val centerGap = 18.dp.toPx()

            rotate(degrees = -orientation.roll, pivot = Offset(centerX, centerY)) {
                // Left wing
                drawLine(
                    color = horizonColor,
                    start = Offset(centerX - barLength, centerY),
                    end = Offset(centerX - centerGap, centerY),
                    strokeWidth = if (orientation.isLevel) 3.5f else 2f,
                    cap = StrokeCap.Round
                )
                // Right wing
                drawLine(
                    color = horizonColor,
                    start = Offset(centerX + centerGap, centerY),
                    end = Offset(centerX + barLength, centerY),
                    strokeWidth = if (orientation.isLevel) 3.5f else 2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 4. Horizon & Pitch Status Readout
        Text(
            text = String.format(
                java.util.Locale.US,
                "%s  |  Góc ngẩng: %+.1f°  |  Nghiêng: %+.1f° %s",
                orientation.cardinalDirection,
                orientation.pitch,
                orientation.roll,
                if (orientation.isLevel) "✓ CHUẨN" else ""
            ),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (orientation.isLevel) levelColor else (if (isRedMode) Color(0xCCFF6B81) else Color(0xCCFFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

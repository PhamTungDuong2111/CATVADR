package com.astrocam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun AstroHistogramView(
    histogramData: IntArray?,
    isRedMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF)
    val fillColor = if (isRedMode) Color(0x66FF1744) else Color(0x5500B0FF)
    val bgColor = Color(0xCC0A0E17)
    val borderColor = if (isRedMode) Color(0x44FF2A4B) else Color(0x3300E5FF)

    Box(
        modifier = modifier
            .width(140.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
            val width = size.width
            val height = size.height

            if (histogramData == null || histogramData.isEmpty()) {
                // Empty fallback guide lines
                drawLine(
                    color = borderColor,
                    start = Offset(0f, height * 0.5f),
                    end = Offset(width, height * 0.5f),
                    strokeWidth = 1f
                )
                return@Canvas
            }

            val bins = histogramData.size
            var maxVal = 1
            for (v in histogramData) {
                if (v > maxVal) maxVal = v
            }

            val path = Path()
            val fillPath = Path()

            val stepX = width / (bins - 1).coerceAtLeast(1)
            fillPath.moveTo(0f, height)

            for (i in 0 until bins) {
                val x = i * stepX
                val normalizedY = (histogramData[i].toFloat() / maxVal).coerceIn(0f, 1f)
                // Logarithmic scaling for astrophotography dynamic range
                val logY = (Math.log10(1.0 + normalizedY * 9.0) / Math.log10(10.0)).toFloat()
                val y = height - (logY * (height - 4f))

                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw filled gradient area
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw stroke outline
            drawPath(
                path = path,
                color = accentColor,
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
        }

        // Mini HUD Label
        Text(
            text = "HISTOGRAM",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRedMode) Color(0xCCFF6B81) else Color(0xAA00E5FF),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp)
        )
    }
}

package com.astrocam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.astrocam.app.astro.PlanetInfo

@Composable
fun PlanetDetailDialog(
    planet: PlanetInfo,
    isTargeted: Boolean,
    isRedMode: Boolean,
    onTargetToggle: (PlanetInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isRedMode) Color(0xF8180306) else Color(0xF80B1120)
    val accentColor = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF)
    val cardBg = if (isRedMode) Color(0x33FF2A4B) else Color(0x221E293B)
    val borderColor = if (isRedMode) Color(0x66FF2A4B) else Color(0x3338BDF8)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Planet Header: Symbol + Name + Status Pill
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(planet.color.copy(alpha = 0.8f), planet.glowColor.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        .border(1.5.dp, planet.color.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = planet.symbol,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = planet.nameVi,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRedMode) Color(0xFFFF8A9E) else Color.White
                )

                Text(
                    text = "${planet.nameEn} • Chòm sao: ${planet.constellationVi} ${planet.constellationSymbol}",
                    fontSize = 12.sp,
                    color = if (isRedMode) Color(0xAAFF8A9E) else Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Above / Below Horizon Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (planet.isAboveHorizon) {
                                if (isRedMode) Color(0x44FF2A4B) else Color(0x2A10B981)
                            } else {
                                Color(0x22475569)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (planet.isAboveHorizon) "🟢 Đang trên bầu trời (Có thể quan sát)" else "🔴 Dưới đường chân trời (Không nhìn thấy)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (planet.isAboveHorizon) (if (isRedMode) Color(0xFFFF5252) else Color(0xFF34D399)) else Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Coordinate Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Góc ngẩng (Altitude):", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = planet.formattedAltitude,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Phương vị (Azimuth):", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = planet.formattedAzimuth,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Khoảng cách tới Trái Đất:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = planet.formattedDistance,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Độ sáng biểu kiến (Mag):", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = String.format(java.util.Locale.US, "%+.1f mag", planet.magnitude),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Đường kính thiên thể:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = String.format(java.util.Locale.US, "%,d km", planet.diameterKm),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scientific Description
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "📖 Giới thiệu khoa học",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = planet.descriptionVi,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Observation Tip
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "🔭 Mẹo quan sát thiên văn",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRedMode) Color(0xFFFFB74D) else Color(0xFFFFD54F)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = planet.observationTipVi,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Target Guidance & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onTargetToggle(planet)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTargeted) (if (isRedMode) Color(0xFF6B111A) else Color(0xFF0F766E)) else accentColor
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (isTargeted) "✕ Bỏ dẫn đường" else "🎯 Dẫn đường mục tiêu",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTargeted) Color.White else Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            text = "Đóng",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

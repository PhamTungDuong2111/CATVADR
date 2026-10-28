package com.astrocam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrocam.app.astro.PlanetInfo

@Composable
fun PlanetQuickBar(
    planets: List<PlanetInfo>,
    selectedPlanet: PlanetInfo?,
    targetedPlanet: PlanetInfo?,
    isRedMode: Boolean,
    onPlanetSelected: (PlanetInfo) -> Unit,
    onPlanetTargetToggle: (PlanetInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = if (isRedMode) Color(0xF21C0407) else Color(0xEE090F1E)
    val barBorder = if (isRedMode) Color(0x66FF2A4B) else Color(0x3338BDF8)
    val scrollState = rememberScrollState()

    val visibleCount = planets.count { it.isAboveHorizon }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(barBg)
            .border(1.dp, barBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 8.dp)
    ) {
        // Top header row: Title & Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "🌌", fontSize = 13.sp)
                Text(
                    text = "CELESTIAL & MILKY WAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRedMode) Color(0xFFFF8A9E) else Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRedMode) Color(0x33FF2A4B) else Color(0x2210B981))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$visibleCount/${planets.size} in sky",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRedMode) Color(0xFFFF5252) else Color(0xFF34D399)
                    )
                }
            }

            Text(
                text = "✕",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal scrollable planet chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort: Above horizon planets first
            val sortedList = planets.sortedWith(
                compareByDescending<PlanetInfo> { it.isAboveHorizon }.thenByDescending { it.altitude }
            )

            for (p in sortedList) {
                val isTargeted = targetedPlanet?.id == p.id
                val isSelected = selectedPlanet?.id == p.id

                val chipBg = if (isTargeted) {
                    if (isRedMode) Color(0x44FF2A4B) else Color(0x3300E5FF)
                } else if (p.isAboveHorizon) {
                    if (isRedMode) Color(0x22FF2A4B) else Color(0x1A1E293B)
                } else {
                    Color(0x110F172A)
                }

                val chipBorder = if (isTargeted) {
                    if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF)
                } else if (p.isAboveHorizon) {
                    if (isRedMode) Color(0x44FF2A4B) else Color(0x3338BDF8)
                } else {
                    Color(0x22475569)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isTargeted) {
                                onPlanetSelected(p)
                            } else {
                                onPlanetTargetToggle(p)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = p.symbol, fontSize = 14.sp)

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = p.nameEn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRedMode) Color(0xFFFF8A9E) else (if (p.isAboveHorizon) Color.White else Color.Gray)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (p.isAboveHorizon) (if (isRedMode) Color.Red else Color(0xFF10B981)) else Color(0xFF64748B))
                                )
                            }

                            Text(
                                text = "${p.formattedAltitude} • ${p.azimuth.toInt()}°",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isRedMode) Color(0xAAFF8A9E) else (if (p.isAboveHorizon) Color(0xFFB0BEC5) else Color(0xFF64748B))
                            )
                        }

                        if (isTargeted) {
                            Text(
                                text = "🎯",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

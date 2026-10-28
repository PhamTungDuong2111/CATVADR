package com.astrocam.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrocam.app.astro.PlanetInfo
import com.astrocam.app.astro.SolarSystemEngine
import com.astrocam.app.sensor.OrientationData
import kotlin.math.*

data class ProjectedPlanet(
    val planet: PlanetInfo,
    val screenX: Float,
    val screenY: Float,
    val isOnScreen: Boolean,
    val isFront: Boolean,
    val edgeX: Float,
    val edgeY: Float,
    val edgeAngleDeg: Float,
    val angularDistanceDeg: Float
)

@Composable
fun PlanetOverlay(
    planets: List<PlanetInfo>,
    orientation: OrientationData,
    isRedMode: Boolean,
    selectedPlanet: PlanetInfo?,
    targetedPlanet: PlanetInfo?,
    onPlanetSelected: (PlanetInfo) -> Unit,
    modifier: Modifier = Modifier,
    magneticDeclination: Float = 0f
) {
    val density = LocalDensity.current

    // Pulsing glow animation for celestial reticles
    val infiniteTransition = rememberInfiniteTransition(label = "PlanetAura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        // Calculate 3D screen projection for all celestial bodies
        val projectedList = remember(planets, orientation, widthPx, heightPx, magneticDeclination) {
            val rotMat = orientation.rotationMatrix
            val fovYDeg = 60f
            val fovYRad = Math.toRadians(fovYDeg.toDouble()).toFloat()
            val focalPixels = (heightPx / 2f) / tan(fovYRad / 2f)
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f

            planets.map { p ->
                val altRad = Math.toRadians(p.altitude.toDouble())
                val azRad = Math.toRadians((p.azimuth - magneticDeclination).toDouble())

                // World celestial vector (X=East, Y=North, Z=Up)
                val wx = (cos(altRad) * sin(azRad)).toFloat()
                val wy = (cos(altRad) * cos(azRad)).toFloat()
                val wz = sin(altRad).toFloat()

                // Inverse matrix R^T
                val dx = rotMat[0] * wx + rotMat[3] * wy + rotMat[6] * wz
                val dy = rotMat[1] * wx + rotMat[4] * wy + rotMat[7] * wz
                val dz = rotMat[2] * wx + rotMat[5] * wy + rotMat[8] * wz

                // Back camera looks along -Z
                val zForward = -dz

                val isOnScreen: Boolean
                val isFront = zForward > 0.05f
                val screenX: Float
                val screenY: Float

                if (isFront) {
                    screenX = centerX + focalPixels * (dx / zForward)
                    screenY = centerY - focalPixels * (dy / zForward)
                    isOnScreen = screenX in 40f..(widthPx - 40f) && screenY in 60f..(heightPx - 80f)
                } else {
                    screenX = centerX - focalPixels * (dx / abs(zForward).coerceAtLeast(0.1f))
                    screenY = centerY + focalPixels * (dy / abs(zForward).coerceAtLeast(0.1f))
                    isOnScreen = false
                }

                // 2D direction for off-screen indicator
                val dirX = if (isFront) dx else -dx
                val dirY = if (isFront) -dy else dy
                val len = hypot(dirX, dirY).coerceAtLeast(0.001f)
                val ndx = dirX / len
                val ndy = dirY / len

                val edgeMarginX = 64f
                val edgeMarginY = 110f
                val halfW = (widthPx / 2f) - edgeMarginX
                val halfH = (heightPx / 2f) - edgeMarginY
                val scaleFactor = minOf(
                    if (abs(ndx) > 1e-4) halfW / abs(ndx) else Float.MAX_VALUE,
                    if (abs(ndy) > 1e-4) halfH / abs(ndy) else Float.MAX_VALUE
                )

                val edgeX = centerX + ndx * scaleFactor
                val edgeY = centerY + ndy * scaleFactor
                val edgeAngle = Math.toDegrees(atan2(ndy.toDouble(), ndx.toDouble())).toFloat()

                // Angular separation from camera optical center
                val cosAng = (zForward / hypot(hypot(dx, dy), dz)).coerceIn(-1f, 1f)
                val angularDist = Math.toDegrees(acos(cosAng.toDouble())).toFloat()

                ProjectedPlanet(
                    planet = p,
                    screenX = screenX,
                    screenY = screenY,
                    isOnScreen = isOnScreen,
                    isFront = isFront,
                    edgeX = edgeX,
                    edgeY = edgeY,
                    edgeAngleDeg = edgeAngle,
                    angularDistanceDeg = angularDist
                )
            }
        }

        // 1. CANVAS: Milky Way Band + Ecliptic line + Planet background graphics + Reticle rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f
            val fovYRad = Math.toRadians(60.0).toFloat()
            val focalPixels = (heightPx / 2f) / tan(fovYRad / 2f)
            val rotMat = orientation.rotationMatrix

            // A1. Draw Milky Way Plane Band
            val milkyWayPoints = SolarSystemEngine.calculateMilkyWayPath(stepDeg = 3)
            var prevMwPoint: Offset? = null
            val mwGlowColor = if (isRedMode) Color(0x33FF2A4B) else Color(0x289C27B0)
            val mwLineColor = if (isRedMode) Color(0x77FF2A4B) else Color(0x88BA68C8)

            for (mw in milkyWayPoints) {
                val altRad = Math.toRadians(mw.altitude.toDouble())
                val azRad = Math.toRadians((mw.azimuth - magneticDeclination).toDouble())
                val wx = (cos(altRad) * sin(azRad)).toFloat()
                val wy = (cos(altRad) * cos(azRad)).toFloat()
                val wz = sin(altRad).toFloat()

                val dx = rotMat[0] * wx + rotMat[3] * wy + rotMat[6] * wz
                val dy = rotMat[1] * wx + rotMat[4] * wy + rotMat[7] * wz
                val dz = rotMat[2] * wx + rotMat[5] * wy + rotMat[8] * wz

                if (-dz > 0.08f) {
                    val zF = -dz
                    val sx = centerX + focalPixels * (dx / zF)
                    val sy = centerY - focalPixels * (dy / zF)
                    val pt = Offset(sx, sy)

                    if (prevMwPoint != null && (pt - prevMwPoint).getDistance() < 280f) {
                        // Wide faint galactic gas cloud band
                        drawLine(
                            color = mwGlowColor,
                            start = prevMwPoint,
                            end = pt,
                            strokeWidth = 24f,
                            cap = StrokeCap.Round
                        )
                        // Core starry spine
                        drawLine(
                            color = mwLineColor,
                            start = prevMwPoint,
                            end = pt,
                            strokeWidth = 2.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), ringRotation)
                        )
                    }
                    prevMwPoint = pt
                } else {
                    prevMwPoint = null
                }
            }

            // A2. Draw Ecliptic Path
            val eclipticPoints = SolarSystemEngine.calculateEclipticPath(stepDeg = 6)
            var prevPoint: Offset? = null
            val eclipticColor = if (isRedMode) Color(0x33FF2A4B) else Color(0x3300E5FF)

            for ((eAlt, eAz) in eclipticPoints) {
                val altRad = Math.toRadians(eAlt.toDouble())
                val azRad = Math.toRadians((eAz - magneticDeclination).toDouble())
                val wx = (cos(altRad) * sin(azRad)).toFloat()
                val wy = (cos(altRad) * cos(azRad)).toFloat()
                val wz = sin(altRad).toFloat()

                val dx = rotMat[0] * wx + rotMat[3] * wy + rotMat[6] * wz
                val dy = rotMat[1] * wx + rotMat[4] * wy + rotMat[7] * wz
                val dz = rotMat[2] * wx + rotMat[5] * wy + rotMat[8] * wz

                if (-dz > 0.1f) {
                    val zF = -dz
                    val sx = centerX + focalPixels * (dx / zF)
                    val sy = centerY - focalPixels * (dy / zF)
                    val pt = Offset(sx, sy)

                    if (prevPoint != null && (pt - prevPoint).getDistance() < 300f) {
                        drawLine(
                            color = eclipticColor,
                            start = prevPoint,
                            end = pt,
                            strokeWidth = 1.8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    }
                    prevPoint = pt
                } else {
                    prevPoint = null
                }
            }

            // B. Draw Celestial Graphic for each on-screen planet
            for (item in projectedList) {
                if (item.isOnScreen) {
                    val p = item.planet
                    val isTargeted = targetedPlanet?.id == p.id
                    val isCurrentSelected = selectedPlanet?.id == p.id

                    val baseColor = if (isRedMode) Color(0xFFFF2A4B) else p.color
                    val glowColor = if (isRedMode) Color(0x88FF1744) else p.glowColor

                    val radius = if (isTargeted) 28f * pulseScale else 20f

                    // Glowing Aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(item.screenX, item.screenY),
                            radius = radius * 2.8f
                        ),
                        radius = radius * 2.8f,
                        center = Offset(item.screenX, item.screenY)
                    )

                    // Core Planet Body Drawing
                    drawPlanetDisc(
                        planet = p,
                        center = Offset(item.screenX, item.screenY),
                        radius = radius,
                        baseColor = baseColor,
                        isRedMode = isRedMode
                    )

                    // Outer Reticle & Crosshairs
                    val ringColor = if (isTargeted) (if (isRedMode) Color.Red else Color(0xFF00E5FF)) else baseColor.copy(alpha = 0.6f)
                    drawCircle(
                        color = ringColor,
                        radius = radius * 1.55f,
                        center = Offset(item.screenX, item.screenY),
                        style = Stroke(
                            width = if (isTargeted) 2.5f else 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), ringRotation)
                        )
                    )

                    // Reticle tick marks
                    val tickLen = 7f
                    val r1 = radius * 1.7f
                    val r2 = r1 + tickLen
                    val c = Offset(item.screenX, item.screenY)
                    drawLine(ringColor, c + Offset(0f, -r1), c + Offset(0f, -r2), 1.5f)
                    drawLine(ringColor, c + Offset(0f, r1), c + Offset(0f, r2), 1.5f)
                    drawLine(ringColor, c + Offset(-r1, 0f), c + Offset(-r2, 0f), 1.5f)
                    drawLine(ringColor, c + Offset(r1, 0f), c + Offset(r2, 0f), 1.5f)
                }
            }

            // C. Draw Target Guidance Lock Ring if a planet is actively targeted
            targetedPlanet?.let { target ->
                val targetItem = projectedList.firstOrNull { it.planet.id == target.id }
                if (targetItem != null && !targetItem.isOnScreen) {
                    // Draw large guide ring in center
                    val guideColor = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF)
                    drawCircle(
                        color = guideColor.copy(alpha = 0.4f),
                        radius = 80f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )

                    // Arrow from center pointing towards target
                    val rad = Math.toRadians(targetItem.edgeAngleDeg.toDouble())
                    val arrowLen = 65f
                    val ax = centerX + cos(rad).toFloat() * arrowLen
                    val ay = centerY + sin(rad).toFloat() * arrowLen

                    drawLine(
                        color = guideColor,
                        start = Offset(centerX, centerY),
                        end = Offset(ax, ay),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(guideColor, radius = 5f, center = Offset(ax, ay))
                }
            }
        }

        // 2. COMPOSABLE INTERACTIVE BADGES FOR ON-SCREEN PLANETS
        for (item in projectedList) {
            if (item.isOnScreen) {
                val p = item.planet
                val isTargeted = targetedPlanet?.id == p.id
                val isSelected = selectedPlanet?.id == p.id

                val cardBg = if (isRedMode) Color(0xE61A0205) else Color(0xDD0B1220)
                val borderColor = if (isTargeted) (if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF))
                else if (isRedMode) Color(0x66FF2A4B) else Color(0x5538BDF8)

                // Place tag slightly below and to the right of planet
                val tagX = (item.screenX + 24f).coerceIn(16f, widthPx - 210f)
                val tagY = (item.screenY + 24f).coerceIn(40f, heightPx - 110f)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(tagX.roundToInt(), tagY.roundToInt()) }
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(
                            width = if (isTargeted || isSelected) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onPlanetSelected(p)
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = p.symbol,
                                fontSize = 14.sp
                            )
                            Text(
                                text = p.nameEn,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRedMode) Color(0xFFFF8A9E) else Color.White
                            )
                            Text(
                                text = p.constellationSymbol,
                                fontSize = 11.sp,
                                color = if (isRedMode) Color(0xAAFF5252) else Color(0xFF38BDF8)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Alt: ${p.formattedAltitude}  Az: ${p.azimuth.roundToInt()}°",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRedMode) Color(0xCCFFB2BA) else Color(0xCCB0BEC5)
                        )

                        Text(
                            text = "Mag: ${String.format(java.util.Locale.US, "%+.1f", p.magnitude)} • ${p.constellationVi}",
                            fontSize = 9.sp,
                            color = if (isRedMode) Color(0x99FF8A9E) else Color(0x9994A3B8)
                        )
                    }
                }
            }
        }

        // 3. COMPOSABLE OFF-SCREEN DIRECTIONAL PILL BADGES
        // Show indicator arrows on screen border so user instantly knows where planets are even if phone is on table or facing wall!
        for (item in projectedList) {
            if (!item.isOnScreen && item.planet.isAboveHorizon) {
                val p = item.planet
                val isTargeted = targetedPlanet?.id == p.id

                val pillBg = if (isRedMode) Color(0xE625050A) else Color(0xEE091122)
                val pillBorder = if (isTargeted) (if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF))
                else if (isRedMode) Color(0x55FF2A4B) else Color(0x3338BDF8)

                val pillX = (item.edgeX - 55f).coerceIn(8f, widthPx - 130f)
                val pillY = (item.edgeY - 18f).coerceIn(40f, heightPx - 70f)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillX.roundToInt(), pillY.roundToInt()) }
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBg)
                        .border(
                            width = if (isTargeted) 1.5.dp else 1.dp,
                            color = pillBorder,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onPlanetSelected(p)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = p.symbol,
                            fontSize = 12.sp
                        )
                        Text(
                            text = p.nameEn,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRedMode) Color(0xFFFF8A9E) else Color.White
                        )
                        // Arrow pointing towards off-screen planet
                        Text(
                            text = "➤",
                            fontSize = 10.sp,
                            modifier = Modifier.rotate(item.edgeAngleDeg),
                            color = if (isTargeted) (if (isRedMode) Color.Red else Color(0xFF00E5FF)) else (if (isRedMode) Color(0xAAFF5252) else Color(0xFF38BDF8))
                        )
                    }
                }
            }
        }

        // 4. TARGET GUIDANCE HUD BANNER (When a planet is actively targeted)
        targetedPlanet?.let { target ->
            val targetItem = projectedList.firstOrNull { it.planet.id == target.id }
            if (targetItem != null) {
                val isLocked = targetItem.isOnScreen && targetItem.angularDistanceDeg < 6f

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isLocked) (if (isRedMode) Color(0xE640060C) else Color(0xE6064E3B)) else (if (isRedMode) Color(0xDD200307) else Color(0xDD0F172A)))
                        .border(
                            1.dp,
                            if (isLocked) (if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF10B981)) else (if (isRedMode) Color(0x66FF2A4B) else Color(0x6638BDF8)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = target.symbol,
                            fontSize = 18.sp
                        )
                        Column {
                            Text(
                                text = if (isLocked) "🎯 TARGET LOCKED: ${target.nameEn.uppercase()}" else "🧭 POINT TOWARDS ${target.nameEn.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLocked) (if (isRedMode) Color(0xFFFF5252) else Color(0xFF34D399)) else Color.White
                            )
                            Text(
                                text = if (isLocked) "Altitude: ${target.formattedAltitude} • Azimuth: ${target.formattedAzimuth}" else "Off by: ${targetItem.angularDistanceDeg.roundToInt()}° • Rotate phone along arrow",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas drawing for planetary disc features (Saturn rings, Jupiter bands, Mars crater glow, Moon phase, Venus flare)
 */
private fun DrawScope.drawPlanetDisc(
    planet: PlanetInfo,
    center: Offset,
    radius: Float,
    baseColor: Color,
    isRedMode: Boolean
) {
    when (planet.id) {
        "SATURN" -> {
            // Draw Saturn's tilted rings first (behind the planet)
            val ringWidth = radius * 2.8f
            val ringHeight = radius * 0.95f
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x99FFE082), Color(0xEEFFD54F), Color(0x99FFE082))
                ),
                topLeft = center - Offset(ringWidth / 2f, ringHeight / 2f),
                size = Size(ringWidth, ringHeight),
                style = Stroke(width = 3.5f)
            )

            // Planet sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.7f), Color(0xFFB58E2A)),
                    center = center - Offset(radius * 0.3f, radius * 0.3f),
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        "JUPITER" -> {
            // Jupiter with cloud bands
            drawCircle(
                color = baseColor,
                radius = radius,
                center = center
            )
            // Two dark atmospheric stripes
            val stripeH = radius * 0.25f
            drawRect(
                color = Color(0x66B76A1B),
                topLeft = center - Offset(radius * 0.85f, stripeH * 1.5f),
                size = Size(radius * 1.7f, stripeH)
            )
            drawRect(
                color = Color(0x66B76A1B),
                topLeft = center - Offset(radius * 0.85f, -stripeH * 0.5f),
                size = Size(radius * 1.7f, stripeH)
            )
        }
        "MARS" -> {
            // Mars rust red sphere with dark polar cap
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF8A65), baseColor, Color(0xFFBF360C)),
                    center = center - Offset(radius * 0.2f, radius * 0.2f),
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        "VENUS" -> {
            // Brilliant bright white-gold disc with 4-point radiant lens sparkle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFFFF9C4), baseColor),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            val flareLen = radius * 2.2f
            drawLine(Color.White.copy(alpha = 0.75f), center - Offset(flareLen, 0f), center + Offset(flareLen, 0f), 1.2f)
            drawLine(Color.White.copy(alpha = 0.75f), center - Offset(0f, flareLen), center + Offset(0f, flareLen), 1.2f)
        }
        "MOON" -> {
            // Moon with lunar phase
            drawCircle(
                color = Color(0xFFECEFF1),
                radius = radius,
                center = center
            )
            // Subtle crater dark spots
            drawCircle(Color(0x3378909C), radius = radius * 0.35f, center = center - Offset(radius * 0.3f, 0f))
            drawCircle(Color(0x3378909C), radius = radius * 0.22f, center = center + Offset(radius * 0.2f, radius * 0.3f))
        }
        "SUN" -> {
            // Sun with radiant corona
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        "MILKY_WAY_CORE" -> {
            // Spiral galaxy disc with glowing core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFFF80AB), Color(0x889C27B0), Color.Transparent),
                    center = center,
                    radius = radius * 1.6f
                ),
                radius = radius * 1.6f,
                center = center
            )
            val ovalW = radius * 2.6f
            val ovalH = radius * 1.1f
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x88BA68C8), Color(0xEEFF80AB), Color(0x887C4DFF))
                ),
                topLeft = center - Offset(ovalW / 2f, ovalH / 2f),
                size = Size(ovalW, ovalH),
                style = Stroke(width = 2.5f)
            )
        }
        "ANDROMEDA" -> {
            // Tilted Andromeda galaxy oval with bright nucleus
            val mw = radius * 2.4f
            val mh = radius * 0.9f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFF80DEEA), Color(0x4400E5FF), Color.Transparent),
                    center = center,
                    radius = radius * 1.5f
                ),
                topLeft = center - Offset(mw / 2f, mh / 2f),
                size = Size(mw, mh)
            )
            drawCircle(Color.White, radius = radius * 0.35f, center = center)
        }
        "ORION_NEBULA" -> {
            // Glowing emission nebula cloud
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF8A80), Color(0xAAFF4081), Color(0x337C4DFF), Color.Transparent),
                    center = center,
                    radius = radius * 1.5f
                ),
                radius = radius * 1.5f,
                center = center
            )
            drawCircle(Color.White, radius = radius * 0.3f, center = center)
        }
        "PLEIADES" -> {
            // Cluster of dazzling mini stars
            drawCircle(Color(0x44448AFF), radius = radius * 1.2f, center = center)
            val offsets = listOf(
                Offset(0f, 0f),
                Offset(-radius * 0.4f, -radius * 0.3f),
                Offset(radius * 0.35f, -radius * 0.4f),
                Offset(radius * 0.5f, radius * 0.2f),
                Offset(-radius * 0.3f, radius * 0.4f),
                Offset(-radius * 0.6f, 0.1f * radius),
                Offset(0.1f * radius, radius * 0.5f)
            )
            for (off in offsets) {
                drawCircle(Color.White, radius = 2.2f, center = center + off)
            }
        }
        "SIRIUS" -> {
            // Bright sparkling star
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFF80D8FF), Color.Transparent),
                    center = center,
                    radius = radius * 1.4f
                ),
                radius = radius * 1.4f,
                center = center
            )
            val l = radius * 1.8f
            drawLine(Color.White, center - Offset(l, 0f), center + Offset(l, 0f), 1.5f)
            drawLine(Color.White, center - Offset(0f, l), center + Offset(0f, l), 1.5f)
        }
        "POLARIS" -> {
            // North star with compass cross
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFFFD54F), Color.Transparent),
                    center = center,
                    radius = radius * 1.2f
                ),
                radius = radius * 1.2f,
                center = center
            )
            val l = radius * 1.5f
            drawLine(Color(0xFFFFD54F), center - Offset(l, 0f), center + Offset(l, 0f), 1.5f)
            drawLine(Color(0xFFFFD54F), center - Offset(0f, l), center + Offset(0f, l), 1.5f)
        }
        else -> {
            // Standard planet sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.7f)),
                    center = center - Offset(radius * 0.25f, radius * 0.25f),
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

package com.astrocam.app.astro

import androidx.compose.ui.graphics.Color

enum class CelestialBodyType {
    STAR,
    PLANET,
    MOON,
    SUN,
    MILKY_WAY,
    DEEP_SKY
}

data class PlanetInfo(
    val id: String,
    val nameVi: String,
    val nameEn: String,
    val symbol: String,
    val color: Color,
    val glowColor: Color,
    val altitude: Float,        // Altitude angle (-90° to +90°)
    val azimuth: Float,         // Azimuth (0° to 360°, 0=North, 90=East, 180=South, 270=West)
    val distanceAu: Double,     // Distance to Earth (AU)
    val magnitude: Float,       // Apparent magnitude
    val constellationVi: String,// Constellation name
    val constellationSymbol: String,
    val type: CelestialBodyType = CelestialBodyType.PLANET,
    val descriptionVi: String,
    val observationTipVi: String,
    val diameterKm: Long,
    val phasePercent: Int = 100 // Illumination percentage (for Moon)
) {
    val isAboveHorizon: Boolean
        get() = altitude > 0f

    val formattedAltitude: String
        get() = String.format(java.util.Locale.US, "%+.1f°", altitude)

    val formattedAzimuth: String
        get() = String.format(java.util.Locale.US, "%.0f° %s", azimuth, getAzimuthDirection(azimuth))

    val formattedDistance: String
        get() = when (type) {
            CelestialBodyType.MOON -> String.format(java.util.Locale.US, "%,d km", (distanceAu * 149597870.7).toLong())
            CelestialBodyType.MILKY_WAY -> "~26,000 light-years (Galactic Center)"
            CelestialBodyType.DEEP_SKY -> when (id) {
                "ANDROMEDA" -> "~2.5 million light-years"
                "PLEIADES" -> "~444 light-years"
                "ORION_NEBULA" -> "~1,344 light-years"
                else -> String.format(java.util.Locale.US, "%,.0f light-years", distanceAu / 63241.07)
            }
            CelestialBodyType.STAR -> when (id) {
                "SIRIUS" -> "8.6 light-years"
                "POLARIS" -> "~433 light-years"
                else -> String.format(java.util.Locale.US, "%,.1f light-years", distanceAu / 63241.07)
            }
            else -> String.format(java.util.Locale.US, "%.2f AU (%,d million km)", distanceAu, (distanceAu * 149.6).toLong())
        }

    companion object {
        fun getAzimuthDirection(azimuth: Float): String {
            val norm = (azimuth % 360f + 360f) % 360f
            return when (((norm + 22.5f) / 45f).toInt() % 8) {
                0 -> "North (N)"
                1 -> "Northeast (NE)"
                2 -> "East (E)"
                3 -> "Southeast (SE)"
                4 -> "South (S)"
                5 -> "Southwest (SW)"
                6 -> "West (W)"
                7 -> "Northwest (NW)"
                else -> "North (N)"
            }
        }
    }
}

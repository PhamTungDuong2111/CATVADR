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
    val altitude: Float,        // Góc ngẩng (-90° đến +90°)
    val azimuth: Float,         // Phương vị (0° đến 360°, 0=Bắc, 90=Đông, 180=Nam, 270=Tây)
    val distanceAu: Double,     // Khoảng cách tới Trái Đất (AU)
    val magnitude: Float,       // Độ sáng biểu kiến
    val constellationVi: String,// Chòm sao hoàng đạo / bầu trời
    val constellationSymbol: String,
    val type: CelestialBodyType = CelestialBodyType.PLANET,
    val descriptionVi: String,
    val observationTipVi: String,
    val diameterKm: Long,
    val phasePercent: Int = 100 // Chỉ số phần trăm chiếu sáng (cho Mặt Trăng)
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
            CelestialBodyType.MILKY_WAY -> "~26.000 năm ánh sáng (Tâm Ngân Hà)"
            CelestialBodyType.DEEP_SKY -> when (id) {
                "ANDROMEDA" -> "~2.5 triệu năm ánh sáng"
                "PLEIADES" -> "~444 năm ánh sáng"
                "ORION_NEBULA" -> "~1.344 năm ánh sáng"
                else -> String.format(java.util.Locale.US, "%,.0f năm ánh sáng", distanceAu / 63241.07)
            }
            CelestialBodyType.STAR -> when (id) {
                "SIRIUS" -> "8.6 năm ánh sáng"
                "POLARIS" -> "~433 năm ánh sáng"
                else -> String.format(java.util.Locale.US, "%,.1f năm ánh sáng", distanceAu / 63241.07)
            }
            else -> String.format(java.util.Locale.US, "%.2f AU (%,d triệu km)", distanceAu, (distanceAu * 149.6).toLong())
        }

    companion object {
        fun getAzimuthDirection(azimuth: Float): String {
            val norm = (azimuth % 360f + 360f) % 360f
            return when (((norm + 22.5f) / 45f).toInt() % 8) {
                0 -> "Bắc (N)"
                1 -> "Đông Bắc (NE)"
                2 -> "Đông (E)"
                3 -> "Đông Nam (SE)"
                4 -> "Nam (S)"
                5 -> "Tây Nam (SW)"
                6 -> "Tây (W)"
                7 -> "Tây Bắc (NW)"
                else -> "Bắc (N)"
            }
        }
    }
}

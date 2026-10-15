package com.astrocam.app.astro

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * Thuật toán thiên văn Paul Schlyter & Jean Meeus tính toán tọa độ biểu kiến
 * (Góc ngẩng Altitude & Phương vị Azimuth) thời gian thực của các hành tinh trong Hệ Mặt Trời
 * đối với vị trí quan sát trên Trái Đất (Topocentric Coordinates).
 */
object SolarSystemEngine {

    private fun normalizeDeg(deg: Double): Double {
        var v = deg % 360.0
        if (v < 0.0) v += 360.0
        return v
    }

    private fun solveKepler(M_rad: Double, e: Double): Double {
        var E = M_rad + e * sin(M_rad) * (1.0 + e * cos(M_rad))
        for (i in 0..6) {
            val dE = (E - e * sin(E) - M_rad) / (1.0 - e * cos(E))
            E -= dE
            if (abs(dE) < 1e-7) break
        }
        return E
    }

    fun getZodiacConstellation(eclipticLonDeg: Double): Pair<String, String> {
        val norm = normalizeDeg(eclipticLonDeg)
        val idx = ((norm / 30.0).toInt()) % 12
        return when (idx) {
            0 -> "Bạch Dương" to "♈"
            1 -> "Kim Ngưu" to "♉"
            2 -> "Song Tử" to "♊"
            3 -> "Cự Giải" to "♋"
            4 -> "Sư Tử" to "♌"
            5 -> "Xử Nữ" to "♍"
            6 -> "Thiên Bình" to "♎"
            7 -> "Bọ Cạp" to "♏"
            8 -> "Nhân Mã" to "♐"
            9 -> "Ma Kết" to "♑"
            10 -> "Bảo Bình" to "♒"
            11 -> "Song Ngư" to "♓"
            else -> "Bạch Dương" to "♈"
        }
    }

    /**
     * Tính toán vị trí tất cả các hành tinh cho thời điểm timeMillis tại tọa độ (latitude, longitude)
     */
    fun calculateAllBodies(
        timeMillis: Long = System.currentTimeMillis(),
        latitude: Double = 21.0285, // Mặc định Hà Nội
        longitude: Double = 105.8542
    ): List<PlanetInfo> {
        val jd = (timeMillis.toDouble() / 86400000.0) + 2440587.5
        val d = jd - 2451543.5 // Số ngày từ kỷ nguyên J2000.0

        val eclDeg = 23.4393 - 3.563e-7 * d
        val eclRad = Math.toRadians(eclDeg)

        // Tính vị trí Mặt Trời
        val wSun = normalizeDeg(282.9404 + 4.70935e-5 * d)
        val aSun = 1.000000
        val eSun = 0.016709 - 1.151e-9 * d
        val mSun = normalizeDeg(356.0470 + 0.9856002585 * d)
        val mSunRad = Math.toRadians(mSun)
        val vSunRad = mSunRad + (2.0 * eSun - eSun.pow(3) / 4.0) * sin(mSunRad) + 1.25 * eSun.pow(2) * sin(2.0 * mSunRad)
        val lonSunRad = Math.toRadians(wSun) + vSunRad
        val rSun = aSun * (1.0 - eSun * eSun) / (1.0 + eSun * cos(vSunRad))
        val xs = rSun * cos(lonSunRad)
        val ys = rSun * sin(lonSunRad)
        val zs = 0.0

        // Giờ thiên văn Greenwich và địa phương (Local Sidereal Time)
        val gmstDeg = normalizeDeg(280.46061837 + 360.98564736629 * d)
        val lstDeg = normalizeDeg(gmstDeg + longitude)
        val lstRad = Math.toRadians(lstDeg)
        val latRad = Math.toRadians(latitude)

        val results = mutableListOf<PlanetInfo>()

        // 1. MẶT TRỜI
        run {
            val xeq = xs
            val yeq = ys * cos(eclRad) - zs * sin(eclRad)
            val zeq = ys * sin(eclRad) + zs * cos(eclRad)
            val raRad = atan2(yeq, xeq)
            val decRad = atan2(zeq, sqrt(xeq * xeq + yeq * yeq))
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)
            val (constName, constSym) = getZodiacConstellation(Math.toDegrees(lonSunRad))

            results.add(
                PlanetInfo(
                    id = "SUN",
                    nameVi = "Mặt Trời",
                    nameEn = "Sun",
                    symbol = "☀️",
                    color = Color(0xFFFFD54F),
                    glowColor = Color(0xFFFF8F00),
                    altitude = alt,
                    azimuth = az,
                    distanceAu = rSun,
                    magnitude = -26.7f,
                    constellationVi = constName,
                    constellationSymbol = constSym,
                    type = CelestialBodyType.SUN,
                    descriptionVi = "Ngôi sao trung tâm của Hệ Mặt Trời, cung cấp nhiệt và ánh sáng nuôi dưỡng sự sống trên Trái Đất.",
                    observationTipVi = "Cảnh báo: Tuyệt đối KHÔNG nhìn trực tiếp bằng ống nhòm hoặc mắt thường qua kính thiên văn mà không có màng lọc mặt trời chuyên dụng.",
                    diameterKm = 1392700L
                )
            )
        }

        // 2. MẶT TRĂNG
        run {
            val nMoon = normalizeDeg(125.1228 - 0.0529538083 * d)
            val iMoon = 5.1454
            val wMoon = normalizeDeg(318.0634 + 0.1643573223 * d)
            val aMoon = 60.2666 // Bán kính Trái Đất
            val eMoon = 0.054900
            val mMoon = normalizeDeg(115.3654 + 13.0649929509 * d)
            val mMoonRad = Math.toRadians(mMoon)

            val eRad = solveKepler(mMoonRad, eMoon)
            val xv = aMoon * (cos(eRad) - eMoon)
            val yv = aMoon * sqrt(1.0 - eMoon * eMoon) * sin(eRad)
            val rMoon = sqrt(xv * xv + yv * yv)
            val vMoon = atan2(yv, xv)

            val nRad = Math.toRadians(nMoon)
            val iRad = Math.toRadians(iMoon)
            val vwRad = vMoon + Math.toRadians(wMoon)

            val xh = rMoon * (cos(nRad) * cos(vwRad) - sin(nRad) * sin(vwRad) * cos(iRad))
            val yh = rMoon * (sin(nRad) * cos(vwRad) + cos(nRad) * sin(vwRad) * cos(iRad))
            val zh = rMoon * (sin(vwRad) * sin(iRad))

            val distAu = (rMoon * 6378.137) / 149597870.7

            val xeq = xh
            val yeq = yh * cos(eclRad) - zh * sin(eclRad)
            val zeq = yh * sin(eclRad) + zh * cos(eclRad)
            val raRad = atan2(yeq, xeq)
            val decRad = atan2(zeq, sqrt(xeq * xeq + yeq * yeq))
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)

            val moonLonDeg = normalizeDeg(Math.toDegrees(atan2(yh, xh)))
            val sunLonDeg = normalizeDeg(Math.toDegrees(lonSunRad))
            val elongationDeg = normalizeDeg(moonLonDeg - sunLonDeg)
            val phaseFrac = (1.0 - cos(Math.toRadians(elongationDeg))) / 2.0
            val phasePct = (phaseFrac * 100.0).roundToInt().coerceIn(0, 100)

            val (constName, constSym) = getZodiacConstellation(moonLonDeg)

            val phaseTitle = when {
                phasePct < 5 -> "Trăng Non (New Moon)"
                elongationDeg < 90 -> "Trăng Lưỡi Liềm Đầu Tháng ($phasePct%)"
                elongationDeg < 170 -> "Trăng Bán Nguyệt Đầu Tháng ($phasePct%)"
                elongationDeg < 190 -> "Trăng Tròn (Full Moon $phasePct%)"
                elongationDeg < 270 -> "Trăng Khuyết Cuối Tháng ($phasePct%)"
                else -> "Trăng Lưỡi Liềm Cuối Tháng ($phasePct%)"
            }

            results.add(
                PlanetInfo(
                    id = "MOON",
                    nameVi = "Mặt Trăng",
                    nameEn = "Moon",
                    symbol = "🌙",
                    color = Color(0xFFECEFF1),
                    glowColor = Color(0xFF90CAF9),
                    altitude = alt,
                    azimuth = az,
                    distanceAu = distAu,
                    magnitude = -12.5f + (1f - phaseFrac.toFloat()) * 4f,
                    constellationVi = constName,
                    constellationSymbol = constSym,
                    type = CelestialBodyType.MOON,
                    descriptionVi = "Vệ tinh tự nhiên duy nhất của Trái Đất. Trạng thái pha hiện tại: $phaseTitle.",
                    observationTipVi = "Thời điểm tốt nhất để quan sát các miệng hố va chạm và rặng núi là quanh đường phân chia sáng-tối (terminator).",
                    diameterKm = 3474L,
                    phasePercent = phasePct
                )
            )
        }

        // 3. CÁC HÀNH TINH (Sao Thủy, Sao Kim, Sao Hỏa, Sao Mộc, Sao Thổ, Thiên Vương, Hải Vương)
        data class PlanetParams(
            val id: String,
            val nameVi: String,
            val nameEn: String,
            val symbol: String,
            val color: Color,
            val glowColor: Color,
            val N0: Double, val Nd: Double,
            val i0: Double, val id_rate: Double,
            val w0: Double, val wd: Double,
            val a0: Double, val ad: Double,
            val e0: Double, val ed: Double,
            val M0: Double, val Md: Double,
            val baseMag: Float,
            val diameterKm: Long,
            val descriptionVi: String,
            val tipVi: String
        )

        val planetParamsList = listOf(
            PlanetParams(
                id = "MERCURY",
                nameVi = "Sao Thủy",
                nameEn = "Mercury",
                symbol = "☿",
                color = Color(0xFFCFD8DC),
                glowColor = Color(0xFF90A4AE),
                N0 = 48.3313, Nd = 3.24587e-5,
                i0 = 7.0047, id_rate = 5.00e-8,
                w0 = 29.1241, wd = 1.01444e-5,
                a0 = 0.387098, ad = 0.0,
                e0 = 0.205635, ed = 5.59e-10,
                M0 = 168.6562, Md = 4.0923344368,
                baseMag = -0.4f,
                diameterKm = 4879L,
                descriptionVi = "Hành tinh gần Mặt Trời nhất và nhỏ nhất trong Hệ Mặt Trời. Nhiệt độ dao động từ -180°C ban đêm đến 430°C ban ngày.",
                tipVi = "Quan sát tốt nhất vào lúc chạng vạng hoàng hôn hoặc bình minh khi đạt góc ly giác cực đại."
            ),
            PlanetParams(
                id = "VENUS",
                nameVi = "Sao Kim",
                nameEn = "Venus",
                symbol = "♀",
                color = Color(0xFFFFF9C4),
                glowColor = Color(0xFFFFD54F),
                N0 = 76.6799, Nd = 2.46590e-5,
                i0 = 3.3946, id_rate = 2.75e-8,
                w0 = 54.8910, wd = 1.38374e-5,
                a0 = 0.723330, ad = 0.0,
                e0 = 0.006773, ed = -1.302e-9,
                M0 = 48.0052, Md = 1.6021302244,
                baseMag = -4.4f,
                diameterKm = 12104L,
                descriptionVi = "Thiên thể tự nhiên sáng thứ ba trên bầu trời đêm sau Mặt Trời và Mặt Trăng. Dân gian gọi là Sao Hôm (hoàng hôn) hoặc Sao Mai (bình minh).",
                tipVi = "Dễ dàng nhìn thấy bằng mắt thường ngay cả khi hoàng hôn chưa tắt hẳn vì phản xạ ánh sáng cực mạnh từ lớp mây axit sulfuric dày đặc."
            ),
            PlanetParams(
                id = "MARS",
                nameVi = "Sao Hỏa",
                nameEn = "Mars",
                symbol = "♂",
                color = Color(0xFFFF7043),
                glowColor = Color(0xFFD84315),
                N0 = 49.5574, Nd = 2.11081e-5,
                i0 = 1.8497, id_rate = -1.78e-8,
                w0 = 286.5016, wd = 2.92961e-5,
                a0 = 1.523688, ad = 0.0,
                e0 = 0.093405, ed = 2.516e-9,
                M0 = 18.6021, Md = 0.5240207766,
                baseMag = -1.2f,
                diameterKm = 6779L,
                descriptionVi = "Hành tinh Đỏ thứ 4 từ Mặt Trời. Có núi lửa Olympus Mons cao nhất Hệ Mặt Trời (21.9 km) và hẻm núi Valles Marineris kỳ vĩ.",
                tipVi = "Hiện lên với ánh sáng đỏ cam đặc trưng rất dễ phân biệt bằng mắt thường trên nền sao."
            ),
            PlanetParams(
                id = "JUPITER",
                nameVi = "Sao Mộc",
                nameEn = "Jupiter",
                symbol = "♃",
                color = Color(0xFFFFCC80),
                glowColor = Color(0xFFEF6C00),
                N0 = 100.4542, Nd = 2.76854e-5,
                i0 = 1.3030, id_rate = -1.557e-7,
                w0 = 273.8777, wd = 1.64505e-5,
                a0 = 5.20256, ad = 0.0,
                e0 = 0.048498, ed = 4.469e-9,
                M0 = 19.8950, Md = 0.0830853001,
                baseMag = -2.7f,
                diameterKm = 139820L,
                descriptionVi = "Hành tinh lớn nhất trong Hệ Mặt Trời, khối lượng lớn hơn 2.5 lần tất cả các hành tinh khác cộng lại. Nổi tiếng với Vết Đỏ Lớn khổng lồ.",
                tipVi = "Dùng ống nhòm thông thường có thể nhìn thấy 4 vệ tinh Galileo lớn: Io, Europa, Ganymede và Callisto xếp thẳng hàng."
            ),
            PlanetParams(
                id = "SATURN",
                nameVi = "Sao Thổ",
                nameEn = "Saturn",
                symbol = "♄",
                color = Color(0xFFFFE082),
                glowColor = Color(0xFFFFB300),
                N0 = 113.6634, Nd = 2.38980e-5,
                i0 = 2.4886, id_rate = -1.081e-7,
                w0 = 339.3939, wd = 2.97661e-5,
                a0 = 9.55475, ad = 0.0,
                e0 = 0.055546, ed = -9.499e-9,
                M0 = 316.9670, Md = 0.0334442282,
                baseMag = 0.4f,
                diameterKm = 116460L,
                descriptionVi = "Chúa tể những chiếc vành đai. Hệ thống vành đai ngoạn mục cấu thành từ hàng tỷ hạt băng và bụi đá quay quanh hành tinh khí.",
                tipVi = "Kính thiên văn cỡ nhỏ (độ phóng đại 30x trở lên) là đủ để chiêm ngưỡng rõ ràng vành đai và vệ tinh khổng lồ Titan."
            ),
            PlanetParams(
                id = "URANUS",
                nameVi = "Sao Thiên Vương",
                nameEn = "Uranus",
                symbol = "♅",
                color = Color(0xFF80DEEA),
                glowColor = Color(0xFF00ACC1),
                N0 = 74.0005, Nd = 1.3978e-5,
                i0 = 0.7733, id_rate = 1.9e-8,
                w0 = 96.6612, wd = 3.0565e-5,
                a0 = 19.18171, ad = -1.55e-8,
                e0 = 0.047318, ed = 7.45e-9,
                M0 = 142.5905, Md = 0.011725806,
                baseMag = 5.7f,
                diameterKm = 50724L,
                descriptionVi = "Hành tinh băng khổng lồ có bầu khí quyển chứa hydro, heli và methane tạo nên màu lục lam thơ mộng. Trục tự quay nghiêng tới 97.8°.",
                tipVi = "Nằm ở ngưỡng nhạy của mắt thường trong điều kiện trời đêm cực tối (Bortle 1-2). Nên dùng ống nhòm để xác định dưới dạng chấm xanh nhạt."
            ),
            PlanetParams(
                id = "NEPTUNE",
                nameVi = "Sao Hải Vương",
                nameEn = "Neptune",
                symbol = "♆",
                color = Color(0xFF64B5F6),
                glowColor = Color(0xFF1976D2),
                N0 = 131.7806, Nd = 3.0173e-5,
                i0 = 1.7700, id_rate = -2.55e-7,
                w0 = 272.8461, wd = -6.027e-6,
                a0 = 30.05826, ad = 3.313e-8,
                e0 = 0.008606, ed = 2.15e-9,
                M0 = 260.2471, Md = 0.005995147,
                baseMag = 7.8f,
                diameterKm = 49244L,
                descriptionVi = "Hành tinh xa xôi nhất trong Hệ Mặt Trời. Có những cơn bão dữ dội nhất với tốc độ gió đo được lên tới 2,100 km/h.",
                tipVi = "Cần kính thiên văn khẩu độ từ 70mm trở lên để phân biệt đĩa màu xanh lam thẫm giữa hàng ngàn ngôi sao nền."
            )
        )

        for (p in planetParamsList) {
            val n = normalizeDeg(p.N0 + p.Nd * d)
            val i = p.i0 + p.id_rate * d
            val w = normalizeDeg(p.w0 + p.wd * d)
            val a = p.a0 + p.ad * d
            val e = p.e0 + p.ed * d
            val m = normalizeDeg(p.M0 + p.Md * d)
            val mRad = Math.toRadians(m)

            val eRad = solveKepler(mRad, e)
            val xv = a * (cos(eRad) - e)
            val yv = a * sqrt(1.0 - e * e) * sin(eRad)
            val r = sqrt(xv * xv + yv * yv)
            val v = atan2(yv, xv)

            val nRad = Math.toRadians(n)
            val iRad = Math.toRadians(i)
            val vwRad = v + Math.toRadians(w)

            val xh = r * (cos(nRad) * cos(vwRad) - sin(nRad) * sin(vwRad) * cos(iRad))
            val yh = r * (sin(nRad) * cos(vwRad) + cos(nRad) * sin(vwRad) * cos(iRad))
            val zh = r * (sin(vwRad) * sin(iRad))

            // Tọa độ địa tâm (Geocentric)
            val xg = xh + xs
            val yg = yh + ys
            val zg = zh + zs
            val distAu = sqrt(xg * xg + yg * yg + zg * zg)

            // Đổi sang tọa độ xích đạo (Equatorial)
            val xeq = xg
            val yeq = yg * cos(eclRad) - zg * sin(eclRad)
            val zeq = yg * sin(eclRad) + zg * cos(eclRad)
            val raRad = atan2(yeq, xeq)
            val decRad = atan2(zeq, sqrt(xeq * xeq + yeq * yeq))

            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)

            val eclLon = normalizeDeg(Math.toDegrees(atan2(yg, xg)))
            val (constName, constSym) = getZodiacConstellation(eclLon)

            results.add(
                PlanetInfo(
                    id = p.id,
                    nameVi = p.nameVi,
                    nameEn = p.nameEn,
                    symbol = p.symbol,
                    color = p.color,
                    glowColor = p.glowColor,
                    altitude = alt,
                    azimuth = az,
                    distanceAu = distAu,
                    magnitude = p.baseMag + (5f * log10((r * distAu).toFloat())).coerceAtLeast(-5f),
                    constellationVi = constName,
                    constellationSymbol = constSym,
                    type = CelestialBodyType.PLANET,
                    descriptionVi = p.descriptionVi,
                    observationTipVi = p.tipVi,
                    diameterKm = p.diameterKm
                )
            )
        }

        return results
    }

    /**
     * Tính toán chuỗi điểm trên Đường Hoàng Đạo (Ecliptic line) để vẽ quỹ đạo mặt phẳng hệ mặt trời
     */
    fun calculateEclipticPath(
        timeMillis: Long = System.currentTimeMillis(),
        latitude: Double = 21.0285,
        longitude: Double = 105.8542,
        stepDeg: Int = 10
    ): List<Pair<Float, Float>> {
        val jd = (timeMillis.toDouble() / 86400000.0) + 2440587.5
        val d = jd - 2451543.5
        val eclRad = Math.toRadians(23.4393 - 3.563e-7 * d)

        val gmstDeg = normalizeDeg(280.46061837 + 360.98564736629 * d)
        val lstRad = Math.toRadians(normalizeDeg(gmstDeg + longitude))
        val latRad = Math.toRadians(latitude)

        val points = mutableListOf<Pair<Float, Float>>()
        for (lonDeg in 0 until 360 step stepDeg) {
            val lonRad = Math.toRadians(lonDeg.toDouble())
            val xg = cos(lonRad)
            val yg = sin(lonRad)
            val zg = 0.0

            val xeq = xg
            val yeq = yg * cos(eclRad) - zg * sin(eclRad)
            val zeq = yg * sin(eclRad) + zg * cos(eclRad)

            val raRad = atan2(yeq, xeq)
            val decRad = atan2(zeq, sqrt(xeq * xeq + yeq * yeq))
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)
            points.add(alt to az)
        }
        return points
    }

    private fun eqToHor(decRad: Double, haRad: Double, latRad: Double): Pair<Float, Float> {
        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
        val altDeg = Math.toDegrees(altRad).toFloat()

        val cosAz = (sin(decRad) - sinAlt * sin(latRad)) / (cos(altRad) * cos(latRad))
        val sinAz = -cos(decRad) * sin(haRad) / cos(altRad)
        var azDeg = Math.toDegrees(atan2(sinAz, cosAz)).toFloat()
        if (azDeg < 0f) azDeg += 360f

        return altDeg to azDeg
    }
}

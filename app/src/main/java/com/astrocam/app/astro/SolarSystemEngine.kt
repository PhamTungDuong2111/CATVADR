package com.astrocam.app.astro

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * Paul Schlyter & Jean Meeus astronomical algorithms to calculate real-time apparent coordinates
 * (Altitude & Azimuth) of Solar System celestial bodies from an Earth observer location (Topocentric Coordinates).
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
            0 -> "Aries" to "♈"
            1 -> "Taurus" to "♉"
            2 -> "Gemini" to "♊"
            3 -> "Cancer" to "♋"
            4 -> "Leo" to "♌"
            5 -> "Virgo" to "♍"
            6 -> "Libra" to "♎"
            7 -> "Scorpio" to "♏"
            8 -> "Sagittarius" to "♐"
            9 -> "Capricorn" to "♑"
            10 -> "Aquarius" to "♒"
            11 -> "Pisces" to "♓"
            else -> "Aries" to "♈"
        }
    }

    /**
     * Compute positions of all celestial bodies for timeMillis at (latitude, longitude)
     */
    fun calculateAllBodies(
        timeMillis: Long = System.currentTimeMillis(),
        latitude: Double = 21.0285, // Default Hanoi
        longitude: Double = 105.8542
    ): List<PlanetInfo> {
        val jd = (timeMillis.toDouble() / 86400000.0) + 2440587.5
        val d = jd - 2451543.5 // Days from epoch 1999 Dec 31.0 (Paul Schlyter orbital elements)
        val dJ2000 = jd - 2451545.0 // Epoch J2000.0 IAU standard (2000 Jan 1.5 TD)

        val eclDeg = 23.4393 - 3.563e-7 * d
        val eclRad = Math.toRadians(eclDeg)

        // Calculate Sun position
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

        // Greenwich Sidereal Time (GMST) and Local Sidereal Time (LST)
        val gmstDeg = normalizeDeg(280.46061837 + 360.98564736629 * dJ2000)
        val lstDeg = normalizeDeg(gmstDeg + longitude)
        val lstRad = Math.toRadians(lstDeg)
        val latRad = Math.toRadians(latitude)

        val results = mutableListOf<PlanetInfo>()

        // 1. SUN
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
                    nameVi = "Sun",
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
                    descriptionVi = "Central star of our Solar System, providing heat and light essential for life on Earth.",
                    observationTipVi = "Warning: NEVER look directly at the Sun with binoculars, cameras, or telescopes without a certified solar filter.",
                    diameterKm = 1392700L
                )
            )
        }

        // 2. MOON
        run {
            val nMoon = normalizeDeg(125.1228 - 0.0529538083 * d)
            val iMoon = 5.1454
            val wMoon = normalizeDeg(318.0634 + 0.1643573223 * d)
            val aMoon = 60.2666 // Earth radii
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
                phasePct < 5 -> "New Moon"
                elongationDeg < 90 -> "Waxing Crescent ($phasePct%)"
                elongationDeg < 170 -> "First Quarter ($phasePct%)"
                elongationDeg < 190 -> "Full Moon ($phasePct%)"
                elongationDeg < 270 -> "Waning Gibbous ($phasePct%)"
                else -> "Waning Crescent ($phasePct%)"
            }

            results.add(
                PlanetInfo(
                    id = "MOON",
                    nameVi = "Moon",
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
                    descriptionVi = "Earth's only natural satellite. Current lunar phase: $phaseTitle.",
                    observationTipVi = "The optimal time to view craters and mountain ridges is along the lunar day-night terminator line.",
                    diameterKm = 3474L,
                    phasePercent = phasePct
                )
            )
        }

        // 3. PLANETS (Mercury, Venus, Mars, Jupiter, Saturn, Uranus, Neptune)
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
                nameVi = "Mercury",
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
                descriptionVi = "Closest planet to the Sun and smallest in the Solar System. Temperatures range from -180°C at night to 430°C by day.",
                tipVi = "Best observed during twilight at dawn or dusk when reaching greatest elongation."
            ),
            PlanetParams(
                id = "VENUS",
                nameVi = "Venus",
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
                descriptionVi = "Third brightest celestial object in the sky after the Sun and Moon. Often called the Morning Star or Evening Star.",
                tipVi = "Easily visible to the naked eye even during dusk due to strong light reflection from thick sulfuric acid clouds."
            ),
            PlanetParams(
                id = "MARS",
                nameVi = "Mars",
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
                descriptionVi = "The Red Planet, 4th from the Sun. Home to Olympus Mons (21.9 km high) and the magnificent Valles Marineris canyon.",
                tipVi = "Appears with a characteristic reddish-orange glow, easy to spot with the naked eye against background stars."
            ),
            PlanetParams(
                id = "JUPITER",
                nameVi = "Jupiter",
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
                descriptionVi = "Largest planet in the Solar System, more massive than all other planets combined. Famous for its iconic Great Red Spot.",
                tipVi = "Standard binoculars easily reveal the four large Galilean moons: Io, Europa, Ganymede, and Callisto in a straight line."
            ),
            PlanetParams(
                id = "SATURN",
                nameVi = "Saturn",
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
                descriptionVi = "The Jewel of the Solar System. Breathtaking ring system composed of billions of ice and rock particles orbiting a gas giant.",
                tipVi = "A small telescope (30x magnification or higher) is sufficient to clearly view the rings and giant moon Titan."
            ),
            PlanetParams(
                id = "URANUS",
                nameVi = "Uranus",
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
                descriptionVi = "Ice giant planet with an atmosphere rich in hydrogen, helium, and methane creating a cyan-blue hue. Axially tilted at 97.8°.",
                tipVi = "At the naked-eye limit under pristine dark skies (Bortle 1-2). Binoculars clearly resolve it as a faint bluish dot."
            ),
            PlanetParams(
                id = "NEPTUNE",
                nameVi = "Neptune",
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
                descriptionVi = "The most distant planet in our Solar System. Experiences ferocious supersonic storms with winds clocked at 2,100 km/h.",
                tipVi = "Requires a telescope with 70mm+ aperture to distinguish its small deep blue disc among field stars."
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

            // Geocentric coordinates
            val xg = xh + xs
            val yg = yh + ys
            val zg = zh + zs
            val distAu = sqrt(xg * xg + yg * yg + zg * zg)

            // Convert to Equatorial coordinates
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

        // 4. MILKY WAY & GALACTIC CENTER (Sagittarius A*)
        run {
            // Galactic Center coordinates J2000: RA = 17h 45m 40s (266.417°), Dec = -29° 00' 28" (-29.008°)
            val raRad = Math.toRadians(266.4168)
            val decRad = Math.toRadians(-29.0078)
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)

            results.add(
                PlanetInfo(
                    id = "MILKY_WAY_CORE",
                    nameVi = "Galactic Center (Sagittarius A*)",
                    nameEn = "Galactic Center",
                    symbol = "🌌",
                    color = Color(0xFFFF80AB),
                    glowColor = Color(0xFFE040FB),
                    altitude = alt,
                    azimuth = az,
                    distanceAu = 26000.0 * 63241.07, // ~26,000 light-years
                    magnitude = -3.0f,
                    constellationVi = "Sagittarius",
                    constellationSymbol = "♐",
                    type = CelestialBodyType.MILKY_WAY,
                    descriptionVi = "The densest and most luminous region of the Milky Way, hosting the supermassive black hole Sagittarius A* (4 million solar masses).",
                    observationTipVi = "Prime astrophotography window: Summer & autumn nights (April - September) around new moon. Point camera south when altitude > 20°.",
                    diameterKm = 100000L * 9460730472580L // ~100,000 ly
                )
            )
        }

        // 5. PROMINENT DEEP-SKY TARGETS & ALIGNMENT STARS
        data class DeepSkyTarget(
            val id: String,
            val nameVi: String,
            val nameEn: String,
            val symbol: String,
            val color: Color,
            val glowColor: Color,
            val raDeg: Double,
            val decDeg: Double,
            val distLy: Double,
            val mag: Float,
            val constVi: String,
            val constSym: String,
            val type: CelestialBodyType,
            val descVi: String,
            val tipVi: String
        )

        val deepSkyList = listOf(
            DeepSkyTarget(
                id = "ANDROMEDA",
                nameVi = "Andromeda Galaxy (M31)",
                nameEn = "Andromeda Galaxy",
                symbol = "🌀",
                color = Color(0xFF80DEEA),
                glowColor = Color(0xFF00E5FF),
                raDeg = 10.6847,
                decDeg = 41.2687,
                distLy = 2500000.0,
                mag = 3.44f,
                constVi = "Andromeda",
                constSym = "And",
                type = CelestialBodyType.DEEP_SKY,
                descVi = "Nearest major spiral galaxy to the Milky Way, containing over 1 trillion stars. Visible to the naked eye under dark skies.",
                tipVi = "Use focal lengths between 85mm - 200mm and stacking exposure mode to capture spiral disc details."
            ),
            DeepSkyTarget(
                id = "ORION_NEBULA",
                nameVi = "Orion Nebula (M42)",
                nameEn = "Orion Nebula",
                symbol = "✨",
                color = Color(0xFFFF8A80),
                glowColor = Color(0xFFFF5252),
                raDeg = 83.8221,
                decDeg = -5.3911,
                distLy = 1344.0,
                mag = 4.0f,
                constVi = "Orion",
                constSym = "Ori",
                type = CelestialBodyType.DEEP_SKY,
                descVi = "Brightest diffuse nebula in the sky, located in the sword hanging from Orion's Belt. A vibrant stellar nursery.",
                tipVi = "The glowing pinkish-purple gas cloud can be captured even with smartphone cameras on clear nights."
            ),
            DeepSkyTarget(
                id = "PLEIADES",
                nameVi = "Pleiades (M45)",
                nameEn = "Pleiades (Seven Sisters)",
                symbol = "💎",
                color = Color(0xFF82B1FF),
                glowColor = Color(0xFF448AFF),
                raDeg = 56.75,
                decDeg = 24.11,
                distLy = 444.0,
                mag = 1.6f,
                constVi = "Taurus",
                constSym = "Tau",
                type = CelestialBodyType.DEEP_SKY,
                descVi = "Magnificent open star cluster surrounded by a luminous blue reflection nebula dust cloud.",
                tipVi = "Prominent in winter skies. The naked eye can readily count 6-7 brightest cluster stars."
            ),
            DeepSkyTarget(
                id = "SIRIUS",
                nameVi = "Sirius",
                nameEn = "Sirius",
                symbol = "⭐",
                color = Color(0xFFFFFFFF),
                glowColor = Color(0xFF80D8FF),
                raDeg = 101.287,
                decDeg = -16.716,
                distLy = 8.6,
                mag = -1.46f,
                constVi = "Canis Major",
                constSym = "CMa",
                type = CelestialBodyType.STAR,
                descVi = "Brightest star in Earth's night sky, situated in the constellation Canis Major.",
                tipVi = "Intense sparkling blue-white light serves as an ideal focusing and calibration target."
            ),
            DeepSkyTarget(
                id = "POLARIS",
                nameVi = "North Star (Polaris)",
                nameEn = "North Star (Polaris)",
                symbol = "🌟",
                color = Color(0xFFFFF9C4),
                glowColor = Color(0xFFFFD54F),
                raDeg = 37.954,
                decDeg = 89.264,
                distLy = 433.0,
                mag = 1.98f,
                constVi = "Ursa Minor",
                constSym = "UMi",
                type = CelestialBodyType.STAR,
                descVi = "Marks the North Celestial Pole, around which all northern constellations revolve.",
                tipVi = "Crucial for polar alignment when shooting star trails or using an equatorial tracker."
            )
        )

        for (t in deepSkyList) {
            val raRad = Math.toRadians(t.raDeg)
            val decRad = Math.toRadians(t.decDeg)
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)

            results.add(
                PlanetInfo(
                    id = t.id,
                    nameVi = t.nameVi,
                    nameEn = t.nameEn,
                    symbol = t.symbol,
                    color = t.color,
                    glowColor = t.glowColor,
                    altitude = alt,
                    azimuth = az,
                    distanceAu = t.distLy * 63241.07,
                    magnitude = t.mag,
                    constellationVi = t.constVi,
                    constellationSymbol = t.constSym,
                    type = t.type,
                    descriptionVi = t.descVi,
                    observationTipVi = t.tipVi,
                    diameterKm = (t.distLy * 1000).toLong()
                )
            )
        }

        return results
    }

    /**
     * Coordinates of a point along the Milky Way band
     */
    data class MilkyWayPoint(
        val altitude: Float,
        val azimuth: Float,
        val galacticLonDeg: Int,
        val labelVi: String? = null
    )

    /**
     * Calculate points along the Galactic Equator (b=0°)
     * using IAU J2000 rotation matrix between Galactic and Equatorial coordinates.
     */
    fun calculateMilkyWayPath(
        timeMillis: Long = System.currentTimeMillis(),
        latitude: Double = 21.0285,
        longitude: Double = 105.8542,
        stepDeg: Int = 4
    ): List<MilkyWayPoint> {
        val jd = (timeMillis.toDouble() / 86400000.0) + 2440587.5
        val dJ2000 = jd - 2451545.0
        val gmstDeg = normalizeDeg(280.46061837 + 360.98564736629 * dJ2000)
        val lstRad = Math.toRadians(normalizeDeg(gmstDeg + longitude))
        val latRad = Math.toRadians(latitude)

        // IAU J2000 Rotation matrix: Galactic (b=0) -> Equatorial
        val r00 = -0.05540621; val r01 =  0.49405101
        val r10 = -0.87295905; val r11 = -0.44576694
        val r20 = -0.48463662; val r21 =  0.74646194

        val points = mutableListOf<MilkyWayPoint>()
        for (lDeg in 0 until 360 step stepDeg) {
            val lRad = Math.toRadians(lDeg.toDouble())
            val xGal = cos(lRad)
            val yGal = sin(lRad)

            val xEq = r00 * xGal + r01 * yGal
            val yEq = r10 * xGal + r11 * yGal
            val zEq = r20 * xGal + r21 * yGal

            val raRad = atan2(yEq, xEq)
            val decRad = asin(zEq.coerceIn(-1.0, 1.0))
            val haRad = lstRad - raRad
            val (alt, az) = eqToHor(decRad, haRad, latRad)

            val label = when (lDeg) {
                0 -> "Galactic Center (Core)"
                76 -> "Milky Way • Cygnus"
                180 -> "Milky Way • Auriga"
                280 -> "Milky Way • Carina"
                else -> null
            }

            points.add(MilkyWayPoint(alt, az, lDeg, label))
        }
        return points
    }

    /**
     * Calculate points on the Ecliptic line to render the Solar System orbital plane
     */
    fun calculateEclipticPath(
        timeMillis: Long = System.currentTimeMillis(),
        latitude: Double = 21.0285,
        longitude: Double = 105.8542,
        stepDeg: Int = 10
    ): List<Pair<Float, Float>> {
        val jd = (timeMillis.toDouble() / 86400000.0) + 2440587.5
        val d = jd - 2451543.5
        val dJ2000 = jd - 2451545.0
        val eclRad = Math.toRadians(23.4393 - 3.563e-7 * d)

        val gmstDeg = normalizeDeg(280.46061837 + 360.98564736629 * dJ2000)
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

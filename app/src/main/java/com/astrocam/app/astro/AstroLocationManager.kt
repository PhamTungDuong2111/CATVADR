package com.astrocam.app.astro

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AstroLocation(
    val latitude: Double = 21.0285,   // Hanoi, Vietnam default
    val longitude: Double = 105.8542,
    val altitude: Double = 10.0,
    val locationName: String = "Hanoi, Vietnam",
    val isGpsActive: Boolean = false,
    val magneticDeclination: Float = -1.5f // Northern region ~ -1.5°
)

class AstroLocationManager(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow(AstroLocation())
    val currentLocation: StateFlow<AstroLocation> = _currentLocation.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                // Try to get last known location
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLast = lastGps ?: lastNet

                if (bestLast != null) {
                    updateFromLocation(bestLast)
                }

                // Register location updates if moving
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        60000L, // Every 1 minute
                        50f,    // 50 meters
                        this
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        60000L,
                        100f,
                        this
                    )
                }
            } catch (e: Exception) {
                // Fallback to default coordinates
            }
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {}
    }

    private fun updateFromLocation(loc: Location) {
        val geoField = try {
            android.hardware.GeomagneticField(
                loc.latitude.toFloat(),
                loc.longitude.toFloat(),
                loc.altitude.toFloat(),
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
        val decl = geoField?.declination ?: -1.5f

        _currentLocation.value = AstroLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            locationName = String.format(java.util.Locale.US, "%.2f°N, %.2f°E", loc.latitude, loc.longitude),
            isGpsActive = true,
            magneticDeclination = decl
        )
    }

    override fun onLocationChanged(location: Location) {
        updateFromLocation(location)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

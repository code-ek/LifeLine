package com.lifeline.app.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * GPS/GNSS position for SOS alerts. Uses the satellite provider directly so it works with no
 * network; falls back to any cached fix. Runs only while the emergency screen asks for it, and
 * is independent of the chat app's location consent gate.
 */
internal class EmergencyLocationSource(context: Context) {

    companion object {
        private const val TAG = "EmergencyLocation"
        private const val UPDATE_INTERVAL_MS = 3_000L
        private const val MIN_DISTANCE_M = 3f
    }

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean =
        try { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true } catch (_: Exception) { false }

    @SuppressLint("MissingPermission")
    fun start(onLocation: (Location) -> Unit) {
        stop()
        val manager = locationManager ?: return
        if (!hasPermission()) return
        try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let(onLocation)
        } catch (e: Exception) {
            Log.w(TAG, "No cached fix: ${e.message}")
        }
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = newListener
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(
                        provider, UPDATE_INTERVAL_MS, MIN_DISTANCE_M, newListener, Looper.getMainLooper()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider $provider unavailable: ${e.message}")
            }
        }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        try { locationManager?.removeUpdates(current) } catch (_: Exception) { }
    }
}

internal fun Location.toSosLocation(): SosLocation = SosLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null
)

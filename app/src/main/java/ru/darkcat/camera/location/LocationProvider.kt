package ru.darkcat.camera.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Non-blocking location cache. Capture never waits for a GPS fix. */
class LocationProvider(context: Context) {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var latest: Location? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            synchronized(this@LocationProvider) { latest = location }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        synchronized(this) {
            if (latest == null) latest = bestLastKnownLocation()
        }
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 2f, listener)
        }
        runCatching {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 5f, listener)
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
    }

    fun snapshot(): Location? = synchronized(this) { latest?.let(::Location) }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(): Location? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

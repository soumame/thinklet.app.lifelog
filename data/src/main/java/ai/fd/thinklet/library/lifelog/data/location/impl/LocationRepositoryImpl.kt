package ai.fd.thinklet.library.lifelog.data.location.impl

import ai.fd.thinklet.library.lifelog.data.location.LocationRepository
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {

    companion object {
        private const val TAG = "LocationRepositoryImpl"
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<Location?> = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted")
                return@withContext Result.success(null)
            }

            // Try to get last known location from various providers
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            var bestAccuracy = Float.MAX_VALUE

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    try {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            // Use the location with the best accuracy
                            if (location.hasAccuracy() && location.accuracy < bestAccuracy) {
                                bestLocation = location
                                bestAccuracy = location.accuracy
                            } else if (bestLocation == null) {
                                bestLocation = location
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get location from provider: $provider", e)
                    }
                }
            }

            if (bestLocation != null) {
                Log.d(TAG, "Got location: lat=${bestLocation.latitude}, lon=${bestLocation.longitude}, accuracy=${bestLocation.accuracy}m")
            } else {
                Log.d(TAG, "No location available")
            }

            Result.success(bestLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location", e)
            Result.failure(e)
        }
    }
}
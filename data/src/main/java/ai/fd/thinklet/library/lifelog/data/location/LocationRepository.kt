package ai.fd.thinklet.library.lifelog.data.location

import android.location.Location

interface LocationRepository {
    suspend fun getCurrentLocation(): Result<Location?>
    fun hasLocationPermission(): Boolean
}
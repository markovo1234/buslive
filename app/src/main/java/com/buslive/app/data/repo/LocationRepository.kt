package com.buslive.app.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.di.ApplicationScope
import com.buslive.app.domain.model.GeoPoint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class UserLocation(val point: GeoPoint, val accuracyM: Float, val speedMps: Float, val timeMs: Long)

/** FusedLocationProvider wrapper. Fallback center when no permission: last saved spot or Tel Aviv. */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val _last = MutableStateFlow<UserLocation?>(null)
    val last: StateFlow<UserLocation?> = _last.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Fresh-ish location: cached fix if recent, else one current fix (max ~8s). */
    @SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        _last.value?.let { if (System.currentTimeMillis() - it.timeMs < 60_000) return it.point }
        val loc = runCatching {
            withTimeoutOrNull(8_000) {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setMaxUpdateAgeMillis(60_000)
                        .build(),
                    null,
                ).await()
            } ?: client.lastLocation.await()
        }.getOrNull() ?: return null
        val u = UserLocation(GeoPoint(loc.latitude, loc.longitude), loc.accuracy, loc.speed, loc.time)
        publish(u)
        return u.point
    }

    /** Best-known point without waiting: last fix, saved spot, or Tel Aviv center. */
    suspend fun fallbackCenter(): GeoPoint =
        _last.value?.point ?: settings.current().lastLocation ?: TEL_AVIV

    /** Continuous updates. Interval / priority are chosen by the caller (battery-aware). */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long, highAccuracy: Boolean): Flow<UserLocation> = callbackFlow {
        if (!hasPermission()) { close(); return@callbackFlow }
        val request = LocationRequest.Builder(
            if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs,
        ).setMinUpdateIntervalMillis(intervalMs / 2).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val u = UserLocation(GeoPoint(loc.latitude, loc.longitude), loc.accuracy, loc.speed, loc.time)
                    publish(u)
                    trySend(u)
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun publish(u: UserLocation) {
        val prev = _last.value
        _last.value = u
        if (prev == null || System.currentTimeMillis() - prev.timeMs > 5 * 60_000) {
            scope.launch { settings.setLastLocation(u.point) }
        }
    }

    companion object {
        val TEL_AVIV = GeoPoint(32.0809, 34.7806)
    }
}

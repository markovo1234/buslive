package com.buslive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buslive.app.data.prefs.AppSettings
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.data.prefs.ThemeMode
import com.buslive.app.data.repo.LocationRepository
import com.buslive.app.data.repo.OfflineMapRepository
import com.buslive.app.domain.planner.OtpTripPlanner
import com.buslive.app.service.SyncMonitor
import com.buslive.app.service.SyncState
import com.buslive.app.ui.map.BusMapController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val syncMonitor: SyncMonitor,
    private val offline: OfflineMapRepository,
    private val location: LocationRepository,
    private val client: OkHttpClient,
    private val json: Json,
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val sync: StateFlow<SyncState> = syncMonitor.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.IDLE)
    val offlineProgress = offline.progress

    sealed interface OtpTest {
        data object Idle : OtpTest
        data object Running : OtpTest
        data class Ok(val options: Int) : OtpTest
        data class Failed(val message: String) : OtpTest
    }

    private val _otpTest = MutableStateFlow<OtpTest>(OtpTest.Idle)
    val otpTest: StateFlow<OtpTest> = _otpTest.asStateFlow()

    private val _tomtomTest = MutableStateFlow<OtpTest>(OtpTest.Idle)
    val tomtomTest: StateFlow<OtpTest> = _tomtomTest.asStateFlow()

    fun setTomtomKey(key: String) = viewModelScope.launch {
        settings.setTomtomKey(key)
        _tomtomTest.value = OtpTest.Idle
    }

    /** Fetches one Tel Aviv map tile with the key. */
    fun testTomtom(key: String) = viewModelScope.launch {
        _tomtomTest.value = OtpTest.Running
        val url = com.buslive.app.ui.map.MapSource.tileUrl(key.trim(), dark = false)
            .replace("{z}", "10").replace("{x}", "612").replace("{y}", "415")
        _tomtomTest.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful && r.header("Content-Type").orEmpty().startsWith("image")) OtpTest.Ok(1)
                    else OtpTest.Failed("HTTP ${r.code}")
                }
            }.getOrElse { OtpTest.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setOtpUrl(url: String) = viewModelScope.launch {
        settings.setOtpBaseUrl(url)
        _otpTest.value = OtpTest.Idle
    }

    /** Plans a short sample trip in Tel Aviv against the OTP server. */
    fun testOtp(url: String) = viewModelScope.launch {
        _otpTest.value = OtpTest.Running
        val c = client.newBuilder().readTimeout(30, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()
        _otpTest.value = runCatching {
            OtpTripPlanner(url.trim(), c, json).plan(
                com.buslive.app.domain.model.GeoPoint(32.0730, 34.7925),
                com.buslive.app.domain.model.GeoPoint(32.1133, 34.8044),
                Instant.now(),
            ).size
        }.fold({ OtpTest.Ok(it) }, { OtpTest.Failed(it.message ?: it.javaClass.simpleName) })
    }

    fun syncNow() = syncMonitor.syncNow(force = true)

    fun downloadCity(dark: Boolean) = viewModelScope.launch {
        val center = location.current() ?: location.fallbackCenter()
        offline.download(center, if (dark) BusMapController.STYLE_DARK else BusMapController.STYLE_LIGHT)
    }

    fun deleteOffline() = offline.deleteAll()
}

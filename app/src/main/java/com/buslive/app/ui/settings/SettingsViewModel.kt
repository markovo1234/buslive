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

package com.buslive.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buslive.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val otpBaseUrl: String = "",
    val widgetStopCode: Int? = null,
    val lastSyncDate: String? = null,
    val lastSyncAt: Long = 0L,
    val lastLocation: GeoPoint? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val OTP_URL = stringPreferencesKey("otp_base_url")
        val WIDGET_STOP = intPreferencesKey("widget_stop_code")
        val SYNC_DATE = stringPreferencesKey("last_sync_date")
        val SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val lat = p[Keys.LAST_LAT]
        val lon = p[Keys.LAST_LON]
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            otpBaseUrl = p[Keys.OTP_URL].orEmpty(),
            widgetStopCode = p[Keys.WIDGET_STOP],
            lastSyncDate = p[Keys.SYNC_DATE],
            lastSyncAt = p[Keys.SYNC_AT] ?: 0L,
            lastLocation = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setOtpBaseUrl(url: String) = context.dataStore.edit { it[Keys.OTP_URL] = url.trim() }

    suspend fun setWidgetStop(code: Int?) = context.dataStore.edit {
        if (code == null) it.remove(Keys.WIDGET_STOP) else it[Keys.WIDGET_STOP] = code
    }

    suspend fun setSynced(date: String, at: Long) = context.dataStore.edit {
        it[Keys.SYNC_DATE] = date
        it[Keys.SYNC_AT] = at
    }

    suspend fun setLastLocation(p: GeoPoint) = context.dataStore.edit {
        it[Keys.LAST_LAT] = p.lat
        it[Keys.LAST_LON] = p.lon
    }
}

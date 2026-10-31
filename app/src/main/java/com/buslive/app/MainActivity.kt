package com.buslive.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.buslive.app.data.ConnectivityMonitor
import com.buslive.app.data.prefs.AppSettings
import com.buslive.app.ui.map.LocalMapSource
import com.buslive.app.ui.map.MapSource
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.data.prefs.ThemeMode
import com.buslive.app.ui.navigation.AppRoot
import com.buslive.app.ui.theme.BusLiveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var connectivity: ConnectivityMonitor

    /** Bumped when a notification asks to open live directions. */
    private val openGuidanceRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status / navigation bar icons follow the app theme, also when it is forced in Settings.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            val online by connectivity.online.collectAsStateWithLifecycle()
            // Key from Settings wins; else the one built in (local.properties / CI secret).
            val tomtomKey = settings.tomtomKey.ifBlank { BuildConfig.TOMTOM_API_KEY }
            BusLiveTheme(mode = settings.themeMode) {
                CompositionLocalProvider(LocalMapSource provides MapSource(tomtomKey, online)) {
                    AppRoot(openGuidanceRequest = openGuidanceRequest.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_GUIDANCE) openGuidanceRequest.intValue += 1
    }

    companion object {
        const val ACTION_OPEN_GUIDANCE = "com.buslive.app.OPEN_GUIDANCE"
        private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}

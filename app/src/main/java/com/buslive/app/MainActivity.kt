package com.buslive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buslive.app.data.prefs.AppSettings
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.ui.navigation.AppRoot
import com.buslive.app.ui.theme.BusLiveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Bumped when a notification asks to open live directions. */
    private val openGuidanceRequest = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            BusLiveTheme(mode = settings.themeMode) {
                AppRoot(openGuidanceRequest = openGuidanceRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_GUIDANCE) openGuidanceRequest.value += 1
    }

    companion object {
        const val ACTION_OPEN_GUIDANCE = "com.buslive.app.OPEN_GUIDANCE"
    }
}

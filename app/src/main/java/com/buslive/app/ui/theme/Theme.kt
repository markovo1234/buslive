package com.buslive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.buslive.app.data.prefs.ThemeMode

val Brand = Color(0xFF0E7C86)
val LiveGreen = Color(0xFF1B9E4B)
val LiveGreenDark = Color(0xFF5BD68A)
val Warning = Color(0xFFE65100)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F7),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF8FAFA),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4F4),
    surfaceContainer = Color(0xFFECEEEF),
    surfaceContainerHigh = Color(0xFFE6E9E9),
    surfaceContainerHighest = Color(0xFFE1E3E3),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4DB),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9CF0F7),
    secondary = Color(0xFFB0CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF171D1D),
    surfaceContainer = Color(0xFF1B2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    error = Color(0xFFFFB4AB),
)

/** True when the app is drawing in dark mode (system or forced). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun BusLiveTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}

@Composable
fun liveColor(): Color = if (LocalDarkTheme.current) LiveGreenDark else LiveGreen

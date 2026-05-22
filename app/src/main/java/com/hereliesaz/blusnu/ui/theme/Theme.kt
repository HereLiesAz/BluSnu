package com.hereliesaz.blusnu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark Theme Color Scheme.
// Uses Black background for OLED efficiency and "Hacker" aesthetic.
// All surface variants are explicitly set to ensure consistency.
private val DarkColorScheme = darkColorScheme(
    primary = BluSnuBlue,
    secondary = BluSnuBlue,
    tertiary = BluSnuBlue,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF333333)
)

/**
 * The main Theme composable for the application.
 * Wraps the MaterialTheme and handles dynamic colors (Android 12+).
 */
@Composable
fun BluSnuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use dark theme — the app is designed with a dark/OLED aesthetic.
    val colorScheme = DarkColorScheme

    // Set status bar appearance.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Force status bar icons to be light (for dark background) or dark (for light background).
            // Here we hardcode to light status bars (dark icons = false) because of the black theme.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

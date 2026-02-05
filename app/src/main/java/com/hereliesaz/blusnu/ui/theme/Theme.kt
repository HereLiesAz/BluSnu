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
private val DarkColorScheme = darkColorScheme(
    primary = BluSnuBlue,
    secondary = BluSnuBlue,
    tertiary = BluSnuBlue,
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

// Light Theme Color Scheme (less used, but good for accessibility).
private val LightColorScheme = lightColorScheme(
    primary = BluSnuBlue,
    secondary = BluSnuBlue,
    tertiary = BluSnuBlue,
    background = Color.Black,
    surface = Color.Black, // Keeping dark-ish theme even in light mode? No, this overrides to Black.
                           // This suggests a "Dark Mode by Default" design choice.
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
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
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

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

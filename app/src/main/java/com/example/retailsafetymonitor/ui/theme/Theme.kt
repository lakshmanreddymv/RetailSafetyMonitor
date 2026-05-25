package com.example.retailsafetymonitor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    surface = Surface,
    background = Background
)

/**
 * Root Material 3 theme for the Retail Safety Monitor app.
 *
 * Uses Android 12+ dynamic color ([dynamicColor] = true by default) when available,
 * falling back to the purple seed palette defined in [Color.kt] for older devices.
 * Hazard severity colors in overlays and badges are defined independently in
 * [SeverityColors.kt] and are not affected by the theme's color scheme.
 *
 * @param darkTheme True to apply the dark color scheme.
 * @param dynamicColor True to use Android 12+ dynamic color (follows the wallpaper palette).
 * @param content The composable content tree to apply the theme to.
 */
@Composable
fun RetailSafetyMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
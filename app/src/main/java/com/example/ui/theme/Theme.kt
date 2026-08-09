package com.example.ui.theme

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
    primary = MinimalDarkPrimary,
    onPrimary = MinimalDarkOnPrimary,
    primaryContainer = MinimalDarkPrimaryContainer,
    onPrimaryContainer = MinimalDarkOnPrimaryContainer,
    secondary = MinimalDarkSecondary,
    onSecondary = MinimalDarkOnSecondary,
    secondaryContainer = MinimalDarkSecondaryContainer,
    onSecondaryContainer = MinimalDarkOnSecondaryContainer,
    tertiary = MinimalDarkTertiary,
    onTertiary = MinimalDarkOnTertiary,
    tertiaryContainer = MinimalDarkTertiaryContainer,
    onTertiaryContainer = MinimalDarkOnTertiaryContainer,
    background = MinimalDarkBackground,
    surface = MinimalDarkSurface,
    surfaceVariant = MinimalDarkSurfaceVariant,
    onBackground = MinimalDarkOnSurface,
    onSurface = MinimalDarkOnSurface,
    onSurfaceVariant = MinimalDarkOnSurfaceVariant,
    outline = MinimalDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalLightPrimary,
    onPrimary = MinimalLightOnPrimary,
    primaryContainer = MinimalLightPrimaryContainer,
    onPrimaryContainer = MinimalLightOnPrimaryContainer,
    secondary = MinimalLightSecondary,
    onSecondary = MinimalLightOnSecondary,
    secondaryContainer = MinimalLightSecondaryContainer,
    onSecondaryContainer = MinimalLightOnSecondaryContainer,
    tertiary = MinimalLightTertiary,
    onTertiary = MinimalLightOnTertiary,
    tertiaryContainer = MinimalLightTertiaryContainer,
    onTertiaryContainer = MinimalLightOnTertiaryContainer,
    background = MinimalLightBackground,
    surface = MinimalLightSurface,
    surfaceVariant = MinimalLightSurfaceVariant,
    onBackground = MinimalLightOnSurface,
    onSurface = MinimalLightOnSurface,
    onSurfaceVariant = MinimalLightOnSurfaceVariant,
    outline = MinimalLightOutline
)

@Composable
fun WorkLogTheme(
    themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

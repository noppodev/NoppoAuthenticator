package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LuminousPrimary,
    onPrimary = LuminousOnPrimary,
    primaryContainer = LuminousPrimaryContainer,
    onPrimaryContainer = LuminousOnPrimaryContainer,
    secondary = LuminousSecondary,
    onSecondary = LuminousOnSecondary,
    secondaryContainer = LuminousSecondaryContainer,
    onSecondaryContainer = LuminousOnSecondaryContainer,
    tertiary = LuminousTertiary,
    onTertiary = LuminousOnTertiary,
    background = Color(0xFF08121E), // Ultra-deep glacier slate shadow
    onBackground = Color(0xFFEAF1FF),
    surface = Color(0xFF0F1E30),
    onSurface = Color(0xFFEAF1FF),
    surfaceVariant = Color(0xFF213145),
    onSurfaceVariant = Color(0xFFC4C5D9),
    outline = Color(0xFF747688),
    outlineVariant = Color(0xFF434656)
)

private val LightColorScheme = lightColorScheme(
    primary = LuminousPrimary,
    onPrimary = LuminousOnPrimary,
    primaryContainer = LuminousPrimaryContainer,
    onPrimaryContainer = LuminousOnPrimaryContainer,
    secondary = LuminousSecondary,
    onSecondary = LuminousOnSecondary,
    secondaryContainer = LuminousSecondaryContainer,
    onSecondaryContainer = LuminousOnSecondaryContainer,
    tertiary = LuminousTertiary,
    onTertiary = LuminousOnTertiary,
    background = LuminousBackground,
    onBackground = LuminousOnBackground,
    surface = LuminousSurface,
    onSurface = LuminousOnSurface,
    surfaceVariant = LuminousSurfaceVariant,
    onSurfaceVariant = LuminousOnSurfaceVariant,
    outline = LuminousOutline,
    outlineVariant = LuminousOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep standard fallback
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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

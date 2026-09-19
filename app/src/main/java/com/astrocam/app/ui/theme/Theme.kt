package com.astrocam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AstroRed,
    secondary = AstroRedLight,
    background = AstroBlack,
    surface = AstroSurface,
    surfaceVariant = AstroSurfaceVariant,
    onPrimary = AstroBlack,
    onSecondary = AstroBlack,
    onBackground = AstroTextPrimary,
    onSurface = AstroTextPrimary
)

@Composable
fun AstroCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

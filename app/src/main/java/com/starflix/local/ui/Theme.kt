package com.starflix.local.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StarFlixColors = darkColorScheme(
    primary = Color(0xFFF7F4EF),
    onPrimary = Color(0xFF11100F),
    background = Color(0xFF050505),
    onBackground = Color(0xFFF7F4EF),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFF1EDE9),
    surfaceVariant = Color(0xFF211E1C),
    onSurfaceVariant = Color(0xFFAAA39E),
    outline = Color(0xFF3B3734)
)

@Composable
fun StarFlixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StarFlixColors,
        content = content
    )
}

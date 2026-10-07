package com.srikads.codewall.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF1E1F29),
    secondary = Color(0xFF8BE9FD),
    tertiary = Color(0xFF50FA7B),
    background = Color(0xFF1E1F29),
    surface = Color(0xFF1E1F29),
    surfaceContainer = Color(0xFF282A36),
    surfaceVariant = Color(0xFF343746),
    onBackground = Color(0xFFF8F8F2),
    onSurface = Color(0xFFF8F8F2),
)

@Composable
fun CodeWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

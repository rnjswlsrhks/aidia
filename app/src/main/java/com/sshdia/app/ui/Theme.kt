package com.sshdia.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF38BDF8)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0F172A),
    surface = Color(0xFF111827)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7)
)

@Composable
fun SshdiaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

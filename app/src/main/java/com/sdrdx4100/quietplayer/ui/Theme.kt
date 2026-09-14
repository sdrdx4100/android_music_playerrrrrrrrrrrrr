package com.sdrdx4100.quietplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0C0D0D)
val Surface = Color(0xFF141616)
val SurfaceRaised = Color(0xFF1C1F1E)
val PrimaryText = Color(0xFFF1F2EF)
val SecondaryText = Color(0xFFA6AAA7)
val Accent = Color(0xFF91A99F)
val Hairline = Color(0xFF292C2B)

private val QuietColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF101312),
    background = Background,
    onBackground = PrimaryText,
    surface = Surface,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = SecondaryText,
    outline = Hairline,
)

@Composable
fun QuietPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QuietColors, content = content)
}


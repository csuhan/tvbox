package com.juying.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0C0F16)
val SurfaceDark = Color(0xFF141926)
val CardDark = Color(0xFF1A2130)
val Primary = Color(0xFF7C6CFF)
val PrimarySoft = Color(0x337C6CFF)
val Secondary = Color(0xFF22D3EE)
val TextPrimary = Color(0xFFF1F3F9)
val TextSecondary = Color(0xFF9AA3B5)
val Accent = Color(0xFFFFB020)

private val Scheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Secondary,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A3245),
)

@Composable
fun JyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

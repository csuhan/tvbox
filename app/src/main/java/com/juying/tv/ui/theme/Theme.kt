package com.juying.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// —— 品牌色（参考B站粉，略收敛更文艺） ——
val Accent = Color(0xFF3E9C87)
val AccentSoft = Color(0x1A3E9C87)
val AccentDark = Color(0xFF2E7D6D)

// —— 浅色（主） ——
val BgLight = Color(0xFFF4F5F7)
val CardLight = Color(0xFFFFFFFF)
val FillLight = Color(0xFFF1F2F4)      // 搜索框/按钮填充
val TextPrimaryLight = Color(0xFF18191C)
val TextSecondaryLight = Color(0xFF9499A0)
val DividerLight = Color(0xFFE8EAED)
val BadgeLight = Color(0xFFF6F7F9)

// —— 深色（跟随系统） ——
val BgDark = Color(0xFF151618)
val CardDark = Color(0xFF1E2023)
val FillDark = Color(0xFF26282C)
val TextPrimaryDark = Color(0xFFE8E9EB)
val TextSecondaryDark = Color(0xFF8B8F96)
val DividerDark = Color(0xFF2C2E33)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentDark,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = FillLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
)

@Composable
fun JyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}

/** 语义色：跟随当前明暗主题 */
object AppColors {
    val bg: Color @Composable get() = MaterialTheme.colorScheme.background
    val card: Color @Composable get() = MaterialTheme.colorScheme.surface
    val fill: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val textSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val divider: Color @Composable get() = MaterialTheme.colorScheme.outline
    val accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val accentSoft: Color @Composable get() = AccentSoft
}

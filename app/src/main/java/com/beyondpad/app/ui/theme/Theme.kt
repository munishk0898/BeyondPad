package com.beyondpad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object BP {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF121826)
    val SurfaceHigh = Color(0xFF1A2233)
    val Outline = Color(0xFF263048)
    val Accent = Color(0xFF3D8BFF)
    val AccentSoft = Color(0xFF1E3A66)
    val Text = Color(0xFFE8EEF9)
    val TextDim = Color(0xFF8D99B3)
    val Good = Color(0xFF3DD68C)
    val Warn = Color(0xFFFFB547)
    val Bad = Color(0xFFFF5D6C)
    val Taskbar = Color(0xE6101622)
}

private val scheme = darkColorScheme(
    primary = BP.Accent,
    onPrimary = Color.White,
    primaryContainer = BP.AccentSoft,
    onPrimaryContainer = BP.Text,
    secondary = BP.Accent,
    background = BP.Background,
    onBackground = BP.Text,
    surface = BP.Surface,
    onSurface = BP.Text,
    surfaceVariant = BP.SurfaceHigh,
    onSurfaceVariant = BP.TextDim,
    surfaceContainer = BP.Surface,
    surfaceContainerHigh = BP.SurfaceHigh,
    outline = BP.Outline,
    error = BP.Bad,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BeyondPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

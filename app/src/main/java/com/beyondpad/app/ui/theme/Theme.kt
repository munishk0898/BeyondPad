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
    val Background = Color(0xFF0E0F11)
    val Surface = Color(0xFF16181C)
    val SurfaceHigh = Color(0xFF1F2227)
    val Outline = Color(0xFF2A2E35)
    val Accent = Color(0xFF4C8DFF)
    val AccentSoft = Color(0xFF1C2B45)
    val Text = Color(0xFFECEDEF)
    val TextDim = Color(0xFF8A9099)
    val TextFaint = Color(0xFF5A6069)
    val Good = Color(0xFF46C37B)
    val Warn = Color(0xFFE8A33D)
    val Bad = Color(0xFFE5534B)
    val Taskbar = Color(0xF0141619)
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

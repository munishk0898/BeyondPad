package com.beyondpad.app.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.beyondpad.app.Settings

/**
 * Colours are read through getters backed by Compose state, so switching light/dark or the accent
 * recolours every screen at once without restarting.
 */
object BP {
    var light by mutableStateOf(false)
    var accentIndex by mutableIntStateOf(0)

    val accents = listOf(
        "Blue" to Color(0xFF4C8DFF),
        "Teal" to Color(0xFF26B29A),
        "Amber" to Color(0xFFE38B2C),
        "Violet" to Color(0xFF9A72FF),
    )

    private fun pick(dark: Long, lite: Long) = Color(if (light) lite else dark)

    val Background get() = pick(0xFF0E0F11, 0xFFF5F6F8)
    val Surface get() = pick(0xFF16181C, 0xFFFFFFFF)
    val SurfaceHigh get() = pick(0xFF1F2227, 0xFFEBEDF1)
    val Outline get() = pick(0xFF2A2E35, 0xFFDDE0E6)
    val Text get() = pick(0xFFECEDEF, 0xFF15171A)
    val TextDim get() = pick(0xFF8A9099, 0xFF5C636E)
    val TextFaint get() = pick(0xFF5A6069, 0xFF9AA0A9)
    val Accent get() = accents[accentIndex.coerceIn(0, accents.lastIndex)].second
    val AccentSoft get() = Accent.copy(alpha = if (light) 0.14f else 0.22f).compositeOver(Surface)
    val Good get() = pick(0xFF46C37B, 0xFF1F9D57)
    val Warn get() = pick(0xFFE8A33D, 0xFFB86E00)
    val Bad get() = pick(0xFFE5534B, 0xFFD03A32)
    val Taskbar get() = pick(0xF0141619, 0xF0FFFFFF)

    private fun Color.compositeOver(bg: Color) = Color(
        red = red * alpha + bg.red * (1 - alpha),
        green = green * alpha + bg.green * (1 - alpha),
        blue = blue * alpha + bg.blue * (1 - alpha),
        alpha = 1f,
    )

    /** Applies the user's theme choice; call on start, on config change and when settings change. */
    fun apply(activity: Activity, s: Settings) {
        val systemDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        light = when (s.theme) { "light" -> true; "dark" -> false; else -> !systemDark }
        accentIndex = s.accent
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        activity.window.decorView.setBackgroundColor(android.graphics.Color.argb(255, (Background.red * 255).toInt(), (Background.green * 255).toInt(), (Background.blue * 255).toInt()))
    }
}

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
    val scheme = if (BP.light) {
        lightColorScheme(
            primary = BP.Accent, onPrimary = Color.White, primaryContainer = BP.AccentSoft, onPrimaryContainer = BP.Text,
            secondary = BP.Accent, background = BP.Background, onBackground = BP.Text, surface = BP.Surface, onSurface = BP.Text,
            surfaceVariant = BP.SurfaceHigh, onSurfaceVariant = BP.TextDim, surfaceContainer = BP.Surface,
            surfaceContainerHigh = BP.SurfaceHigh, outline = BP.Outline, error = BP.Bad,
        )
    } else {
        darkColorScheme(
            primary = BP.Accent, onPrimary = Color.White, primaryContainer = BP.AccentSoft, onPrimaryContainer = BP.Text,
            secondary = BP.Accent, background = BP.Background, onBackground = BP.Text, surface = BP.Surface, onSurface = BP.Text,
            surfaceVariant = BP.SurfaceHigh, onSurfaceVariant = BP.TextDim, surfaceContainer = BP.Surface,
            surfaceContainerHigh = BP.SurfaceHigh, outline = BP.Outline, error = BP.Bad,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

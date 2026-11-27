package com.beyondpad.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondpad.app.ui.theme.BP

/** Click with a soft spring "press-in", used by every tappable surface so the app feels physical. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, shape: Dp = 12.dp, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(shape))
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
}

@Composable
internal fun Label(text: String) {
    Text(text, color = BP.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BP.Surface), content = content)
}

@Composable
internal fun Item(
    title: String,
    sub: String?,
    dot: Color? = null,
    value: String? = null,
    mono: Boolean = false,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                val c by animateColorAsState(dot, label = "dot")
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = BP.Text,
                    fontSize = if (mono) 12.sp else 15.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                )
                if (sub != null) {
                    Text(sub, color = BP.TextDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (value != null) {
                Spacer(Modifier.width(16.dp))
                Text(value, color = BP.TextDim, fontSize = 14.sp, modifier = Modifier.widthIn(max = 360.dp))
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        }
        if (!last) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(BP.Outline))
    }
}

@Composable
internal fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = BP.Accent, checkedThumbColor = Color.White,
            uncheckedTrackColor = BP.SurfaceHigh, uncheckedThumbColor = BP.TextDim, uncheckedBorderColor = BP.Outline,
        ),
    )

@Composable
internal fun PrimaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick = onClick).background(BP.Accent).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GhostButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.pressable(enabled = enabled, onClick = onClick).background(BP.SurfaceHigh).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = BP.Text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) BP.Text else BP.TextFaint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Round icon button for the top bar. */
@Composable
internal fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(shape = 22.dp, onClick = onClick).background(BP.Surface),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = BP.Text, modifier = Modifier.size(22.dp)) }
}

package com.beyondpad.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.display.ModeController
import com.beyondpad.app.display.ModeResult
import com.beyondpad.app.display.NativeModePicker
import com.beyondpad.app.ui.theme.BP

/** Resolution and refresh rate, as a sheet over the home screen instead of a whole tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(activity: MainActivity, ext: ExternalDisplayInfo, onDismiss: () -> Unit) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val status by ModeController.status.collectAsStateWithLifecycle()
    val best = NativeModePicker.best(ext.supportedModes)

    fun apply() {
        if (DesktopLauncher.runningOnDisplay < 0) activity.openDesktop() else ModeController.applyToDesktop()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BP.Background,
        contentColor = BP.Text,
    ) {
        Column(Modifier.padding(horizontal = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("Resolution and refresh rate", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Applied through the BeyondPad desktop on the monitor, so choosing one opens it.",
                color = BP.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
            AnimatedVisibility(status.message.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    status.message, fontSize = 14.sp, modifier = Modifier.padding(bottom = 14.dp),
                    color = when (status.result) { ModeResult.VERIFIED -> BP.Good; ModeResult.NOT_APPLIED -> BP.Warn; else -> BP.TextDim },
                )
            }
            val locked = ModeController.lockedRate(ext)
            AnimatedVisibility(locked != null, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.padding(bottom = 18.dp)) {
                    Group {
                        Item(
                            "Locked at ${locked?.toInt() ?: 0} Hz by OxygenOS",
                            "This firmware refused a different refresh rate for this monitor, so BeyondPad stays at ${locked?.toInt() ?: 0} Hz. Resolution can still be changed.",
                            dot = BP.Warn,
                        )
                        Item("Try again anyway", "After an OxygenOS update, for example", onClick = { ModeController.forgetLock(); apply() }, last = true)
                    }
                }
            }
            Group {
                Item(
                    "Automatic", best?.let { "Best available: ${it.label}" },
                    trailing = { Toggle(settings.autoResolution) { on -> Prefs.update { it.copy(autoResolution = on) }; apply() } },
                    last = true,
                )
            }
            Spacer(Modifier.height(18.dp))
            Group {
                ext.supportedModes.forEachIndexed { i, m ->
                    val key = ModeController.keyOf(m)
                    val chosen = if (settings.autoResolution) best?.modeId == m.modeId else settings.manualModeKey == key
                    val tags = listOfNotNull(
                        if (m.modeId == ext.nativeMode?.modeId) "native" else null,
                        if (m.modeId == ext.currentMode?.modeId) "running now" else null,
                    ).joinToString(" · ")
                    Item(
                        m.label, tags.ifEmpty { null },
                        onClick = { Prefs.update { it.copy(autoResolution = false, manualModeKey = key) }; apply() },
                        trailing = { if (chosen) Icon(Icons.Filled.Check, null, tint = BP.Accent, modifier = Modifier.size(20.dp)) },
                        last = i == ext.supportedModes.lastIndex,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

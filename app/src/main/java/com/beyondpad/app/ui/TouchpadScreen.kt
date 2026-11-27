package com.beyondpad.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.touch.TouchpadService
import com.beyondpad.app.ui.theme.BP
import com.beyondpad.app.ui.theme.BpIcons
import kotlin.math.abs

@Composable
fun TouchpadScreen(activity: MainActivity, onBack: () -> Unit) {
    val connected by TouchpadService.connected.collectAsStateWithLifecycle()
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val ext = displays.firstOrNull()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(16.dp))
            Text("Touchpad", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        }
        when {
            !com.beyondpad.app.BuildConfig.TOUCHPAD -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "The touchpad is in the touchpad edition of BeyondPad. Google Play Protect blocks sideloaded apps that use Accessibility in India, so this standard edition leaves it out. Use a Bluetooth or USB mouse instead.",
                    color = BP.TextDim, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            !connected -> TouchpadSetup(activity)
            ext == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Connect a monitor to use the touchpad.", color = BP.TextDim, fontSize = 16.sp)
            }
            else -> Pad(activity, ext.displayId)
        }
    }
}

@Composable
private fun Pad(activity: MainActivity, displayId: Int) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(true) }
    val hintAlpha by animateFloatAsState(if (hint) 1f else 0f, tween(600), label = "hint")

    DisposableEffect(displayId) {
        val ok = TouchpadService.instance?.attach(displayId) == true
        if (!ok) activity.toast("Couldn't show a pointer on the monitor.")
        onDispose { TouchpadService.instance?.detach() }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(5000); hint = false }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(BP.Surface)
                .border(1.dp, BP.Outline, RoundedCornerShape(24.dp))
                .pointerInput(displayId, settings.cursorSpeed) {
                    val speed = settings.cursorSpeed
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = System.currentTimeMillis()
                        var moved = 0f
                        var maxFingers = 1
                        var scrollAcc = Offset.Zero
                        var holdFired = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            maxFingers = maxOf(maxFingers, pressed.size)
                            if (pressed.size == 1 && maxFingers == 1) {
                                val d = pressed[0].positionChange()
                                moved += abs(d.x) + abs(d.y)
                                TouchpadService.instance?.move(d.x * speed, d.y * speed)
                                if (!holdFired && moved < 12f && System.currentTimeMillis() - start > 550) {
                                    holdFired = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    TouchpadService.instance?.longPress()
                                }
                            } else if (pressed.size >= 2) {
                                val d = pressed.map { it.positionChange() }.reduce { a, b -> a + b } / pressed.size.toFloat()
                                scrollAcc += d
                                moved += abs(d.x) + abs(d.y)
                            }
                            ev.changes.forEach { it.consume() }
                        }
                        val quick = System.currentTimeMillis() - start < 300
                        when {
                            holdFired -> Unit
                            maxFingers >= 2 && moved < 20f && quick -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                TouchpadService.instance?.longPress()
                            }
                            maxFingers >= 2 && (abs(scrollAcc.x) > 8f || abs(scrollAcc.y) > 8f) ->
                                TouchpadService.instance?.scroll(scrollAcc.x * speed * 1.5f, scrollAcc.y * speed * 1.5f)
                            maxFingers == 1 && moved < 12f && quick -> {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                TouchpadService.instance?.tap()
                            }
                        }
                        down.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("Move one finger to point", color = BP.TextDim.copy(alpha = hintAlpha), fontSize = 16.sp)
                Text(
                    "Tap to click · hold or two-finger tap for long press · two fingers to scroll",
                    color = BP.TextFaint.copy(alpha = hintAlpha), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        AnimatedVisibility(typing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(BP.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Type here, it goes to the selected field on the monitor", color = BP.TextFaint, fontSize = 15.sp)
                    BasicTextField(
                        text, { text = it }, singleLine = true,
                        textStyle = TextStyle(color = BP.Text, fontSize = 15.sp), cursorBrush = SolidColor(BP.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val ok = TouchpadService.instance?.type(text, enter = true) == true
                            if (!ok) activity.toast("Tap a text field on the monitor first.") else text = ""
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("⌫", color = BP.Text, fontSize = 18.sp, modifier = Modifier.pressable(shape = 10.dp) {
                    TouchpadService.instance?.deleteLast()
                }.padding(horizontal = 10.dp, vertical = 4.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Check, "Send", tint = BP.Accent, modifier = Modifier.size(36.dp).pressable(shape = 18.dp) {
                    val ok = TouchpadService.instance?.type(text, enter = false) == true
                    if (!ok) activity.toast("Tap a text field on the monitor first.") else text = ""
                }.padding(6.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("Back", Icons.AutoMirrored.Filled.ArrowBack) { TouchpadService.instance?.back() }
            GhostButton("Desktop", BpIcons.Monitor) { activity.openDesktop() }
            GhostButton(if (typing) "Hide keyboard" else "Keyboard", if (typing) Icons.Filled.Close else null) { typing = !typing }
            Spacer(Modifier.weight(1f))
            Text("Speed", color = BP.TextDim, fontSize = 13.sp)
            Slider(
                value = settings.cursorSpeed, onValueChange = { v -> Prefs.update { it.copy(cursorSpeed = v) } },
                valueRange = 0.8f..3.5f, modifier = Modifier.widthIn(max = 220.dp),
                colors = SliderDefaults.colors(thumbColor = BP.Accent, activeTrackColor = BP.Accent, inactiveTrackColor = BP.SurfaceHigh),
            )
        }
    }
}

/** Explains the one-time Accessibility switch, including Android's "restricted setting" for sideloaded apps. */
@Composable
private fun TouchpadSetup(activity: MainActivity) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).padding(32.dp)) {
            Text("Use the tablet as a touchpad", color = BP.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Without a mouse, BeyondPad can show a pointer on the monitor and click, scroll and type for you. Android only allows this through an Accessibility service, so it has to be switched on once. BeyondPad uses it only while this screen is open and reads nothing.",
                color = BP.TextDim, fontSize = 15.sp, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
            listOf(
                "Tap Open Accessibility below.",
                "Find BeyondPad under Downloaded apps and switch it on.",
                "If the switch is greyed out: open App info, tap the ⋮ menu, choose Allow restricted settings, then try again.",
            ).forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${i + 1}", color = BP.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(26.dp))
                    Text(s, color = BP.Text, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Open Accessibility") {
                    runCatching { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                GhostButton("App info") {
                    runCatching {
                        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
                    }
                }
            }
        }
    }
}

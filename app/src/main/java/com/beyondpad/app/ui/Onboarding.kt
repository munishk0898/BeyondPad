package com.beyondpad.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondpad.app.Prefs
import com.beyondpad.app.display.LinkState
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.touch.TouchpadService
import com.beyondpad.app.ui.theme.BP
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Page(val title: String, val body: String, val points: List<String>)

private val pages = listOf(
    Page(
        "Your Pad, on a bigger screen",
        "BeyondPad puts a real desktop on a monitor plugged into your OnePlus Pad 3, while the tablet keeps working on its own.",
        emptyList(),
    ),
    Page(
        "What you need",
        "Everything works offline. Nothing leaves the tablet.",
        listOf(
            "A USB-C to HDMI adapter or hub that supports DisplayPort Alt Mode.",
            if (com.beyondpad.app.BuildConfig.TOUCHPAD) "A mouse or keyboard, or use the tablet itself as a touchpad."
            else "A Bluetooth or USB mouse and keyboard to work inside apps on the monitor.",
        ),
    ),
    Page(
        "Good to know",
        "Some things are decided by OxygenOS, not by BeyondPad.",
        listOf(
            "OxygenOS 16 can keep a newly plugged-in monitor switched off. BeyondPad tells you when that happens and takes over as soon as Android turns it on.",
            "OxygenOS may hold the monitor at 60 Hz. If it refuses another rate, BeyondPad says so once and stops offering it.",
            "Each app decides whether it runs in a window or full screen on the monitor.",
        ),
    ),
    Page("A few permissions", "Each one is optional. You can change them later in Settings.", emptyList()),
)

@Composable
fun Onboarding(activity: MainActivity) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    fun finish() {
        val v = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull().orEmpty()
        Prefs.update { it.copy(onboarded = true, lastVersion = v) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BeyondPad", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Spacer(Modifier.weight(1f))
            if (state.currentPage < pages.lastIndex) Text("Skip", color = BP.TextDim, fontSize = 15.sp, modifier = Modifier.pressable { finish() }.padding(12.dp))
        }
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i ->
            // Parallax: pages drift and fade as they slide.
            val offset = ((state.currentPage - i) + state.currentPageOffsetFraction).absoluteValue
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f; translationX = offset * 60f },
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 32.dp).verticalScroll(rememberScrollState())) {
                    if (i == 0) {
                        Stage(LinkState.READY, "1920 × 1080", Modifier.fillMaxWidth().aspectRatio(1.9f))
                        Spacer(Modifier.height(28.dp))
                    }
                    Text(pages[i].title, color = BP.Text, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 42.sp, letterSpacing = (-0.8).sp)
                    Spacer(Modifier.height(12.dp))
                    Text(pages[i].body, color = BP.TextDim, fontSize = 17.sp, lineHeight = 25.sp)
                    Spacer(Modifier.height(24.dp))
                    pages[i].points.forEachIndexed { n, p ->
                        Row(Modifier.padding(vertical = 8.dp)) {
                            Text("${n + 1}", color = BP.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
                            Text(p, color = BP.Text, fontSize = 16.sp, lineHeight = 23.sp)
                        }
                    }
                    if (i == pages.lastIndex) PermissionsPage(activity)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val selected = state.currentPage == i
                    val w by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (selected) BP.Accent else BP.Outline, label = "dotc")
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.currentPage > 0) {
                GhostButton("Back") { scope.launch { state.animateScrollToPage(state.currentPage - 1) } }
                Spacer(Modifier.width(10.dp))
            }
            if (state.currentPage < pages.lastIndex) PrimaryButton("Next") { scope.launch { state.animateScrollToPage(state.currentPage + 1) } }
            else PrimaryButton("Start using BeyondPad") { finish() }
        }
    }
}

@Composable
private fun PermissionsPage(activity: MainActivity) {
    val tick = activity.resumeTick.intValue
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { activity.resumeTick.intValue++ }
    val notif = remember(tick) {
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlay = remember(tick) { SystemAccess.canDrawOverlays(activity) }
    val touchpad = remember(tick) { TouchpadService.isEnabled(activity) }
    Group {
        PermRow("Notifications", "Tells you when a monitor connects, with one-tap buttons", notif) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermRow("Display over other apps", "Lets the desktop open on the monitor by itself", overlay, last = !com.beyondpad.app.BuildConfig.TOUCHPAD) {
            activity.startActivity(SystemAccess.overlaySettingsIntent(activity))
        }
        if (com.beyondpad.app.BuildConfig.TOUCHPAD) {
            PermRow("Touchpad (Accessibility)", "Only if you have no mouse. Set it up any time from the Touchpad button.", touchpad, last = true) {
                Prefs.update { it.copy(onboarded = true) }
                activity.screen.value = "touchpad"
            }
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, granted: Boolean, last: Boolean = false, onGrant: () -> Unit) {
    Item(title, sub, last = last, onClick = if (granted) null else onGrant, trailing = {
        if (granted) Text("On", color = BP.Good, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        else Text("Allow", color = BP.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    })
}

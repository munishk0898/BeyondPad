package com.beyondpad.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondpad.app.ui.theme.BP

private val faq = listOf(
    "The monitor stays dark or shows \"no signal\"" to
        "First check the adapter: it must support DisplayPort Alt Mode (many hubs only output video from laptops). If the adapter is fine, OxygenOS 16 may be keeping the monitor switched off until it is confirmed. Unplug and replug, unlock the tablet and look for a prompt. BeyondPad takes over as soon as Android turns the monitor on.",
    "The monitor shows a copy of the tablet with black bars" to
        "That is screen mirroring. The tablet is nearly square and the monitor is wide, so the copy can't fill it. Tap Show desktop: the BeyondPad desktop is drawn for the monitor itself and fills the whole screen.",
    "I can't pick a higher refresh rate" to
        "BeyondPad asks for the rate through its desktop window, and Android decides. If OxygenOS refuses once, BeyondPad remembers that this monitor is locked to its current rate and stops offering the others.",
    "I have no mouse" to
        "Open Touchpad on the home screen. One finger moves the pointer, a tap clicks, holding or a two-finger tap long-presses, and two fingers scroll. The keyboard button types into the field you tapped on the monitor.",
    "The Touchpad switch is greyed out in Accessibility" to
        "Android protects Accessibility for apps installed outside the Play Store. Open App info for BeyondPad, tap the ⋮ menu, choose Allow restricted settings, then switch it on again.",
    "An app opened on the tablet instead of the monitor" to
        "Some apps only run on the main screen, and Android puts them there. Open on tablet always opens a fresh window, so nothing is dragged between screens.",
    "Can I make things bigger or smaller on the monitor?" to
        "Settings › Desktop size changes the BeyondPad desktop. Other apps use the monitor's own size, which only OxygenOS can change.",
    "Does BeyondPad use the internet or my data?" to
        "No. It has no internet permission. The touchpad service reads nothing and is idle unless the Touchpad screen is open. A crash report, if there is one, stays on the tablet until you copy it.",
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(16.dp))
            Text("Help", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(32.dp)) {
                Group { faq.forEachIndexed { i, (q, a) -> Faq(q, a, last = i == faq.lastIndex) } }
            }
        }
    }
}

@Composable
private fun Faq(q: String, a: String, last: Boolean) {
    var open by rememberSaveable(q) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(q, color = BP.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = BP.TextDim, modifier = Modifier.size(22.dp).rotate(turn))
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(a, color = BP.TextDim, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
        }
        if (!last) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(BP.Outline))
    }
}

/** Shown once after an update. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(version: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BP.Background) {
        Column(Modifier.padding(horizontal = 28.dp).navigationBarsPadding()) {
            Text("What's new in $version", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(14.dp))
            listOf(
                "Touchpad: use the tablet to point, click, scroll and type on the monitor.",
                "Quick Settings tile and notification buttons to show the desktop in one tap.",
                "Desktop wallpaper from your gallery, a clock, an app switcher and a size slider.",
                "Reopen the apps you had on the monitor last time.",
                "Light theme and accent colours, haptic feedback, and a Help page.",
            ).forEach {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("•", color = BP.Accent, fontSize = 16.sp, modifier = Modifier.width(20.dp))
                    Text(it, color = BP.Text, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.size(18.dp))
            PrimaryButton("Got it", onClick = onDismiss)
            Spacer(Modifier.size(28.dp))
        }
    }
}

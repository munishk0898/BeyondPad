package com.beyondpad.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.desktop.AppEntry
import com.beyondpad.app.desktop.AppLauncher
import com.beyondpad.app.desktop.AppRepository
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.desktop.DesktopSession
import com.beyondpad.app.desktop.WindowStyle
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.ConnectionSignals
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.display.LinkState
import com.beyondpad.app.display.Signals
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.ui.theme.BP
import com.beyondpad.app.ui.theme.BpIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Advice(val title: String, val body: String, val steps: List<String>)

private fun advice(link: LinkState, ext: ExternalDisplayInfo?, s: Signals): Advice = when (link) {
    LinkState.READY -> Advice(
        "Monitor ready",
        "${ext?.let { it.productName ?: it.name } ?: "Your monitor"} works as a second screen. Pick an app to put on it.",
        emptyList(),
    )
    LinkState.HIDDEN_BY_SYSTEM -> Advice(
        "Monitor switched off by Android",
        "The tablet sees the monitor but keeps it off until it is confirmed, and this OxygenOS version doesn't ask.",
        listOf(
            "Unplug and replug the cable, then unlock the tablet and look for a prompt to use the display.",
            "Look in Settings for a connected or external display option.",
            "The moment Android turns the monitor on, BeyondPad takes over.",
        ),
    )
    LinkState.ADAPTER_NO_VIDEO -> Advice(
        "Hub connected, no picture yet",
        "The tablet sees ${s.usb.firstOrNull { it.isHub }?.name ?: s.usb.first().name}, but no active display behind it.",
        listOf(
            "Unplug and replug the cable, then unlock the tablet and look for a prompt to use the display.",
            "Look in Settings for a connected or external display option.",
            "Still nothing: the hub may not carry video from a tablet (it needs DisplayPort Alt Mode).",
        ),
    )
    LinkState.NOTHING -> Advice(
        "Plug in a monitor",
        "Connect a USB-C to HDMI adapter or hub. BeyondPad notices it straight away.",
        emptyList(),
    )
}

@Composable
fun HomeScreen(activity: MainActivity, onSettings: () -> Unit) {
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val signals by ConnectionSignals.signals.collectAsStateWithLifecycle()
    val ext = displays.firstOrNull()
    val link = ConnectionSignals.linkState(displays.size, signals)
    var showModes by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BeyondPad", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Filled.Settings, "Settings", onSettings)
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp)) {
            if (maxWidth > 900.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Hero(activity, link, ext, signals, stageModifier = Modifier.fillMaxWidth().aspectRatio(1.75f))
                    }
                    Column(Modifier.weight(1.15f).fillMaxSize()) {
                        RightPane(activity, link, ext, signals) { showModes = true }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Hero(activity, link, ext, signals, stageModifier = Modifier.fillMaxWidth().height(200.dp))
                    Spacer(Modifier.height(20.dp))
                    RightPane(activity, link, ext, signals) { showModes = true }
                }
            }
        }
    }
    if (showModes && ext != null) ModeSheet(activity, ext) { showModes = false }
}

@Composable
private fun Hero(activity: MainActivity, link: LinkState, ext: ExternalDisplayInfo?, s: Signals, stageModifier: Modifier) {
    val a = advice(link, ext, s)
    val monitorText = when (link) {
        LinkState.READY -> ext?.currentMode?.let { "${it.width} × ${it.height} · ${it.refreshRate.toInt()} Hz" } ?: "Ready"
        LinkState.HIDDEN_BY_SYSTEM -> "Switched off"
        LinkState.ADAPTER_NO_VIDEO -> "No signal"
        LinkState.NOTHING -> ""
    }
    Stage(link, monitorText, stageModifier)
    Spacer(Modifier.height(18.dp))
    AnimatedContent(
        targetState = a,
        transitionSpec = { (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 5 }) togetherWith fadeOut(tween(180)) },
        label = "headline",
    ) { adv ->
        Column {
            Text(adv.title, color = BP.Text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(8.dp))
            Text(adv.body, color = BP.TextDim, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.widthIn(max = 560.dp))
        }
    }
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (link) {
            LinkState.READY -> {
                PrimaryButton("Show desktop", BpIcons.Monitor) { activity.openDesktop() }
                if (DesktopLauncher.runningOnDisplay >= 0) GhostButton("Close desktop", Icons.Filled.Close) { DesktopLauncher.close(activity) }
            }
            LinkState.HIDDEN_BY_SYSTEM, LinkState.ADAPTER_NO_VIDEO -> {
                PrimaryButton("Display settings") { runCatching { activity.startActivity(SystemAccess.displaySettingsIntent()) } }
                GhostButton("Check again", Icons.Filled.Refresh) { ConnectionSignals.refresh(); DisplayRepository.refresh(); CapabilityProbe.refreshStatic(activity) }
            }
            LinkState.NOTHING -> GhostButton("Check again", Icons.Filled.Refresh) { ConnectionSignals.refresh(); DisplayRepository.refresh() }
        }
    }
}

@Composable
private fun ColumnScope.RightPane(activity: MainActivity, link: LinkState, ext: ExternalDisplayInfo?, s: Signals, openModes: () -> Unit) {
    AnimatedContent(
        targetState = link == LinkState.READY && ext != null,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        modifier = Modifier.weight(1f),
        label = "pane",
    ) { ready ->
        Column(Modifier.fillMaxSize()) {
            if (ready && ext != null) MonitorPanel(activity, ext, openModes)
            else WaitingPanel(link, s)
        }
    }
}

// ───────────────────────── monitor connected ─────────────────────────

@Composable
private fun ColumnScope.MonitorPanel(activity: MainActivity, ext: ExternalDisplayInfo, openModes: () -> Unit) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { AppRepository.load(activity) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val style = if (settings.freeformWindows) WindowStyle.WINDOW else WindowStyle.FULLSCREEN
    val opened = DesktopSession.opened.toList().asReversed().mapNotNull { byKey[it] }

    fun openOnMonitor(app: AppEntry) {
        val r = AppLauncher.launch(activity, app, ext.displayId, style)
        if (r.ok) DesktopSession.noteOpened(app.key)
        activity.toast(if (r.ok) "${app.label} is on the monitor" else r.message)
    }

    // Monitor + resolution chip
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ext.productName ?: ext.name, color = BP.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Second screen", color = BP.TextDim, fontSize = 13.sp)
        }
        Row(
            Modifier.pressable(shape = 20.dp, onClick = openModes).background(BP.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BpIcons.Monitor, null, tint = BP.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                ext.currentMode?.let { "${it.width} × ${it.height} · ${it.refreshRate.toInt()} Hz" } ?: "Resolution",
                color = BP.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
    }

    // What's on the monitor
    AnimatedVisibility(opened.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column {
            Spacer(Modifier.height(20.dp))
            Label("On the monitor")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(opened, key = { "o_" + it.key + it.user.hashCode() }) { app -> OpenedChip(activity, app) { openOnMonitor(app) } }
            }
        }
    }

    // Apps
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth().background(BP.Surface, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = BP.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Open an app on the monitor", color = BP.TextFaint, fontSize = 15.sp)
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = TextStyle(color = BP.Text, fontSize = 15.sp), cursorBrush = SolidColor(BP.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", tint = BP.TextDim, modifier = Modifier.size(20.dp).pressable(shape = 10.dp) { query = "" })
    }
    Spacer(Modifier.height(12.dp))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(shown, key = { it.key + "#" + it.user.hashCode() }) { app ->
            Column(
                Modifier.animateItem().pressable(shape = 16.dp) { openOnMonitor(app) }.padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, app.label, Modifier.size(50.dp))
                Text(
                    app.label, color = BP.Text, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun OpenedChip(activity: MainActivity, app: AppEntry, onShow: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.pressable(shape = 24.dp) { menu = true }.background(BP.Surface).padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(app.icon, null, Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(app.label, color = BP.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Bring to front on monitor") }, onClick = { menu = false; onShow() })
            DropdownMenuItem(text = { Text("Open on tablet") }, onClick = {
                menu = false
                val r = AppLauncher.openOnTablet(activity, app)
                if (r.ok) DesktopSession.noteMovedAway(app.key)
                activity.toast(if (r.ok) "${app.label} opened on the tablet" else r.message)
            })
            DropdownMenuItem(text = { Text("Remove from this list") }, onClick = { menu = false; DesktopSession.noteMovedAway(app.key) })
        }
    }
}

// ───────────────────────── no monitor yet ─────────────────────────

@Composable
private fun WaitingPanel(link: LinkState, s: Signals) {
    val steps = advice(link, null, s).steps
    Label("What the tablet sees")
    Group {
        Item("Monitor signal", null, value = if (s.hdmiAudio) "Detected" else "Not detected", dot = if (s.hdmiAudio) BP.Good else BP.TextFaint)
        Item("On the USB-C port", null, value = if (s.usb.isEmpty()) "Nothing" else s.usb.joinToString { it.name }, dot = if (s.usb.isNotEmpty()) BP.Good else BP.TextFaint)
        Item("Switched-off displays", null, value = if (s.hidden.isEmpty()) "None" else s.hidden.size.toString(), dot = if (s.hidden.isNotEmpty()) BP.Warn else BP.TextFaint, last = true)
    }
    if (steps.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Label("What you can try")
        steps.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 7.dp)) {
                Text("${i + 1}", color = BP.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(26.dp))
                Text(step, color = BP.Text, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

package com.beyondpad.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.ConnectionSignals
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.display.LaunchTest
import com.beyondpad.app.display.LinkState
import com.beyondpad.app.display.ModeController
import com.beyondpad.app.display.ModeResult
import com.beyondpad.app.display.NativeModePicker
import com.beyondpad.app.display.Signals
import com.beyondpad.app.service.DisplayMonitorService
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.ui.theme.BP

private val TABS = listOf("Overview", "Display", "Desktop", "Advanced")

@Composable
fun Dashboard(activity: MainActivity) {
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val signals by ConnectionSignals.signals.collectAsStateWithLifecycle()
    val caps by CapabilityProbe.capabilities.collectAsStateWithLifecycle()
    val tick = activity.resumeTick.intValue
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ext = displays.firstOrNull()
    val link = ConnectionSignals.linkState(displays.size, signals)

    Column(
        Modifier.fillMaxSize().background(BP.Background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(tab, onTab = { tab = it }, build = "${caps.build} · Android ${caps.androidRelease}")
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)) {
                when (tab) {
                    0 -> Overview(activity, link, ext, signals) { tab = it }
                    1 -> DisplayTab(activity, ext)
                    2 -> DesktopTab(activity, ext, tick)
                    else -> AdvancedTab(activity, ext, tick)
                }
            }
        }
    }
}

// ───────────────────────── top bar ─────────────────────────

@Composable
private fun TopBar(selected: Int, onTab: (Int) -> Unit, build: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BeyondPad", color = BP.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        Spacer(Modifier.width(40.dp))
        TABS.forEachIndexed { i, t ->
            Column(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onTab(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t, color = if (i == selected) BP.Text else BP.TextDim, fontSize = 15.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(22.dp).height(2.dp).background(if (i == selected) BP.Accent else Color.Transparent))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(build, color = BP.TextFaint, fontSize = 12.sp)
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(BP.Outline))
}

// ───────────────────────── overview ─────────────────────────

private data class Advice(val title: String, val body: String, val steps: List<String>)

private fun advice(link: LinkState, ext: ExternalDisplayInfo?, s: Signals): Advice = when (link) {
    LinkState.READY -> Advice(
        "Second screen ready",
        "${ext?.name ?: "Monitor"} is available to apps as its own display.",
        listOf(
            "Open the desktop on the monitor.",
            "Run the capability test under Advanced to see whether apps get free windows or open full screen.",
        ),
    )
    LinkState.HIDDEN_BY_SYSTEM -> Advice(
        "Monitor connected but switched off by Android",
        "Android sees the monitor${s.hdmiAudioName?.let { " ($it)" } ?: ""} but keeps it off until it is confirmed, and this OxygenOS version does not ask. No app can switch it on without developer tools.",
        listOf(
            "Unplug and replug the cable, then unlock the tablet and look for a prompt to use or mirror the display.",
            "Check Settings for a connected or external display option.",
            "As soon as Android turns the monitor on, BeyondPad opens the desktop on it by itself.",
        ),
    )
    LinkState.ADAPTER_NO_VIDEO -> Advice(
        "Hub connected, monitor not active",
        "The tablet sees ${s.usb.firstOrNull { it.isHub }?.name ?: s.usb.first().name} on its port but no active display. On this OxygenOS version Android keeps new monitors switched off until they are confirmed.",
        listOf(
            "Unplug and replug the cable, then unlock the tablet and look for a prompt to use or mirror the display.",
            "Check Settings for a connected or external display option.",
            "If nothing changes, the hub may not carry video from a tablet (it needs DisplayPort Alt Mode).",
        ),
    )
    LinkState.NOTHING -> Advice(
        "Nothing connected",
        "Plug a USB-C to HDMI adapter or hub into the tablet.",
        listOf("Use the tablet's USB-C port with an adapter that supports DisplayPort Alt Mode."),
    )
}

@Composable
private fun Overview(activity: MainActivity, link: LinkState, ext: ExternalDisplayInfo?, s: Signals, goTo: (Int) -> Unit) {
    val a = advice(link, ext, s)
    val monitorText = when (link) {
        LinkState.READY -> ext?.currentMode?.let { "${it.width} × ${it.height} · ${it.refreshRate.toInt()} Hz" } ?: "Ready"
        LinkState.HIDDEN_BY_SYSTEM -> "Not shared with apps"
        LinkState.ADAPTER_NO_VIDEO -> "No signal"
        LinkState.NOTHING -> ""
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 900.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1.25f)) { Hero(activity, link, a, monitorText, goTo) }
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    SignalsPanel(link, ext, s); Spacer(Modifier.height(28.dp)); Steps(a.steps)
                }
            }
        } else {
            Column {
                Hero(activity, link, a, monitorText, goTo)
                Spacer(Modifier.height(28.dp)); SignalsPanel(link, ext, s); Spacer(Modifier.height(28.dp)); Steps(a.steps)
            }
        }
    }
}

@Composable
private fun Hero(activity: MainActivity, link: LinkState, a: Advice, monitorText: String, goTo: (Int) -> Unit) {
    Stage(link, monitorText, Modifier.fillMaxWidth().aspectRatio(1.9f))
    Spacer(Modifier.height(20.dp))
    Text(a.title, color = BP.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    Spacer(Modifier.height(8.dp))
    Text(a.body, color = BP.TextDim, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.widthIn(max = 620.dp))
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (link) {
            LinkState.READY -> {
                PrimaryButton("Open desktop on monitor") { activity.openDesktop() }
                GhostButton("Display settings") { goTo(1) }
            }
            LinkState.HIDDEN_BY_SYSTEM, LinkState.ADAPTER_NO_VIDEO -> {
                PrimaryButton("Open display settings") { runCatching { activity.startActivity(SystemAccess.displaySettingsIntent()) } }
                GhostButton("Re-check") { ConnectionSignals.refresh(); DisplayRepository.refresh(); CapabilityProbe.refreshStatic(activity) }
            }
            LinkState.NOTHING -> {
                GhostButton("Re-check") { ConnectionSignals.refresh(); DisplayRepository.refresh() }
            }
        }
    }
}

@Composable
private fun SignalsPanel(link: LinkState, ext: ExternalDisplayInfo?, s: Signals) {
    Label("What the tablet sees")
    Group {
        Item("Second screen for apps", if (ext != null) "${ext.name}, ID ${ext.displayId}" else "None", dot = if (ext != null) BP.Good else BP.Bad)
        Item("Monitor signal", if (s.hdmiAudio) "Detected${s.hdmiAudioName?.let { " · $it" } ?: ""}" else "Not detected", dot = if (s.hdmiAudio) BP.Good else BP.TextFaint)
        Item(
            "On the USB-C port",
            if (s.usb.isEmpty()) "Nothing" else s.usb.joinToString { "${it.name} (${it.ids})" },
            dot = if (s.usb.isNotEmpty()) BP.Good else BP.TextFaint,
        )
        Item(
            "Hidden displays",
            if (s.hidden.isEmpty()) "None" else s.hidden.joinToString { "ID ${it.id} ${it.name}, ${it.reason}" },
            dot = if (s.hidden.isNotEmpty()) BP.Warn else BP.TextFaint,
            last = true,
        )
    }
    if (link != LinkState.READY && !s.hdmiAudio) {
        Spacer(Modifier.height(8.dp))
        Text("Monitors without speakers may not report a signal, so check the screen itself too.", color = BP.TextFaint, fontSize = 12.sp)
    }
}

@Composable
private fun Steps(steps: List<String>) {
    Label("Next")
    steps.forEachIndexed { i, step ->
        Row(Modifier.padding(vertical = 7.dp)) {
            Text("${i + 1}", color = BP.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(26.dp))
            Text(step, color = BP.Text, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

// ───────────────────────── display ─────────────────────────

@Composable
private fun DisplayTab(activity: MainActivity, ext: ExternalDisplayInfo?) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val modeStatus by ModeController.status.collectAsStateWithLifecycle()
    if (ext == null) { Empty("No second screen yet", "Resolution and refresh rate appear here once the monitor is available to apps."); return }

    val cur = ext.currentMode
    Text(cur?.let { "${it.width} × ${it.height}" } ?: "Unknown", color = BP.Text, fontSize = 48.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
    Text(
        listOfNotNull(cur?.let { "%.2f Hz".format(it.refreshRate) }, ext.nativeMode?.let { "native ${it.width} × ${it.height}" }, ext.densityLabel).joinToString("  ·  "),
        color = BP.TextDim, fontSize = 15.sp,
    )
    if (modeStatus.message.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            modeStatus.message, fontSize = 14.sp,
            color = when (modeStatus.result) { ModeResult.VERIFIED -> BP.Good; ModeResult.NOT_APPLIED -> BP.Warn; else -> BP.TextDim },
        )
    }
    Spacer(Modifier.height(28.dp))

    TwoColumns(
        left = {
            Label("Mode")
            Group {
                Item(
                    "Automatic", "Native resolution, highest refresh rate (${NativeModePicker.best(ext.supportedModes)?.label ?: "n/a"})",
                    trailing = { Toggle(settings.autoResolution) { on ->
                        Prefs.update { it.copy(autoResolution = on) }
                        if (DesktopLauncher.runningOnDisplay < 0) activity.openDesktop() else ModeController.applyToDesktop()
                    } },
                    last = true,
                )
            }
            Spacer(Modifier.height(20.dp))
            Label("Available modes")
            Group {
                ext.supportedModes.forEachIndexed { i, m ->
                    val key = ModeController.keyOf(m)
                    val chosen = if (settings.autoResolution) NativeModePicker.best(ext.supportedModes)?.modeId == m.modeId else settings.manualModeKey == key
                    val tags = listOfNotNull(
                        if (m.modeId == ext.nativeMode?.modeId) "native" else null,
                        if (m.modeId == cur?.modeId) "active" else null,
                        if (m.hdrTypes.isNotEmpty()) "HDR" else null,
                    ).joinToString(" · ")
                    Item(
                        m.label, tags.ifEmpty { null },
                        onClick = if (settings.autoResolution) null else ({
                            Prefs.update { it.copy(manualModeKey = key) }
                            if (DesktopLauncher.runningOnDisplay < 0) activity.openDesktop() else ModeController.applyToDesktop()
                        }),
                        trailing = { if (chosen) Icon(Icons.Filled.Check, null, tint = BP.Accent, modifier = Modifier.size(20.dp)) },
                        last = i == ext.supportedModes.lastIndex,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("A mode is applied through BeyondPad's desktop on the monitor, so picking one opens the desktop. It is marked as applied only after the monitor reports it.", color = BP.TextFaint, fontSize = 12.sp)
        },
        right = {
            Label("Monitor")
            Group {
                val rows = buildList {
                    add("Name" to ext.name)
                    ext.manufacturer?.let { add("Maker" to it) }
                    ext.productName?.let { add("Product" to it) }
                    ext.modelYear?.let { add("Year" to it.toString()) }
                    add("Display ID" to ext.displayId.toString())
                    add("Refresh rates" to ext.supportedModes.map { "%.0f".format(it.refreshRate) }.distinct().joinToString(", ") { "$it Hz" })
                    add("HDR" to ext.hdrTypes.ifEmpty { listOf("Not reported") }.joinToString())
                    add("Wide colour" to if (ext.isWideColorGamut) "Yes" else "No")
                    add("Physical DPI" to "%.0f × %.0f".format(ext.xdpi, ext.ydpi))
                    add("Orientation" to ext.orientationLabel)
                    add("State" to ext.stateLabel)
                    add("Secure output (HDCP)" to if (ext.isSecure) "Yes" else "No")
                }
                rows.forEachIndexed { i, (k, v) -> Item(k, null, value = v, last = i == rows.lastIndex) }
            }
        },
    )
}

// ───────────────────────── desktop ─────────────────────────

@Composable
private fun DesktopTab(activity: MainActivity, ext: ExternalDisplayInfo?, tick: Int) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val presentationOn by activity.presentationOn
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { activity.resumeTick.intValue++ }
    val notif = remember(tick) {
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlay = remember(tick) { SystemAccess.canDrawOverlays(activity) }
    val usage = remember(tick) { SystemAccess.hasUsageAccess(activity) }
    val running = DesktopLauncher.runningOnDisplay >= 0

    TwoColumns(
        left = {
            if (ext != null) { MonitorRemote(activity, ext); Spacer(Modifier.height(28.dp)) }
            Label("Desktop on the monitor")
            Group {
                Item("Status", null, value = if (running) "Open on display ${DesktopLauncher.runningOnDisplay}" else if (ext == null) "No second screen" else "Closed",
                    dot = if (running) BP.Good else BP.TextFaint)
                Item("Open desktop", "Start menu, taskbar and pinned apps on the monitor", onClick = if (ext != null) ({ activity.openDesktop() }) else null)
                Item("Close desktop", null, onClick = { DesktopLauncher.close(activity) })
                Item(
                    "Presentation output", "Fallback when apps can't open on the monitor. Shows BeyondPad's own screen only, not a desktop.",
                    trailing = { Toggle(presentationOn) { activity.togglePresentation() } }, last = true,
                )
            }
            Spacer(Modifier.height(20.dp))
            Label("Behaviour")
            Group {
                Item("Open automatically on connect", null, trailing = { Toggle(settings.autoOpenDesktop) { on -> Prefs.update { it.copy(autoOpenDesktop = on) } } })
                Item("Watch for monitors in the background", null, trailing = {
                    Toggle(settings.monitorService) { on ->
                        Prefs.update { it.copy(monitorService = on) }
                        if (on) DisplayMonitorService.start(activity) else DisplayMonitorService.stop(activity)
                    }
                })
                Item("Open apps in windows", "Only where the system supports desktop windowing", trailing = {
                    Toggle(settings.freeformWindows) { on -> Prefs.update { it.copy(freeformWindows = on) } }
                }, last = true)
            }
        },
        right = {
            Label("Permissions")
            Group {
                PermItem("Notifications", "Connect and disconnect alerts", notif) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermItem("Display over other apps", "Lets the desktop open by itself when you plug in", overlay) {
                    activity.startActivity(SystemAccess.overlaySettingsIntent(activity))
                }
                PermItem("Usage access", "Marks taskbar apps that are active", usage, last = true) {
                    activity.startActivity(SystemAccess.usageSettingsIntent())
                }
            }
        },
    )
}

// ───────────────────────── advanced ─────────────────────────

@Composable
private fun AdvancedTab(activity: MainActivity, ext: ExternalDisplayInfo?, tick: Int) {
    val caps by CapabilityProbe.capabilities.collectAsStateWithLifecycle()
    val events by DisplayRepository.events.collectAsStateWithLifecycle()
    val all = remember(tick, ext) { DisplayRepository.allDisplaysSummary() }
    fun yn(b: Boolean?) = when (b) { true -> "Yes"; false -> "No"; null -> "Unknown" }

    TwoColumns(
        left = {
            Label("Capability")
            Group {
                Item("Verdict", null, value = caps.verdict.title)
                Item("Apps may open on the monitor", null, value = yn(caps.activityStartAllowed))
                Item("Launch test", caps.launchTestDetail.ifEmpty { null }, value = when (caps.launchTest) {
                    LaunchTest.NOT_RUN -> "Not run"; LaunchTest.RUNNING -> "Running…"
                    LaunchTest.FULLSCREEN -> "Full screen"; LaunchTest.WINDOWED -> "Free windows"
                    LaunchTest.LANDED_ELSEWHERE -> "Redirected"; LaunchTest.REFUSED -> "Refused"
                })
                Item("Free-form window feature", null, value = yn(caps.freeformFeature))
                Item("Secondary-display launcher", caps.secondaryHomeApps.ifEmpty { listOf("None") }.joinToString(), last = true)
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Run capability test", enabled = ext != null) { CapabilityProbe.runLaunchTest(activity) }

        },
        right = {
            Label("Displays reported by Android")
            Group { all.forEachIndexed { i, d -> Item(d, null, mono = true, last = i == all.lastIndex) } }
            Spacer(Modifier.height(28.dp))
            Label("Event log")
            Group {
                if (events.isEmpty()) Item("No events yet", null, last = true)
                events.take(40).forEachIndexed { i, e -> Item(e.message, e.timeLabel, mono = true, last = i == minOf(39, events.lastIndex)) }
            }
        },
    )
}

// ───────────────────────── building blocks ─────────────────────────

@Composable
private fun TwoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth > 900.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f), content = left)
                Column(Modifier.weight(1f), content = right)
            }
        } else {
            Column { left(); Spacer(Modifier.height(28.dp)); right() }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, color = BP.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BP.Surface), content = content)
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
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = if (onClick == null && trailing == null && sub == null && mono) BP.TextDim else BP.Text,
                    fontSize = if (mono) 13.sp else 15.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                )
                if (sub != null) {
                    Text(sub, color = BP.TextDim, fontSize = 13.sp, lineHeight = 18.sp,
                        fontFamily = if (mono && trailing != null) FontFamily.Monospace else FontFamily.Default,
                        modifier = Modifier.padding(top = 2.dp))
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
private fun PermItem(title: String, sub: String, granted: Boolean, last: Boolean = false, onGrant: () -> Unit) {
    Item(title, sub, last = last, onClick = if (granted) null else onGrant, trailing = {
        if (granted) Text("Allowed", color = BP.TextDim, fontSize = 14.sp) else Text("Allow", color = BP.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    })
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = BP.Accent, checkedThumbColor = Color.White,
            uncheckedTrackColor = BP.SurfaceHigh, uncheckedThumbColor = BP.TextDim, uncheckedBorderColor = BP.Outline,
        ),
    )

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(BP.Accent).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun GhostButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(BP.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, color = if (enabled) BP.Text else BP.TextFaint, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = BP.TextDim, fontSize = 15.sp)
    }
}

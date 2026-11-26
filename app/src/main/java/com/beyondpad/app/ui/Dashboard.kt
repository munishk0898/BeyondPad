package com.beyondpad.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.DesktopVerdict
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.display.LaunchTest
import com.beyondpad.app.display.ModeController
import com.beyondpad.app.display.ModeResult
import com.beyondpad.app.display.NativeModePicker
import com.beyondpad.app.service.DisplayMonitorService
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.ui.theme.BP

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dashboard(activity: MainActivity) {
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val caps by CapabilityProbe.capabilities.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val modeStatus by ModeController.status.collectAsStateWithLifecycle()
    val events by DisplayRepository.events.collectAsStateWithLifecycle()
    val tick = activity.resumeTick.intValue
    val presentationOn by activity.presentationOn
    val ext = displays.firstOrNull()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { activity.resumeTick.intValue++ }
    val notifGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlay = remember(tick) { SystemAccess.canDrawOverlays(activity) }
    val usage = remember(tick) { SystemAccess.hasUsageAccess(activity) }
    val allDisplays = remember(tick, displays) { DisplayRepository.allDisplaysSummary() }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(440.dp),
        modifier = Modifier.fillMaxSize().background(BP.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) { Header(caps.device, caps.androidRelease, caps.sdk, caps.build) }

        // 1. Connection status
        item {
            Card("HDMI / USB-C connection", Icons.Filled.Monitor) {
                if (ext == null) {
                    StatusLine(false, "No external display visible to apps")
                    Dim("Plug in your USB-C to HDMI adapter. If the monitor lights up with a copy of the tablet screen but this stays empty, OxygenOS is mirroring at system level and the monitor is not exposed to apps.")
                } else {
                    StatusLine(true, "Connected: ${ext.name}")
                    KV("Display ID", ext.displayId.toString())
                    KV("Current mode", ext.currentMode?.label ?: "Unknown")
                    if (displays.size > 1) Dim("${displays.size} external displays detected; BeyondPad manages the first one.")
                }
            }
        }

        // 2. Extended desktop availability
        item {
            Card("Extended desktop availability", Icons.Filled.DesktopWindows) {
                val color = when (caps.verdict) {
                    DesktopVerdict.SYSTEM_DESKTOP, DesktopVerdict.APP_DESKTOP -> BP.Good
                    DesktopVerdict.PRESENTATION_ONLY -> BP.Warn
                    DesktopVerdict.NO_DISPLAY -> BP.Bad
                    DesktopVerdict.UNKNOWN -> BP.TextDim
                }
                Text(caps.verdict.title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Dim(caps.verdict.detail)
                Spacer(Modifier.height(10.dp))
                Check("Monitor exposed to apps as a separate display", ext != null)
                Check("System allows opening activities on it", caps.activityStartAllowed)
                Check(
                    "Launch test: ${launchLabel(caps.launchTest)}",
                    when (caps.launchTest) {
                        LaunchTest.WINDOWED, LaunchTest.FULLSCREEN -> true
                        LaunchTest.REFUSED, LaunchTest.LANDED_ELSEWHERE -> false
                        else -> null
                    },
                )
                if (caps.launchTestDetail.isNotEmpty()) Mono(caps.launchTestDetail)
                Check("Free-form window manager feature (android.software.freeform_window_management)", caps.freeformFeature)
                Check("Developer setting: Force desktop mode", caps.forceDesktopSetting)
                Check("Developer setting: Enable freeform windows", caps.freeformSetting)
                Check(
                    "System secondary-display launcher present" +
                        if (caps.secondaryHomeApps.isNotEmpty()) " (${caps.secondaryHomeApps.joinToString()})" else "",
                    caps.secondaryHomeApps.isNotEmpty(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Primary("Run capability test", enabled = ext != null && caps.launchTest != LaunchTest.RUNNING) {
                        CapabilityProbe.runLaunchTest(activity)
                    }
                }
            }
        }

        // 3. Monitor details
        item {
            Card("External monitor details", Icons.Filled.Info) {
                if (ext == null) Dim("Connect a monitor to see its details.") else MonitorDetails(ext)
            }
        }

        // 4. Resolution and refresh rate
        item {
            Card("Resolution and refresh rate", Icons.Filled.Monitor) {
                if (ext == null) {
                    Dim("Available modes appear here once a monitor is connected.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic resolution", color = BP.Text, fontWeight = FontWeight.Medium)
                            Dim("Native resolution at the highest refresh rate: ${NativeModePicker.best(ext.supportedModes)?.label ?: "n/a"}")
                        }
                        BPSwitch(settings.autoResolution) { on -> Prefs.update { it.copy(autoResolution = on) }; ModeController.applyToDesktop() }
                    }
                    Spacer(Modifier.height(8.dp))
                    ext.supportedModes.forEach { m ->
                        val key = ModeController.keyOf(m)
                        val selected = if (settings.autoResolution) ModeController.targetMode(ext)?.modeId == m.modeId else settings.manualModeKey == key
                        val isCurrent = ext.currentMode?.modeId == m.modeId
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !settings.autoResolution) {
                                    Prefs.update { it.copy(manualModeKey = key) }
                                    ModeController.applyToDesktop()
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected, enabled = !settings.autoResolution,
                                onClick = { Prefs.update { it.copy(manualModeKey = key) }; ModeController.applyToDesktop() },
                                colors = RadioButtonDefaults.colors(selectedColor = BP.Accent),
                            )
                            Text(m.label, color = BP.Text, modifier = Modifier.weight(1f))
                            if (m.modeId == ext.nativeMode?.modeId) Tag("native", BP.Accent)
                            if (isCurrent) Tag("active", BP.Good)
                            if (m.hdrTypes.isNotEmpty()) Tag("HDR", BP.Warn)
                        }
                    }
                    if (ext.supportedModes.size <= 1) {
                        Dim("The system exposes only one mode for this monitor, so there is nothing to switch. OxygenOS may fix the output mode (reports suggest 60 Hz).")
                    }
                    Spacer(Modifier.height(8.dp))
                    val c = when (modeStatus.result) {
                        ModeResult.VERIFIED -> BP.Good
                        ModeResult.NOT_APPLIED -> BP.Warn
                        ModeResult.NEEDS_DESKTOP -> BP.TextDim
                        else -> BP.Text
                    }
                    if (modeStatus.message.isNotEmpty()) Text(modeStatus.message, color = c, fontSize = 13.sp)
                    Dim("Modes are applied through BeyondPad's desktop window on the monitor and are only reported as applied after the display confirms it. They last while the desktop (or presentation output) is open.")
                }
            }
        }

        // 5. Desktop controls
        item {
            Card("Desktop on the monitor", Icons.Filled.DesktopWindows) {
                val running = DesktopLauncher.runningOnDisplay >= 0
                StatusLine(running, if (running) "BeyondPad desktop is open on display ${DesktopLauncher.runningOnDisplay}" else "Desktop not open")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Primary("Open desktop on monitor", enabled = ext != null) { activity.openDesktop() }
                    Secondary("Close desktop") { DesktopLauncher.close(activity) }
                    Secondary(if (presentationOn) "Stop presentation output" else "Presentation output (fallback)") { activity.togglePresentation() }
                }
                Spacer(Modifier.height(10.dp))
                Toggle("Open desktop automatically when a monitor connects", settings.autoOpenDesktop) { on ->
                    Prefs.update { it.copy(autoOpenDesktop = on) }
                }
                Toggle("Watch for monitors in the background", settings.monitorService) { on ->
                    Prefs.update { it.copy(monitorService = on) }
                    if (on) DisplayMonitorService.start(activity) else DisplayMonitorService.stop(activity)
                }
                Toggle("Open apps in windows (honoured only if the system has desktop windowing)", settings.freeformWindows) { on ->
                    Prefs.update { it.copy(freeformWindows = on) }
                }
                if (settings.autoOpenDesktop && !overlay) {
                    Dim("Android blocks apps from opening screens from the background. Allow 'Display over other apps' below so the desktop can open by itself; otherwise tap the notification.")
                }
            }
        }

        // 6. Permissions
        item {
            Card("Permissions", Icons.Filled.CheckCircle) {
                PermRow("Notifications (connect / disconnect alerts)", notifGranted) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermRow("Display over other apps (auto-open desktop on connect)", overlay) {
                    activity.startActivity(SystemAccess.overlaySettingsIntent(activity))
                }
                PermRow("Usage access (show which taskbar apps are active)", usage) {
                    activity.startActivity(SystemAccess.usageSettingsIntent())
                }
                PermRow("Secure settings via ADB (developer desktop toggles)", caps.writeSecureSettings, null)
            }
        }

        // 7. System desktop settings (ADB)
        item {
            Card("System desktop mode (OxygenOS / Android)", Icons.Filled.Warning) {
                Dim("These are Android developer options, not BeyondPad features. Whether OxygenOS honours them on the Pad 3 can only be confirmed on the device; the capability test above shows the real result.")
                Spacer(Modifier.height(8.dp))
                if (caps.writeSecureSettings) {
                    SettingToggle(activity, "Force desktop mode on external displays (reboot needed)", SystemAccess.KEY_FORCE_DESKTOP, caps.forceDesktopSetting, tick)
                    SettingToggle(activity, "Enable freeform windows", SystemAccess.KEY_FREEFORM, caps.freeformSetting, tick)
                    SettingToggle(activity, "Force activities to be resizable", SystemAccess.KEY_FORCE_RESIZABLE, caps.forceResizableSetting, tick)
                } else {
                    Dim("To let BeyondPad switch these for you, run this once from a computer with USB debugging on:")
                    CopyLine(activity, SystemAccess.grantCommand(activity))
                    Dim("Or set them yourself in Developer options.")
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("Developer options") { runCatching { activity.startActivity(SystemAccess.developerSettingsIntent()) }.onFailure { activity.toast("Enable Developer options first (tap Build number 7 times).") } }
                    Secondary("Display settings") { runCatching { activity.startActivity(SystemAccess.displaySettingsIntent()) } }
                    Secondary("Screen cast settings") { runCatching { activity.startActivity(SystemAccess.castSettingsIntent()) }.onFailure { activity.toast("Not available on this ROM.") } }
                }
            }
        }

        // 8. Diagnostics
        item(span = StaggeredGridItemSpan.FullLine) {
            Card("Diagnostics and event log", Icons.AutoMirrored.Filled.HelpOutline) {
                Text("All displays reported by Android", color = BP.Text, fontWeight = FontWeight.Medium)
                allDisplays.forEach { Mono(it) }
                Spacer(Modifier.height(10.dp))
                Text("Events", color = BP.Text, fontWeight = FontWeight.Medium)
                if (events.isEmpty()) Dim("No events yet.")
                events.take(30).forEach { Mono("${it.timeLabel}  ${it.message}") }
            }
        }
    }
}

private fun launchLabel(t: LaunchTest) = when (t) {
    LaunchTest.NOT_RUN -> "not run"
    LaunchTest.RUNNING -> "running…"
    LaunchTest.FULLSCREEN -> "apps open full screen on the monitor"
    LaunchTest.WINDOWED -> "apps open in resizable windows on the monitor"
    LaunchTest.LANDED_ELSEWHERE -> "system redirected the window to another display"
    LaunchTest.REFUSED -> "system refused"
}

@Composable
private fun Header(device: String, release: String, sdk: Int, build: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BP.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DesktopWindows, null, tint = BP.Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("BeyondPad", color = BP.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("$device · Android $release (API $sdk) · $build", color = BP.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Card(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BP.Surface)
            .border(1.dp, BP.Outline, RoundedCornerShape(16.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BP.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), color = BP.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MonitorDetails(d: ExternalDisplayInfo) {
    KV("Name", d.name)
    d.manufacturer?.let { KV("Manufacturer (PnP ID)", it) }
    d.productName?.let { KV("Product", it) }
    d.modelYear?.let { KV("Model year", it.toString()) }
    KV("Display ID", d.displayId.toString())
    KV("Native resolution", d.nativeMode?.let { "${it.width} × ${it.height}" } ?: "Unknown")
    KV("Current resolution", d.currentMode?.let { "${it.width} × ${it.height}" } ?: "Unknown")
    KV("Refresh rate", d.currentMode?.let { "%.2f Hz".format(it.refreshRate) } ?: "Unknown")
    KV("Refresh rates offered", d.supportedModes.map { "%.0f".format(it.refreshRate) }.distinct().joinToString(", ") { "$it Hz" })
    KV("Supported modes", d.supportedModes.size.toString())
    KV("HDR", if (d.hdrTypes.isEmpty()) "Not reported" else d.hdrTypes.joinToString())
    KV("Wide colour gamut", if (d.isWideColorGamut) "Yes" else "No")
    KV("Density", d.densityLabel)
    KV("Physical DPI", "%.0f × %.0f".format(d.xdpi, d.ydpi))
    KV("Orientation", d.orientationLabel)
    KV("State", d.stateLabel)
    KV("Flags", listOfNotNull(if (d.isPresentation) "presentation" else null, if (d.isSecure) "secure (HDCP)" else null).joinToString().ifEmpty { "none" })
}

@Composable private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, color = BP.TextDim, fontSize = 13.sp, modifier = Modifier.width(170.dp))
        Text(v, color = BP.Text, fontSize = 13.sp)
    }
}

@Composable private fun Dim(text: String) = Text(text, color = BP.TextDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))

@Composable private fun Mono(text: String) =
    Text(text, color = BP.TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))

@Composable private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) BP.Good else BP.Bad))
        Spacer(Modifier.width(10.dp))
        Text(text, color = BP.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun Check(text: String, ok: Boolean?) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        val (icon, tint) = when (ok) {
            true -> Icons.Filled.CheckCircle to BP.Good
            false -> Icons.Filled.Error to BP.Bad
            null -> Icons.AutoMirrored.Filled.HelpOutline to BP.TextDim
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = BP.Text, fontSize = 13.sp)
    }
}

@Composable private fun Tag(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp,
        modifier = Modifier.padding(start = 6.dp).border(1.dp, color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable private fun BPSwitch(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = BP.Accent))

@Composable private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = BP.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        BPSwitch(checked, onChange)
    }
}

@Composable private fun PermRow(text: String, granted: Boolean, onGrant: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
            tint = if (granted) BP.Good else BP.Warn, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = BP.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (!granted && onGrant != null) Secondary("Allow", onClick = onGrant)
        if (!granted && onGrant == null) Text("optional", color = BP.TextDim, fontSize = 12.sp)
    }
}

@Composable private fun SettingToggle(activity: MainActivity, text: String, key: String, value: Boolean?, tick: Int) {
    Toggle(text, value == true) { on ->
        val ok = SystemAccess.writeGlobal(activity, key, on)
        activity.toast(if (ok) "Saved: $key = ${if (on) 1 else 0}" else "The system rejected the change to $key")
        DisplayRepository.log("Setting $key → ${if (on) 1 else 0}: ${if (ok) "accepted" else "rejected"}")
        CapabilityProbe.refreshStatic(activity)
        activity.resumeTick.intValue = tick + 1
    }
}

@Composable private fun CopyLine(activity: MainActivity, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(BP.SurfaceHigh).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = BP.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", text))
            activity.toast("Copied")
        }) { Icon(Icons.Filled.ContentCopy, "Copy", tint = BP.Accent) }
    }
}

@Composable private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = BP.Accent)) { Text(text) }

@Composable private fun Secondary(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick, shape = RoundedCornerShape(10.dp)) { Text(text, color = BP.Accent) }

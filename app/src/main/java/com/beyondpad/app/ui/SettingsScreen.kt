package com.beyondpad.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.CrashLog
import com.beyondpad.app.Prefs
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.ConnectionSignals
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.LaunchTest
import com.beyondpad.app.service.DisplayMonitorService
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.ui.theme.BP

@Composable
fun SettingsScreen(activity: MainActivity, onBack: () -> Unit) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val caps by CapabilityProbe.capabilities.collectAsStateWithLifecycle()
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val signals by ConnectionSignals.signals.collectAsStateWithLifecycle()
    val events by DisplayRepository.events.collectAsStateWithLifecycle()
    val presentationOn by activity.presentationOn
    val tick = activity.resumeTick.intValue
    val ext = displays.firstOrNull()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { activity.resumeTick.intValue++ }
    val notif = remember(tick) {
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlay = remember(tick) { SystemAccess.canDrawOverlays(activity) }
    val usage = remember(tick) { SystemAccess.hasUsageAccess(activity) }
    var crashTick by remember { mutableIntStateOf(0) }
    val crash = remember(tick, crashTick) { CrashLog.read() }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val version = remember { runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "" }
    fun yn(b: Boolean?) = when (b) { true -> "Yes"; false -> "No"; null -> "Unknown" }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(16.dp))
            Text("Settings", color = BP.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)) {

                Label("Desktop on the monitor")
                Group {
                    Item("Open automatically", "When a monitor becomes available", trailing = {
                        Toggle(settings.autoOpenDesktop) { on -> Prefs.update { it.copy(autoOpenDesktop = on) } }
                    })
                    Item("Open apps in windows", "Only where Android supports desktop windowing", trailing = {
                        Toggle(settings.freeformWindows) { on -> Prefs.update { it.copy(freeformWindows = on) } }
                    })
                    Item("Watch for monitors in the background", "Notices a monitor even when BeyondPad is closed", trailing = {
                        Toggle(settings.monitorService) { on ->
                            Prefs.update { it.copy(monitorService = on) }
                            if (on) DisplayMonitorService.start(activity) else DisplayMonitorService.stop(activity)
                        }
                    })
                    Item("Presentation output", "Fallback that shows BeyondPad's own screen only, not a desktop", trailing = {
                        Toggle(presentationOn) { activity.togglePresentation() }
                    }, last = true)
                }

                Spacer(Modifier.height(28.dp))
                Label("Permissions")
                Group {
                    Perm("Notifications", "Connect and disconnect alerts", notif) {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Perm("Display over other apps", "Lets the desktop open by itself", overlay) {
                        activity.startActivity(SystemAccess.overlaySettingsIntent(activity))
                    }
                    Perm("Usage access", "Shows which apps are active", usage, last = true) {
                        activity.startActivity(SystemAccess.usageSettingsIntent())
                    }
                }

                if (ext != null) {
                    Spacer(Modifier.height(28.dp))
                    Label("Monitor")
                    Group {
                        val rows = buildList {
                            add("Name" to (ext.productName ?: ext.name))
                            ext.manufacturer?.let { add("Maker" to it) }
                            ext.modelYear?.let { add("Year" to it.toString()) }
                            add("Running" to (ext.currentMode?.label ?: "Unknown"))
                            add("Native" to (ext.nativeMode?.let { "${it.width} × ${it.height}" } ?: "Unknown"))
                            add("HDR" to ext.hdrTypes.ifEmpty { listOf("Not reported") }.joinToString())
                            add("Density" to ext.densityLabel)
                            add("Display ID" to ext.displayId.toString())
                        }
                        rows.forEachIndexed { i, (k, v) -> Item(k, null, value = v, last = i == rows.lastIndex) }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Label("Diagnostics")
                Group {
                    Item("Verdict", null, value = caps.verdict.title)
                    Item("Apps may open on the monitor", null, value = yn(caps.activityStartAllowed))
                    Item("Window test", caps.launchTestDetail.ifEmpty { null }, value = when (caps.launchTest) {
                        LaunchTest.NOT_RUN -> "Not run"; LaunchTest.RUNNING -> "Running…"
                        LaunchTest.FULLSCREEN -> "Full screen"; LaunchTest.WINDOWED -> "Free windows"
                        LaunchTest.LANDED_ELSEWHERE -> "Redirected"; LaunchTest.REFUSED -> "Refused"
                    })
                    Item("Free-form windows feature", null, value = yn(caps.freeformFeature))
                    Item("Monitor signal", null, value = if (signals.hdmiAudio) "Detected" else "Not detected")
                    Item("On the USB-C port", null, value = signals.usb.ifEmpty { null }?.joinToString { it.name } ?: "Nothing")
                    Item(
                        "Run window test", if (ext == null) "Needs a monitor" else "Opens a test window on the monitor for a moment",
                        onClick = if (ext != null) ({ CapabilityProbe.runLaunchTest(activity) }) else null, last = true,
                    )
                }

                AnimatedVisibility(crash != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(28.dp))
                        Label("Last crash")
                        Group {
                            Text(
                                crash.orEmpty().take(1500), color = BP.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp, modifier = Modifier.padding(18.dp),
                            )
                            Item("Copy crash report", "Paste it to Claude so it can be fixed", onClick = {
                                activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("BeyondPad crash", crash))
                                activity.toast("Crash report copied")
                            })
                            Item("Clear", null, onClick = { CrashLog.clear(); crashTick++ }, last = true)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Group {
                    Item(if (showLog) "Hide event log" else "Show event log", "${events.size} events", onClick = { showLog = !showLog }, last = !showLog)
                    AnimatedVisibility(showLog, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column {
                            DisplayRepository.allDisplaysSummary().forEach { Item(it, null, mono = true) }
                            if (events.isEmpty()) Item("No events yet", null, last = true)
                            events.take(40).forEachIndexed { i, e -> Item(e.message, e.timeLabel, mono = true, last = i == minOf(39, events.lastIndex)) }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "BeyondPad $version · works offline, no developer options\n${caps.device} · ${caps.build}",
                    color = BP.TextFaint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Perm(title: String, sub: String, granted: Boolean, last: Boolean = false, onGrant: () -> Unit) {
    Item(title, sub, last = last, onClick = if (granted) null else onGrant, trailing = {
        if (granted) Text("Allowed", color = BP.TextDim, fontSize = 14.sp)
        else Text("Allow", color = BP.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    })
}

package com.beyondpad.app.desktop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ModeController
import com.beyondpad.app.display.ModeResult
import com.beyondpad.app.ui.theme.BP
import com.beyondpad.app.ui.theme.BpIcons
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.isAltPressed
import com.beyondpad.app.ui.theme.BeyondPadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The desktop shell shown ON the external display: wallpaper with pinned shortcuts, a Start menu
 * with every launchable app, and a taskbar with pinned and running apps. Every icon really opens
 * the app on this display through ActivityOptions.setLaunchDisplayId().
 */
class DesktopActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DISPLAY = "display_id"
        const val ACTION_CLOSE = "com.beyondpad.app.CLOSE_DESKTOP"
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = finishAndRemoveTask()
    }

    private val onDisconnect: (Int) -> Unit = { id -> if (id == myDisplayId()) finishAndRemoveTask() }

    private fun myDisplayId(): Int = display?.displayId ?: Display.DEFAULT_DISPLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (myDisplayId() == Display.DEFAULT_DISPLAY) {
            // Monitor vanished before we arrived, or the ROM put us on the tablet: don't take over the tablet.
            DisplayRepository.log("Desktop landed on the tablet screen instead of the monitor, closing it.")
            finishAndRemoveTask()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(this, closeReceiver, IntentFilter(ACTION_CLOSE), ContextCompat.RECEIVER_NOT_EXPORTED)
        DisplayRepository.addDisconnectListener(onDisconnect)
        DesktopLauncher.runningOnDisplay = myDisplayId()
        ModeController.desktopWindow = myDisplayId() to window

        BP.apply(this, Prefs.settings.value)
        setContent {
            // "Desktop size" in Settings scales only BeyondPad's own desktop on the monitor.
            val st by Prefs.settings.collectAsStateWithLifecycle()
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * st.desktopScale, base.fontScale)) {
                BeyondPadTheme { DesktopScreen(myDisplayId()) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (myDisplayId() == Display.DEFAULT_DISPLAY) { finishAndRemoveTask(); return }
        ModeController.desktopWindow = myDisplayId() to window
        ModeController.applyToDesktop()
        window.decorView.postDelayed({ reportWindowing() }, 500)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // When a monitor is unplugged Android moves its tasks to the tablet. Leave the tablet alone.
        if (myDisplayId() == Display.DEFAULT_DISPLAY) finishAndRemoveTask()
    }

    private fun reportWindowing() {
        if (isFinishing) return
        val cur = windowManager.currentWindowMetrics.bounds
        val max = windowManager.maximumWindowMetrics.bounds
        val windowed = cur.width() < max.width() * 0.95 || cur.height() < max.height() * 0.95
        CapabilityProbe.noteDesktopWindow(windowed, "${cur.width()}×${cur.height()} of ${max.width()}×${max.height()}")
    }

    override fun onDestroy() {
        DisplayRepository.removeDisconnectListener(onDisconnect)
        if (ModeController.desktopWindow?.second === window) ModeController.desktopWindow = null
        if (DesktopLauncher.runningOnDisplay == myDisplayId()) DesktopLauncher.runningOnDisplay = -1
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopScreen(displayId: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val displays by DisplayRepository.externalDisplays.collectAsStateWithLifecycle()
    val modeStatus by ModeController.status.collectAsStateWithLifecycle()
    val info = displays.firstOrNull { it.displayId == displayId }

    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { AppRepository.load(ctx) }
    }
    val foreground by produceState<Set<String>?>(initialValue = null) {
        while (true) {
            value = withContext(Dispatchers.IO) { AppRepository.foregroundPackages(ctx) }
            delay(10_000)
        }
    }
    val clock by produceState(initialValue = "") {
        val f = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault())
        while (true) { value = f.format(Date()); delay(5000) }
    }

    val sessionApps = DesktopSession.opened
    var drawerOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }
    val wallpaper by produceState<ImageBitmap?>(null, Wallpaper.version, settings.hasWallpaper) {
        value = if (settings.hasWallpaper) withContext(Dispatchers.IO) { Wallpaper.load(ctx) } else null
    }
    val time by produceState(initialValue = "" to "") {
        val t = SimpleDateFormat("HH:mm", Locale.getDefault())
        val d = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        while (true) { val now = Date(); value = t.format(now) to d.format(now); delay(5000) }
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var cascade by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(toast) { if (toast != null) { delay(3500); toast = null } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(settings.autoResolution, settings.manualModeKey) { ModeController.applyToDesktop() }

    val style = if (settings.freeformWindows) WindowStyle.WINDOW else WindowStyle.FULLSCREEN
    fun open(app: AppEntry, target: Int = displayId, newInstance: Boolean = false, forceStyle: WindowStyle? = null) {
        val r = AppLauncher.launch(ctx, app, target, forceStyle ?: style, newInstance, cascade++)
        if (r.ok && target == displayId) DesktopSession.noteOpened(app.key)
        if (r.ok && target != displayId) DesktopSession.noteMovedAway(app.key)
        toast = if (r.ok && target != displayId) "${app.label} opened on the tablet" else r.message
        switcherOpen = false
        drawerOpen = false
    }
    fun togglePin(app: AppEntry) {
        Prefs.update { s -> s.copy(pinned = if (app.key in s.pinned) s.pinned - app.key else s.pinned + app.key) }
    }

    val byKey = apps.associateBy { it.key }
    val pinnedApps = settings.pinned.mapNotNull { byKey[it] }
    val taskbarApps = (pinnedApps + sessionApps.mapNotNull { byKey[it] }).distinctBy { it.key }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BP.SurfaceHigh, BP.Background, BP.AccentSoft)))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when {
                    e.key == Key.MetaLeft || e.key == Key.MetaRight || (e.isCtrlPressed && e.key == Key.Spacebar) -> {
                        drawerOpen = !drawerOpen; true
                    }
                    e.isAltPressed && e.key == Key.Tab -> { switcherOpen = !switcherOpen; drawerOpen = false; true }
                    e.key == Key.Escape && (drawerOpen || panelOpen || switcherOpen) -> { drawerOpen = false; panelOpen = false; switcherOpen = false; true }
                    else -> false
                }
            },
    ) {
        // Wallpaper with a soft scrim so text and icons stay readable
        wallpaper?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0x88000000)))))
        }
        val onDesk = if (wallpaper != null) Color.White else BP.Text
        val onDeskDim = if (wallpaper != null) Color(0xCCFFFFFF) else BP.TextDim

        // Clock and desktop shortcuts
        Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 36.dp, end = 40.dp, bottom = 80.dp)) {
            Text(time.first, color = onDesk, fontSize = 72.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Text(time.second, color = onDeskDim, fontSize = 18.sp, modifier = Modifier.padding(bottom = 28.dp))
            if (pinnedApps.isEmpty()) {
                Text(
                    "Open Start (bottom left, or the Windows key) and right-click an app to pin it here.",
                    color = onDeskDim, fontSize = 14.sp, modifier = Modifier.width(520.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pinnedApps, key = { "d_" + it.key }) { app ->
                    AppTile(
                        app = app, size = 56, showLabel = true, labelColor = onDesk,
                        indicator = null,
                        onOpen = { open(app) },
                        menu = { dismiss -> AppMenuItems(app, true, dismiss, ::open, ::togglePin, displayId) },
                    )
                }
            }
        }

        // App switcher (taskbar button or Alt+Tab)
        AnimatedVisibility(
            switcherOpen,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.94f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.96f),
        ) {
            val openApps = sessionApps.toList().asReversed().mapNotNull { byKey[it] }
            Surface(color = BP.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.border(1.dp, BP.Outline, RoundedCornerShape(20.dp))) {
                Column(Modifier.padding(24.dp)) {
                    Text("Open on this monitor", color = BP.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    if (openApps.isEmpty()) Text("Nothing opened from BeyondPad yet.", color = BP.TextDim, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        openApps.take(8).forEach { app ->
                            Box {
                                Column(
                                    Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).background(BP.SurfaceHigh)
                                        .clickable { open(app) }.padding(vertical = 18.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(app.icon, app.label, Modifier.size(52.dp))
                                    Text(app.label, color = BP.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                                }
                                Icon(
                                    Icons.Filled.Close, "Remove from list", tint = BP.TextDim,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                                        .clickable { DesktopSession.noteMovedAway(app.key) }.padding(3.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Alt+Tab opens this. Click an app to bring it to the front.", color = BP.TextFaint, fontSize = 12.sp)
                }
            }
        }

        // Start menu
        AnimatedVisibility(
            drawerOpen,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 68.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f, transformOrigin = TransformOrigin(0f, 1f)) + slideInVertically(tween(200)) { it / 12 },
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.95f, transformOrigin = TransformOrigin(0f, 1f)),
        ) {
            StartMenu(
                apps = apps,
                pinned = settings.pinned.toSet(),
                onOpen = { open(it) },
                onClose = { drawerOpen = false },
                menu = { app, dismiss -> AppMenuItems(app, app.key in settings.pinned, dismiss, ::open, ::togglePin, displayId) },
                modifier = Modifier,
            )
        }

        // Quick display panel
        AnimatedVisibility(
            panelOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 68.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            Surface(
                color = BP.Surface, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(340.dp)
                    .border(1.dp, BP.Outline, RoundedCornerShape(14.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("This monitor", color = BP.Text, fontSize = 16.sp)
                    Text(info?.name ?: "Display $displayId", color = BP.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Running: ${info?.currentMode?.label ?: "unknown"}", color = BP.Text, fontSize = 13.sp)
                    Text("Density: ${info?.densityLabel ?: "unknown"}", color = BP.TextDim, fontSize = 13.sp)
                    val statusColor = when (modeStatus.result) {
                        ModeResult.VERIFIED -> BP.Good
                        ModeResult.NOT_APPLIED -> BP.Warn
                        else -> BP.TextDim
                    }
                    if (modeStatus.message.isNotEmpty()) {
                        Text(modeStatus.message, color = statusColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    PanelRow(
                        if (settings.freeformWindows) BpIcons.Window else BpIcons.Fullscreen,
                        if (settings.freeformWindows) "New apps open in a window (if the system allows)" else "New apps open full screen",
                    ) { Prefs.update { it.copy(freeformWindows = !it.freeformWindows) } }
                    PanelRow(Icons.Filled.Settings, "Resolution and diagnostics are on the tablet's BeyondPad app") { panelOpen = false }
                }
            }
        }

        // Taskbar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(BP.Taskbar)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskbarButton(BpIcons.Apps, "Start", highlighted = drawerOpen) { drawerOpen = !drawerOpen; panelOpen = false; switcherOpen = false }
            TaskbarButton(BpIcons.Switcher, "Switch apps", highlighted = switcherOpen) { switcherOpen = !switcherOpen; drawerOpen = false; panelOpen = false }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(1.dp).height(28.dp).background(BP.Outline))
            Spacer(Modifier.width(6.dp))
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                items(taskbarApps, key = { "t_" + it.key }) { app ->
                    val launchedHere = app.key in sessionApps
                    val active = foreground?.contains(app.packageName) == true
                    AppTile(
                        app = app, size = 34, showLabel = false, labelColor = Color.White,
                        indicator = when {
                            launchedHere && active -> BP.Accent
                            launchedHere -> BP.TextDim
                            else -> null
                        },
                        onOpen = { open(app) },
                        menu = { dismiss -> AppMenuItems(app, app.key in settings.pinned, dismiss, ::open, ::togglePin, displayId) },
                    )
                }
            }
            toast?.let {
                Text(it, color = BP.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp).width(300.dp))
            }
            TaskbarButton(BpIcons.Monitor, info?.currentMode?.let { "${it.width}×${it.height} ${it.refreshRate.toInt()}Hz" } ?: "Display",
                highlighted = panelOpen, showText = true) { panelOpen = !panelOpen; drawerOpen = false }
            Text(clock, color = BP.Text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
            TaskbarButton(Icons.Filled.Close, "Close desktop") { (ctx as? ComponentActivity)?.finishAndRemoveTask() }
        }
    }
}

@Composable
private fun PanelRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = BP.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = BP.Text, fontSize = 13.sp)
    }
}

@Composable
private fun TaskbarButton(icon: ImageVector, label: String, highlighted: Boolean = false, showText: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) BP.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (highlighted) Color.White else BP.Accent, modifier = Modifier.size(24.dp))
        if (showText) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = BP.Text, fontSize = 12.sp)
        }
    }
}

/** App icon with left-click/tap to open and right-click/long-press for the window menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    size: Int,
    showLabel: Boolean,
    labelColor: Color,
    indicator: Color?,
    onOpen: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(app.key) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                menuOpen = true
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(app.icon, app.label, Modifier.size(size.dp))
            if (showLabel) {
                Text(
                    app.label, color = labelColor, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).width(92.dp),
                )
            }
            if (indicator != null) {
                Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape).background(indicator))
            } else if (!showLabel) {
                Spacer(Modifier.height(6.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menu { menuOpen = false }
        }
    }
}

@Composable
private fun AppMenuItems(
    app: AppEntry,
    pinned: Boolean,
    dismiss: () -> Unit,
    open: (AppEntry, Int, Boolean, WindowStyle?) -> Unit,
    togglePin: (AppEntry) -> Unit,
    displayId: Int,
) {
    DropdownMenuItem(text = { Text("Open in a window") }, onClick = { dismiss(); open(app, displayId, false, WindowStyle.WINDOW) })
    DropdownMenuItem(text = { Text("Open full screen") }, onClick = { dismiss(); open(app, displayId, false, WindowStyle.FULLSCREEN) })
    DropdownMenuItem(text = { Text("Open a new window") }, onClick = { dismiss(); open(app, displayId, true, null) })
    DropdownMenuItem(text = { Text("Open on the tablet") }, onClick = { dismiss(); open(app, Display.DEFAULT_DISPLAY, true, WindowStyle.FULLSCREEN) })
    DropdownMenuItem(text = { Text(if (pinned) "Unpin" else "Pin to desktop and taskbar") }, onClick = { dismiss(); togglePin(app) })
}

@Composable
private fun StartMenu(
    apps: List<AppEntry>,
    pinned: Set<String>,
    onOpen: (AppEntry) -> Unit,
    onClose: () -> Unit,
    menu: @Composable (AppEntry, () -> Unit) -> Unit,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, true) }
    }
    Surface(
        color = BP.Surface, shape = RoundedCornerShape(16.dp),
        modifier = modifier.width(560.dp).height(520.dp).border(1.dp, BP.Outline, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BP.Accent, unfocusedBorderColor = BP.Outline),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && e.key == Key.Enter && shown.isNotEmpty()) { onOpen(shown.first()); true }
                    else if (e.type == KeyEventType.KeyUp && e.key == Key.Escape) { onClose(); true } else false
                },
            )
            Spacer(Modifier.height(10.dp))
            Text("${shown.size} apps. Right-click for window options.", color = BP.TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { "s_" + it.key }) { app ->
                    AppTile(
                        app = app, size = 44, showLabel = true, labelColor = BP.Text,
                        indicator = if (app.key in pinned) BP.AccentSoft else null,
                        onOpen = { onOpen(app) },
                        menu = { dismiss -> menu(app, dismiss) },
                    )
                }
            }
        }
    }
}

package com.beyondpad.app.ui

import android.view.Display
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
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
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.ui.theme.BP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controls the monitor from the tablet's touchscreen, for when no mouse is connected:
 * open apps on the monitor, bring one forward, send one back to the tablet, show the desktop.
 */
@Composable
fun MonitorRemote(activity: MainActivity, ext: ExternalDisplayInfo) {
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

    fun openOnMonitor(app: AppEntry) {
        val r = AppLauncher.launch(activity, app, ext.displayId, style)
        if (r.ok) DesktopSession.noteOpened(app.key)
        activity.toast(if (r.ok) "${app.label} opened on the monitor" else r.message)
    }

    Label("Control the monitor from here")
    Group {
        Item(
            "Show desktop on monitor", "Brings the BeyondPad desktop to the front of the monitor",
            onClick = { activity.openDesktop() },
        )
        val opened = DesktopSession.opened.toList().asReversed().mapNotNull { byKey[it] }
        if (opened.isEmpty()) {
            Item("Nothing opened on the monitor yet", "Tap an app below to open it there", last = true)
        } else {
            opened.forEachIndexed { i, app ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(app.icon, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, color = BP.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    SmallAction("Show") { openOnMonitor(app) }
                    Spacer(Modifier.width(8.dp))
                    SmallAction("Move to tablet") {
                        val r = AppLauncher.launch(activity, app, Display.DEFAULT_DISPLAY, WindowStyle.FULLSCREEN)
                        if (r.ok) DesktopSession.noteMovedAway(app.key)
                        activity.toast(if (r.ok) "Asked the system to bring ${app.label} to the tablet" else r.message)
                    }
                }
                if (i != opened.lastIndex) Spacer(Modifier.fillMaxWidth().padding(start = 18.dp).height(1.dp).background(BP.Outline))
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Label("Open an app on the monitor")
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BP.Accent, unfocusedBorderColor = BP.Outline),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    // Bounded height: this grid sits inside the dashboard's scrolling column.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        modifier = Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(14.dp)).background(BP.Surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(shown, key = { it.key }) { app ->
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { openOnMonitor(app) }.padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(app.icon, app.label, Modifier.size(44.dp))
                Text(
                    app.label, color = BP.Text, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    if (DesktopLauncher.runningOnDisplay < 0) {
        Spacer(Modifier.height(6.dp))
        Text("Apps open on the monitor even when the BeyondPad desktop is closed.", color = BP.TextFaint, fontSize = 12.sp)
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Text(
        text, color = BP.Accent, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BP.SurfaceHigh).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

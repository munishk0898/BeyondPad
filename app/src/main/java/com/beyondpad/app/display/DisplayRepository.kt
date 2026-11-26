package com.beyondpad.app.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DisplayEvent(val time: Long, val message: String) {
    val timeLabel: String get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
}

/**
 * Single source of truth for connected displays. Uses only the public DisplayManager API:
 * any display other than the built-in one that apps are allowed to see is treated as external.
 */
object DisplayRepository {

    private lateinit var appContext: Context
    private lateinit var displayManager: DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _externalDisplays = MutableStateFlow<List<ExternalDisplayInfo>>(emptyList())
    val externalDisplays: StateFlow<List<ExternalDisplayInfo>> = _externalDisplays.asStateFlow()

    private val _events = MutableStateFlow<List<DisplayEvent>>(emptyList())
    val events: StateFlow<List<DisplayEvent>> = _events.asStateFlow()

    private val connectListeners = mutableListOf<(ExternalDisplayInfo) -> Unit>()
    private val disconnectListeners = mutableListOf<(Int) -> Unit>()

    private var started = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            val before = _externalDisplays.value.map { it.displayId }.toSet()
            refresh()
            val added = _externalDisplays.value.firstOrNull { it.displayId == displayId }
            if (added != null && displayId !in before) {
                log("Connected: ${added.name} (ID $displayId), ${added.currentMode?.label ?: "mode unknown"}")
                connectListeners.toList().forEach { it(added) }
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            val wasExternal = _externalDisplays.value.any { it.displayId == displayId }
            refresh()
            if (wasExternal) {
                log("Disconnected: display ID $displayId. Back to tablet-only operation.")
                disconnectListeners.toList().forEach { it(displayId) }
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            val old = _externalDisplays.value.firstOrNull { it.displayId == displayId }
            refresh()
            val new = _externalDisplays.value.firstOrNull { it.displayId == displayId }
            if (old != null && new != null && old.currentMode?.modeId != new.currentMode?.modeId) {
                log("Mode changed on ID $displayId: ${old.currentMode?.label} → ${new.currentMode?.label}")
            }
        }
    }

    fun init(context: Context) {
        if (started) return
        appContext = context.applicationContext
        displayManager = appContext.getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(listener, mainHandler)
        started = true
        refresh()
        _externalDisplays.value.forEach { log("Already connected at start: ${it.name} (ID ${it.displayId})") }
    }

    fun refresh() {
        if (!started) return
        _externalDisplays.value = displayManager.displays
            .filter { isExternalCandidate(it) }
            .map { ExternalDisplayInfo.from(it) }
    }

    /** All displays the OS reports, including the built-in one, for the diagnostics view. */
    fun allDisplaysSummary(): List<String> = displayManager.displays.map { d ->
        val f = d.flags
        val tags = buildList {
            if (d.displayId == Display.DEFAULT_DISPLAY) add("built-in")
            if (f and Display.FLAG_PRESENTATION != 0) add("presentation")
            if (f and Display.FLAG_PRIVATE != 0) add("private")
            if (f and Display.FLAG_SECURE != 0) add("secure")
        }
        "ID ${d.displayId}: ${d.name} ${d.mode.physicalWidth}×${d.mode.physicalHeight} [${tags.joinToString()}]"
    }

    fun display(displayId: Int): Display? = if (started) displayManager.getDisplay(displayId) else null

    fun primaryExternal(): ExternalDisplayInfo? = _externalDisplays.value.firstOrNull()

    fun addConnectListener(l: (ExternalDisplayInfo) -> Unit) { connectListeners += l }
    fun removeConnectListener(l: (ExternalDisplayInfo) -> Unit) { connectListeners -= l }
    fun addDisconnectListener(l: (Int) -> Unit) { disconnectListeners += l }
    fun removeDisconnectListener(l: (Int) -> Unit) { disconnectListeners -= l }

    fun log(message: String) {
        _events.update { (listOf(DisplayEvent(System.currentTimeMillis(), message)) + it).take(100) }
    }

    private fun isExternalCandidate(d: Display): Boolean {
        if (d.displayId == Display.DEFAULT_DISPLAY) return false
        // Private virtual displays (screen recorders, app-owned surfaces) are not monitors.
        if (d.flags and Display.FLAG_PRIVATE != 0) return false
        return true
    }
}

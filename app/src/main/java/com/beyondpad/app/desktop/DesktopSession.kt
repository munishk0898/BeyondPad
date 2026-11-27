package com.beyondpad.app.desktop

import androidx.compose.runtime.mutableStateListOf
import com.beyondpad.app.Prefs

/** Apps opened on the monitor in this session, shared by the monitor's taskbar and the tablet's remote. */
object DesktopSession {
    val opened = mutableStateListOf<String>()

    fun noteOpened(key: String) {
        opened.remove(key)
        opened.add(key)
        remember()
    }

    fun noteMovedAway(key: String) {
        opened.remove(key)
        remember()
    }

    /** Kept so the same apps can be reopened after the monitor is unplugged and plugged back in. */
    private fun remember() {
        val keep = opened.takeLast(8)
        if (keep.isNotEmpty()) Prefs.update { it.copy(lastSession = keep) }
    }
}

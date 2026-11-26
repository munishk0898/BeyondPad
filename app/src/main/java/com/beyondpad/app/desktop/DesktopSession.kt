package com.beyondpad.app.desktop

import androidx.compose.runtime.mutableStateListOf

/** Apps opened on the monitor in this session, shared by the monitor's taskbar and the tablet's remote. */
object DesktopSession {
    val opened = mutableStateListOf<String>()

    fun noteOpened(key: String) {
        opened.remove(key)
        opened.add(key)
    }

    fun noteMovedAway(key: String) {
        opened.remove(key)
    }
}

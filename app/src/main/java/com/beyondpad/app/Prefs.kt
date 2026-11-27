package com.beyondpad.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val autoResolution: Boolean = true,
    val manualModeKey: String? = null,
    val autoOpenDesktop: Boolean = true,
    val monitorService: Boolean = true,
    val freeformWindows: Boolean = true,
    val pinned: List<String> = emptyList(),
    /** "system", "dark" or "light". */
    val theme: String = "system",
    val accent: Int = 0,
    val onboarded: Boolean = false,
    val lastVersion: String = "",
    val lastSession: List<String> = emptyList(),
    /** Size of the BeyondPad desktop on the monitor (1 = default). */
    val desktopScale: Float = 1f,
    val hasWallpaper: Boolean = false,
    /** "monitorName|refreshRate" once OxygenOS has refused to change the rate for that monitor. */
    val lockedRate: String? = null,
    val crashSeen: String = "",
    val cursorSpeed: Float = 1.6f,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun SharedPreferences.list(key: String) = (getString(key, "") ?: "").split('|').filter { it.isNotBlank() }

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("beyondpad", Context.MODE_PRIVATE)
        _settings.value = Settings(
            autoResolution = sp.getBoolean("autoResolution", true),
            manualModeKey = sp.getString("manualModeKey", null),
            autoOpenDesktop = sp.getBoolean("autoOpenDesktop", true),
            monitorService = sp.getBoolean("monitorService", true),
            freeformWindows = sp.getBoolean("freeformWindows", true),
            pinned = sp.list("pinned"),
            theme = sp.getString("theme", "system") ?: "system",
            accent = sp.getInt("accent", 0),
            onboarded = sp.getBoolean("onboarded", false),
            lastVersion = sp.getString("lastVersion", "") ?: "",
            lastSession = sp.list("lastSession"),
            desktopScale = sp.getFloat("desktopScale", 1f),
            hasWallpaper = sp.getBoolean("hasWallpaper", false),
            lockedRate = sp.getString("lockedRate", null),
            crashSeen = sp.getString("crashSeen", "") ?: "",
            cursorSpeed = sp.getFloat("cursorSpeed", 1.6f),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit()
            .putBoolean("autoResolution", s.autoResolution)
            .putString("manualModeKey", s.manualModeKey)
            .putBoolean("autoOpenDesktop", s.autoOpenDesktop)
            .putBoolean("monitorService", s.monitorService)
            .putBoolean("freeformWindows", s.freeformWindows)
            .putString("pinned", s.pinned.joinToString("|"))
            .putString("theme", s.theme)
            .putInt("accent", s.accent)
            .putBoolean("onboarded", s.onboarded)
            .putString("lastVersion", s.lastVersion)
            .putString("lastSession", s.lastSession.joinToString("|"))
            .putFloat("desktopScale", s.desktopScale)
            .putBoolean("hasWallpaper", s.hasWallpaper)
            .putString("lockedRate", s.lockedRate)
            .putString("crashSeen", s.crashSeen)
            .putFloat("cursorSpeed", s.cursorSpeed)
            .apply()
    }
}

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
    val adbPaired: Boolean = false,
    val autoEnableMonitor: Boolean = true,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("beyondpad", Context.MODE_PRIVATE)
        _settings.value = Settings(
            autoResolution = sp.getBoolean("autoResolution", true),
            manualModeKey = sp.getString("manualModeKey", null),
            autoOpenDesktop = sp.getBoolean("autoOpenDesktop", true),
            monitorService = sp.getBoolean("monitorService", true),
            freeformWindows = sp.getBoolean("freeformWindows", true),
            pinned = sp.getString("pinned", "")!!.split('|').filter { it.isNotBlank() },
            adbPaired = sp.getBoolean("adbPaired", false),
            autoEnableMonitor = sp.getBoolean("autoEnableMonitor", true),
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
            .putBoolean("adbPaired", s.adbPaired)
            .putBoolean("autoEnableMonitor", s.autoEnableMonitor)
            .apply()
    }
}

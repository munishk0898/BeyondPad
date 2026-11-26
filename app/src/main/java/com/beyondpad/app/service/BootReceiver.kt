package com.beyondpad.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beyondpad.app.Prefs

/** Restarts the connection monitor after a reboot or app update, if the user left it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // BeyondPadApp.onCreate already ran and started the service if enabled; this is a safety net.
        Prefs.init(context)
        if (Prefs.settings.value.monitorService) DisplayMonitorService.start(context)
    }
}

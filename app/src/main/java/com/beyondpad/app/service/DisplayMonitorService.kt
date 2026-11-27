package com.beyondpad.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.beyondpad.app.Prefs
import com.beyondpad.app.R
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ExternalDisplayInfo
import com.beyondpad.app.system.SystemAccess
import com.beyondpad.app.ui.MainActivity

/**
 * Keeps a DisplayListener alive while the app is in the background, so a monitor plugged in at any
 * time is detected, announced, and (if the user allows) gets the desktop opened on it.
 */
class DisplayMonitorService : Service() {

    companion object {
        private const val CH_MONITOR = "monitor"
        private const val CH_EVENTS = "events"
        private const val ID_ONGOING = 1
        private const val ID_EVENT = 2

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DisplayMonitorService::class.java))
            } catch (e: Exception) {
                // Android blocks starting foreground services from some background states; the
                // dashboard starts it again next time it is opened.
                DisplayRepository.log("Monitor service not started now: ${e.javaClass.simpleName}")
            }
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, DisplayMonitorService::class.java))
    }

    private val handler = Handler(Looper.getMainLooper())

    private val onConnect: (ExternalDisplayInfo) -> Unit = { info ->
        updateOngoing()
        notifyEvent("${info.productName ?: info.name} connected", info.currentMode?.label ?: "Ready to use", true)
        if (Prefs.settings.value.autoOpenDesktop) {
            // Give the system a moment to finish configuring the new display.
            handler.postDelayed({ autoOpen(info.displayId) }, 1500)
        }
    }

    private val onDisconnect: (Int) -> Unit = {
        updateOngoing()
        notifyEvent("Monitor disconnected", "Everything is back on the tablet.", false)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MONITOR, getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT))
        ServiceCompat.startForeground(this, ID_ONGOING, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        DisplayRepository.addConnectListener(onConnect)
        DisplayRepository.addDisconnectListener(onDisconnect)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateOngoing()
        return START_STICKY
    }

    override fun onDestroy() {
        DisplayRepository.removeConnectListener(onConnect)
        DisplayRepository.removeDisconnectListener(onDisconnect)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun autoOpen(displayId: Int) {
        if (DisplayRepository.externalDisplays.value.none { it.displayId == displayId }) return
        if (DesktopLauncher.runningOnDisplay == displayId) return
        if (!SystemAccess.canDrawOverlays(this)) {
            DisplayRepository.log("Desktop not opened by itself: allow Display over other apps, or tap Show desktop in the notification.")
            return
        }
        val r = DesktopLauncher.open(this, displayId)
        if (!r.ok) notifyEvent("Could not open the desktop", r.message, false)
    }

    private fun mainIntent(openDesktop: Boolean): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_DESKTOP, openDesktop)
        return PendingIntent.getActivity(
            this, if (openDesktop) 1 else 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun hop(touchpad: Boolean): PendingIntent = PendingIntent.getActivity(
        this, if (touchpad) 11 else 10,
        Intent(this, com.beyondpad.app.desktop.OpenDesktopActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(com.beyondpad.app.desktop.OpenDesktopActivity.EXTRA_TOUCHPAD, touchpad),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(): Notification {
        val ext = DisplayRepository.primaryExternal()
        val text = ext?.let { "${it.productName ?: it.name} · ${it.currentMode?.label ?: ""}" } ?: "Waiting for a monitor"
        return NotificationCompat.Builder(this, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BeyondPad")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(mainIntent(false))
            .apply {
                if (ext != null) {
                    addAction(0, "Show desktop", hop(false))
                    if (com.beyondpad.app.BuildConfig.TOUCHPAD) addAction(0, "Touchpad", hop(true))
                }
            }
            .build()
    }

    private fun updateOngoing() {
        getSystemService(NotificationManager::class.java).notify(ID_ONGOING, ongoing())
    }

    private fun notifyEvent(title: String, text: String, withDesktopAction: Boolean) {
        val b = NotificationCompat.Builder(this, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(mainIntent(false))
        if (withDesktopAction) {
            b.addAction(0, "Show desktop", hop(false))
            if (com.beyondpad.app.BuildConfig.TOUCHPAD) b.addAction(0, "Touchpad", hop(true))
        }
        try {
            getSystemService(NotificationManager::class.java).notify(ID_EVENT, b.build())
        } catch (_: SecurityException) { /* notifications not allowed */ }
    }
}

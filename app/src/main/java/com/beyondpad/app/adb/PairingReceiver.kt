package com.beyondpad.app.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.beyondpad.app.R

/**
 * The pairing dialog in Settings closes as soon as you switch apps, so the code is typed straight
 * into a BeyondPad notification (inline reply) while the dialog stays on screen.
 */
class PairingReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL = "pairing"
        private const val ID = 7
        private const val KEY = "code"
        private const val ACTION = "com.beyondpad.app.PAIR"

        fun show(ctx: Context, status: String? = null) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH))
            val reply = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, PairingReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val action = NotificationCompat.Action.Builder(0, "Enter pairing code", reply)
                .addRemoteInput(RemoteInput.Builder(KEY).setLabel("6-digit code").build())
                .build()
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Pair BeyondPad with Wireless debugging")
                .setContentText(status ?: "Open Pair device with pairing code, then type the code here.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    status ?: "Settings › Developer options › Wireless debugging › Pair device with pairing code. Keep that dialog open, pull down this notification and type the 6-digit code.",
                ))
                .setOnlyAlertOnce(true)
                .setOngoing(status == null)
                .addAction(action)
                .build()
            runCatching { nm.notify(ID, n) }
        }

        fun dismiss(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY)?.toString() ?: return
        val app = context.applicationContext
        val pending = goAsync()
        show(app, "Pairing…")
        SelfAdb.pair(code) { ok, msg ->
            if (ok) {
                show(app, "Paired. BeyondPad will now switch on your monitor whenever it is plugged in.")
            } else {
                show(app, null)
                show(app, "$msg Get a new code and try again.")
            }
            pending.finish()
        }
    }
}

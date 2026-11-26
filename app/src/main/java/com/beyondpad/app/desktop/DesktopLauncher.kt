package com.beyondpad.app.desktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.beyondpad.app.display.DisplayRepository

/** Opens or closes the BeyondPad desktop shell on an external display. */
object DesktopLauncher {
    @Volatile var runningOnDisplay: Int = -1
        internal set

    fun open(ctx: Context, displayId: Int): AppLauncher.Result {
        val intent = Intent(ctx, DesktopActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(DesktopActivity.EXTRA_DISPLAY, displayId)
        return try {
            ctx.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            DisplayRepository.log("Opening BeyondPad desktop on display $displayId")
            AppLauncher.Result(true, "Desktop opening on the monitor")
        } catch (e: SecurityException) {
            DisplayRepository.log("System refused to open the desktop on display $displayId: ${e.message}")
            AppLauncher.Result(false, "The system refused to open the desktop on the monitor.")
        } catch (e: Exception) {
            AppLauncher.Result(false, "Could not open the desktop: ${e.message}")
        }
    }

    fun close(ctx: Context) {
        ctx.sendBroadcast(Intent(DesktopActivity.ACTION_CLOSE).setPackage(ctx.packageName))
    }
}

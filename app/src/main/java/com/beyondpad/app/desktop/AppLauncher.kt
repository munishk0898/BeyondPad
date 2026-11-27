package com.beyondpad.app.desktop

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.view.Display
import com.beyondpad.app.display.DisplayRepository

enum class WindowStyle { FULLSCREEN, WINDOW }

/**
 * Opens apps on a chosen display with the public ActivityOptions.setLaunchDisplayId() API.
 * Window bounds are only a request: the OS honours them only when freeform/desktop windowing is
 * active on that display. Moving an already-running app between displays is also up to the OS.
 */
object AppLauncher {

    data class Result(val ok: Boolean, val message: String)

    fun launch(
        ctx: Context,
        app: AppEntry,
        displayId: Int,
        style: WindowStyle,
        newInstance: Boolean = false,
        cascadeIndex: Int = 0,
    ): Result {
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        if (style == WindowStyle.WINDOW && displayId != Display.DEFAULT_DISPLAY) {
            defaultWindowBounds(displayId, cascadeIndex)?.let { opts.setLaunchBounds(it) }
        }
        val la = ctx.getSystemService(LauncherApps::class.java)
        return try {
            if (newInstance) {
                // LauncherApps cannot add flags, so a new window uses the plain launch intent.
                val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.setComponent(app.component)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    ?: return Result(false, "${app.label} has no launch intent.")
                ctx.startActivity(intent, opts.toBundle())
            } else {
                la.startMainActivity(app.component, app.user, null, opts.toBundle())
            }
            DisplayRepository.log("Opened ${app.label} on display $displayId (${style.name.lowercase()}${if (newInstance) ", new window" else ""})")
            Result(true, "Opened ${app.label}")
        } catch (e: SecurityException) {
            DisplayRepository.log("Refused ${app.label} on display $displayId: ${e.message}")
            Result(false, "The system refused to open ${app.label} on that display.")
        } catch (e: ActivityNotFoundException) {
            Result(false, "${app.label} could not be found.")
        } catch (e: Exception) {
            Result(false, "Could not open ${app.label}: ${e.message}")
        }
    }

    /**
     * Opens the app on the tablet as a fresh window instead of asking Android to drag its running
     * window over from the monitor. Moving a running window between screens of different size and
     * density makes OxygenOS and many apps restart or crash.
     */
    fun openOnTablet(ctx: Context, app: AppEntry): Result =
        launch(ctx, app, Display.DEFAULT_DISPLAY, WindowStyle.FULLSCREEN, newInstance = true)

    /** A centred window at roughly 60 % of the display, cascaded so several windows don't stack exactly. */
    private fun defaultWindowBounds(displayId: Int, index: Int): Rect? {
        val d = DisplayRepository.display(displayId) ?: return null
        val w = d.mode.physicalWidth
        val h = d.mode.physicalHeight
        val ww = (w * 0.6).toInt()
        val wh = (h * 0.65).toInt()
        val step = (minOf(w, h) * 0.04).toInt() * (index % 6)
        val left = (w - ww) / 2 - (minOf(w, h) * 0.1).toInt() + step
        val top = (h - wh) / 2 - (minOf(w, h) * 0.08).toInt() + step
        return Rect(left, top, left + ww, top + wh)
    }
}

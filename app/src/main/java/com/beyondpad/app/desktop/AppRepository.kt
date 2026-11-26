package com.beyondpad.app.desktop

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.beyondpad.app.system.SystemAccess

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    val key: String get() = component.flattenToString()
    val packageName: String get() = component.packageName
}

/** Launchable apps for every profile on the device, via the public LauncherApps API. */
object AppRepository {
    @Volatile private var cache: List<AppEntry>? = null

    fun load(ctx: Context, force: Boolean = false): List<AppEntry> {
        if (!force) cache?.let { return it }
        val la = ctx.getSystemService(LauncherApps::class.java)
        val self = ctx.packageName
        val list = la.profiles.flatMap { user ->
            la.getActivityList(null, user).mapNotNull { info ->
                if (info.componentName.packageName == self) return@mapNotNull null
                AppEntry(
                    label = info.label?.toString() ?: info.componentName.packageName,
                    component = info.componentName,
                    user = user,
                    icon = info.getBadgedIcon(0).toBitmap(96).asImageBitmap(),
                )
            }
        }.sortedBy { it.label.lowercase() }
        cache = list
        return list
    }

    /**
     * Packages that are in the foreground right now, system-wide (any display), from usage events.
     * Needs usage access; returns null when not granted. Android does not tell normal apps which
     * display an app is on, so this is shown as "active", not "on the monitor".
     */
    fun foregroundPackages(ctx: Context): Set<String>? {
        if (!SystemAccess.hasUsageAccess(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 6 * 60 * 60 * 1000L, end)
        val state = HashMap<String, Int>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> state[e.packageName] = (state[e.packageName] ?: 0) + 1
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    state[e.packageName] = ((state[e.packageName] ?: 0) - 1).coerceAtLeast(0)
            }
        }
        return state.filterValues { it > 0 }.keys
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(c)
        return bmp
    }
}

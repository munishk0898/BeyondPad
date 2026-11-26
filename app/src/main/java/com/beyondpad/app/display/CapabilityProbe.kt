package com.beyondpad.app.display

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.beyondpad.app.desktop.LaunchProbeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LaunchTest { NOT_RUN, RUNNING, FULLSCREEN, WINDOWED, LANDED_ELSEWHERE, REFUSED }

/** What kind of second-screen experience is really available, decided from measurements, not assumptions. */
enum class DesktopVerdict(val title: String, val detail: String) {
    NO_DISPLAY(
        "No external display visible to apps",
        "Nothing is plugged in, or OxygenOS mirrors the tablet at system level without exposing the monitor to apps. " +
            "If the monitor shows a mirror of the tablet while this says nothing is connected, no ordinary app can address that screen.",
    ),
    SYSTEM_DESKTOP(
        "System desktop windowing is active",
        "Apps opened on the monitor get resizable windows from the operating system. BeyondPad acts as the launcher and monitor manager; the windowing itself is provided by OxygenOS/Android.",
    ),
    APP_DESKTOP(
        "Independent display, full-screen apps",
        "The monitor is a separate display and apps can be opened on it independently of the tablet, but the system does not give them free-form windows. " +
            "One app is shown full screen at a time; switch apps from the BeyondPad taskbar.",
    ),
    PRESENTATION_ONLY(
        "Presentation output only",
        "The monitor is visible to apps, but this ROM refuses to open other apps on it. Only app-specific content (Android Presentation API) can be shown. This is not an extended desktop.",
    ),
    UNKNOWN(
        "Not tested yet",
        "Connect a monitor and run the capability test.",
    ),
}

data class Capabilities(
    val androidRelease: String = Build.VERSION.RELEASE,
    val sdk: Int = Build.VERSION.SDK_INT,
    val build: String = Build.DISPLAY,
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
    val freeformFeature: Boolean = false,
    val pcFeature: Boolean = false,
    val secondaryHomeApps: List<String> = emptyList(),
    val externalDisplayCount: Int = 0,
    val activityStartAllowed: Boolean? = null,
    val launchTest: LaunchTest = LaunchTest.NOT_RUN,
    val launchTestDetail: String = "",
    val verdict: DesktopVerdict = DesktopVerdict.UNKNOWN,
)

object CapabilityProbe {
    private val handler = Handler(Looper.getMainLooper())
    private val _caps = MutableStateFlow(Capabilities())
    val capabilities: StateFlow<Capabilities> = _caps.asStateFlow()

    private var pendingDisplay = -1

    /** Cheap checks that need no monitor. */
    fun refreshStatic(ctx: Context) {
        val pm = ctx.packageManager
        val homes = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).map { "${it.activityInfo.packageName}/${it.activityInfo.name.substringAfterLast('.')}" }

        val ext = DisplayRepository.externalDisplays.value
        val allowed = ext.firstOrNull()?.let { d ->
            val probe = Intent(ctx, LaunchProbeActivity::class.java)
            ctx.getSystemService(ActivityManager::class.java).isActivityStartAllowedOnDisplay(ctx, d.displayId, probe)
        }
        _caps.value = _caps.value.copy(
            freeformFeature = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
            pcFeature = pm.hasSystemFeature(PackageManager.FEATURE_PC),
            secondaryHomeApps = homes,
            externalDisplayCount = ext.size,
            activityStartAllowed = allowed,
        ).withVerdict()
    }

    /**
     * Opens a tiny invisible activity on the external display and measures where it actually
     * landed and how big its window is. Windowed = the OS applied desktop/freeform windowing.
     */
    fun runLaunchTest(ctx: Context) {
        refreshStatic(ctx)
        val d = DisplayRepository.primaryExternal()
        if (d == null) {
            _caps.value = _caps.value.copy(launchTest = LaunchTest.NOT_RUN, launchTestDetail = "No external display.").withVerdict()
            return
        }
        pendingDisplay = d.displayId
        _caps.value = _caps.value.copy(launchTest = LaunchTest.RUNNING, launchTestDetail = "Opening a test window on display ${d.displayId}…")
        try {
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(d.displayId)
            val i = Intent(ctx, LaunchProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(LaunchProbeActivity.EXTRA_TARGET, d.displayId)
            ctx.startActivity(i, opts.toBundle())
        } catch (e: SecurityException) {
            report(LaunchTest.REFUSED, "System refused: ${e.message}")
            return
        } catch (e: Exception) {
            report(LaunchTest.REFUSED, "Launch failed: ${e.javaClass.simpleName}: ${e.message}")
            return
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (_caps.value.launchTest == LaunchTest.RUNNING) {
                report(LaunchTest.REFUSED, "The test window never appeared within 4 s.")
            }
        }, 4000)
    }

    /** Called by LaunchProbeActivity once it is on screen. */
    fun onProbeResumed(landedDisplayId: Int, windowW: Int, windowH: Int, displayW: Int, displayH: Int, multiWindow: Boolean) {
        if (pendingDisplay < 0) return
        val detail = "Window ${windowW}×${windowH} on display $landedDisplayId (display ${displayW}×${displayH}, multi-window=$multiWindow)"
        val result = when {
            landedDisplayId != pendingDisplay -> LaunchTest.LANDED_ELSEWHERE
            windowW < displayW * 0.95 || windowH < displayH * 0.95 -> LaunchTest.WINDOWED
            else -> LaunchTest.FULLSCREEN
        }
        report(result, detail)
    }

    /** The desktop itself can also report windowing state after it opens. */
    fun noteDesktopWindow(windowed: Boolean, detail: String) {
        if (_caps.value.launchTest == LaunchTest.RUNNING) return
        report(if (windowed) LaunchTest.WINDOWED else LaunchTest.FULLSCREEN, "Desktop: $detail")
    }

    private fun report(r: LaunchTest, detail: String) {
        pendingDisplay = -1
        handler.removeCallbacksAndMessages(null)
        _caps.value = _caps.value.copy(launchTest = r, launchTestDetail = detail).withVerdict()
        DisplayRepository.log("Capability test: $r. $detail")
    }

    fun clearForDisconnect() {
        _caps.value = _caps.value.copy(
            externalDisplayCount = 0, activityStartAllowed = null,
            launchTest = LaunchTest.NOT_RUN, launchTestDetail = "",
        ).withVerdict()
    }

    private fun Capabilities.withVerdict(): Capabilities {
        val v = when {
            externalDisplayCount == 0 -> DesktopVerdict.NO_DISPLAY
            launchTest == LaunchTest.WINDOWED -> DesktopVerdict.SYSTEM_DESKTOP
            launchTest == LaunchTest.FULLSCREEN -> DesktopVerdict.APP_DESKTOP
            launchTest == LaunchTest.REFUSED || launchTest == LaunchTest.LANDED_ELSEWHERE -> DesktopVerdict.PRESENTATION_ONLY
            activityStartAllowed == false -> DesktopVerdict.PRESENTATION_ONLY
            else -> DesktopVerdict.UNKNOWN
        }
        return copy(verdict = v)
    }
}

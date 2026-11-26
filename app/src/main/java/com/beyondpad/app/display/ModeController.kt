package com.beyondpad.app.display

import android.os.Handler
import android.os.Looper
import android.view.Window
import com.beyondpad.app.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModeResult { IDLE, PENDING, VERIFIED, NOT_APPLIED, NEEDS_DESKTOP }

data class ModeStatus(
    val result: ModeResult = ModeResult.IDLE,
    val requested: ModeInfo? = null,
    val actual: ModeInfo? = null,
    val message: String = "",
)

/**
 * Applies a display mode the only way a normal app can: by asking for it through
 * WindowManager.LayoutParams.preferredDisplayModeId on a window that is showing on that display
 * (our desktop window). The system decides whether to honour it, so every request is verified by
 * re-reading Display.getMode() afterwards. Nothing is reported as applied unless that check passes.
 *
 * Mode keys are "WIDTHxHEIGHT@REFRESH" rather than mode IDs, because IDs change on reconnect.
 */
object ModeController {
    private val handler = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow(ModeStatus())
    val status: StateFlow<ModeStatus> = _status.asStateFlow()

    /** Set by DesktopActivity while it is showing on the external display. */
    @Volatile var desktopWindow: Pair<Int, Window>? = null

    fun keyOf(m: ModeInfo) = "${m.width}x${m.height}@${"%.2f".format(java.util.Locale.US, m.refreshRate)}"

    /** The mode BeyondPad wants for this display under the current settings. */
    fun targetMode(info: ExternalDisplayInfo): ModeInfo? {
        val s = Prefs.settings.value
        if (!s.autoResolution) {
            info.supportedModes.firstOrNull { keyOf(it) == s.manualModeKey }?.let { return it }
        }
        return NativeModePicker.best(info.supportedModes)
    }

    /** Called by the desktop when it starts and whenever settings change. */
    fun applyToDesktop() {
        val (displayId, window) = desktopWindow ?: run {
            _status.value = ModeStatus(ModeResult.NEEDS_DESKTOP, message = "Open the desktop on the monitor to apply a mode.")
            return
        }
        DisplayRepository.refresh()
        val info = DisplayRepository.externalDisplays.value.firstOrNull { it.displayId == displayId } ?: return
        val target = targetMode(info) ?: return
        val current = info.currentMode
        if (current != null && keyOf(current) == keyOf(target)) {
            setPreferred(window, target)
            _status.value = ModeStatus(ModeResult.VERIFIED, target, current, "Display is already running ${current.label}.")
            return
        }
        setPreferred(window, target)
        _status.value = ModeStatus(ModeResult.PENDING, target, current, "Requested ${target.label}. Waiting for the system…")
        DisplayRepository.log("Requested mode ${target.label} on display $displayId")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ verify(displayId, target) }, 2500)
    }

    /** Ask for the mode by ID and, for systems that only look at refresh rate, by rate too. */
    private fun setPreferred(window: Window, mode: ModeInfo) {
        val lp = window.attributes
        if (lp.preferredDisplayModeId != mode.modeId || lp.preferredRefreshRate != mode.refreshRate) {
            lp.preferredDisplayModeId = mode.modeId
            lp.preferredRefreshRate = mode.refreshRate
            window.attributes = lp
        }
    }

    private var retried = false

    private fun verify(displayId: Int, target: ModeInfo) {
        DisplayRepository.refresh()
        val actual = DisplayRepository.externalDisplays.value.firstOrNull { it.displayId == displayId }?.currentMode
        if ((actual == null || keyOf(actual) != keyOf(target)) && !retried) {
            // Some systems apply the request only after the window is re-laid out: ask once more.
            retried = true
            desktopWindow?.second?.let { w ->
                val lp = w.attributes
                lp.preferredDisplayModeId = 0; w.attributes = lp
                setPreferred(w, target)
            }
            handler.postDelayed({ verify(displayId, target) }, 2500)
            return
        }
        retried = false
        _status.value = if (actual != null && keyOf(actual) == keyOf(target)) {
            DisplayRepository.log("Verified: display $displayId now runs ${actual.label}")
            ModeStatus(ModeResult.VERIFIED, target, actual, "Verified: monitor is running ${actual.label}.")
        } else {
            DisplayRepository.log("Not applied: asked for ${target.label}, display reports ${actual?.label ?: "unknown"}")
            ModeStatus(
                ModeResult.NOT_APPLIED, target, actual,
                "The system did not switch modes. It still reports ${actual?.label ?: "an unknown mode"}. " +
                    "OxygenOS may lock the external output mode; try the system display settings.",
            )
        }
    }
}

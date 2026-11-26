package com.beyondpad.app.desktop

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.beyondpad.app.display.CapabilityProbe

/** Invisible-ish test window: reports where it landed and how big it is, then closes itself. */
class LaunchProbeActivity : Activity() {
    companion object { const val EXTRA_TARGET = "target_display" }

    private var reported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (reported) return
        // Let the window manager settle the final bounds before measuring.
        Handler(Looper.getMainLooper()).postDelayed({ measureAndFinish() }, 400)
    }

    private fun measureAndFinish() {
        if (reported || isFinishing) return
        reported = true
        val wm = windowManager
        val cur = wm.currentWindowMetrics.bounds
        val max = wm.maximumWindowMetrics.bounds
        CapabilityProbe.onProbeResumed(
            landedDisplayId = display?.displayId ?: -1,
            windowW = cur.width(), windowH = cur.height(),
            displayW = max.width(), displayH = max.height(),
            multiWindow = isInMultiWindowMode,
        )
        finishAndRemoveTask()
    }
}

package com.beyondpad.app.desktop

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.beyondpad.app.display.DisplayRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fallback for ROMs that refuse to open apps on the external display. A Presentation can only show
 * BeyondPad's own content (here: a clock and live monitor information); it is NOT an extended desktop,
 * and the dashboard says so. It also carries the requested display mode, so resolution selection can
 * still be tried in this mode.
 */
class StatusPresentation(ctx: Context, display: Display, private val preferredModeId: Int?) : Presentation(ctx, display) {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferredModeId?.let { id ->
            window?.let { w -> w.attributes = w.attributes.also { it.preferredDisplayModeId = id } }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A1020"))
        }
        clock = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 96f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val badge = TextView(context).apply {
            setTextColor(Color.parseColor("#3D8BFF")); textSize = 18f
            text = "BeyondPad · Presentation output (app-specific content only, not a desktop)"
        }
        details = TextView(context).apply {
            setTextColor(Color.parseColor("#8D99B3")); textSize = 18f; gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        root.addView(clock); root.addView(badge); root.addView(details)
        setContentView(root)
    }

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val info = DisplayRepository.externalDisplays.value.firstOrNull { it.displayId == display.displayId }
            details.text = listOfNotNull(
                info?.name,
                info?.currentMode?.label,
                info?.densityLabel,
                info?.hdrTypes?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "HDR: "),
            ).joinToString("\n")
            handler.postDelayed(this, 5000)
        }
    }

    override fun onStart() { super.onStart(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }
}

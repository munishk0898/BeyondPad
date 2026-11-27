package com.beyondpad.app.touch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.beyondpad.app.display.DisplayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Touchpad mode. Android lets an accessibility service perform taps and swipes on any display
 * (GestureDescription.setDisplayId) and draw an overlay there, which is how the tablet's screen can
 * drive a pointer on the monitor without a mouse. It reads nothing and reacts to no events except
 * when the user is on BeyondPad's touchpad screen.
 */
class TouchpadService : AccessibilityService() {

    companion object {
        @Volatile var instance: TouchpadService? = null
            private set
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        fun isEnabled(ctx: Context): Boolean {
            val want = ComponentName(ctx, TouchpadService::class.java).flattenToString()
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(want, ignoreCase = true) }
        }
    }

    private var displayId = -1
    private var wm: WindowManager? = null
    private var cursor: CursorView? = null
    private var params: WindowManager.LayoutParams? = null
    private var w = 0
    private var h = 0
    var x = 0f; private set
    var y = 0f; private set

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        detach()
        instance = null
        _connected.value = false
        super.onDestroy()
    }

    /** Shows the pointer in the middle of [id]. Returns false if the display is gone. */
    fun attach(id: Int): Boolean {
        if (id == displayId && cursor != null) return true
        detach()
        val display = getSystemService(DisplayManager::class.java).getDisplay(id) ?: return false
        return try {
            val ctx = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            val manager = ctx.getSystemService(WindowManager::class.java)
            val b = manager.maximumWindowMetrics.bounds
            w = b.width(); h = b.height()
            x = w / 2f; y = h / 2f
            val size = (36 * ctx.resources.displayMetrics.density).toInt()
            val lp = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x.toInt(); this.y = y.toInt() }
            val v = CursorView(ctx)
            manager.addView(v, lp)
            wm = manager; cursor = v; params = lp; displayId = id
            true
        } catch (e: Exception) {
            DisplayRepository.log("Touchpad could not draw a pointer on display $id: ${e.message}")
            false
        }
    }

    fun detach() {
        runCatching { cursor?.let { wm?.removeView(it) } }
        cursor = null; wm = null; params = null; displayId = -1
    }

    fun move(dx: Float, dy: Float) {
        val lp = params ?: return
        x = (x + dx).coerceIn(0f, (w - 1).toFloat())
        y = (y + dy).coerceIn(0f, (h - 1).toFloat())
        lp.x = x.toInt(); lp.y = y.toInt()
        runCatching { wm?.updateViewLayout(cursor, lp) }
    }

    fun tap() = stroke(Path().apply { moveTo(x, y) }, 40)
    fun longPress() = stroke(Path().apply { moveTo(x, y) }, 650)

    /** Natural scrolling: content follows the fingers. */
    fun scroll(dx: Float, dy: Float) {
        val tx = (x + dx).coerceIn(1f, (w - 2).toFloat())
        val ty = (y + dy).coerceIn(1f, (h - 2).toFloat())
        stroke(Path().apply { moveTo(x, y); lineTo(tx, ty) }, 220)
    }

    private fun stroke(path: Path, duration: Long) {
        if (displayId < 0) return
        cursor?.flash()
        val g = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(g, null, null)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Types into the focused text field on the monitor. Returns false when there is none. */
    fun type(text: String, enter: Boolean): Boolean {
        val node = focusedInput() ?: return false
        val current = node.text?.toString().orEmpty().let { if (node.isShowingHintText) "" else it }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + text) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (enter) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return ok
    }

    fun deleteLast(): Boolean {
        val node = focusedInput() ?: return false
        val current = node.text?.toString().orEmpty()
        if (current.isEmpty() || node.isShowingHintText) return true
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1)) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun focusedInput(): AccessibilityNodeInfo? {
        val windows = windowsOnAllDisplays.get(displayId) ?: return null
        for (win in windows) {
            val f = win.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (f != null && f.isEditable) return f
        }
        return null
    }

    /** A clear arrow pointer with a dark outline, readable on any background. */
    private class CursorView(ctx: Context) : View(ctx) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 20, 22, 26); style = Paint.Style.STROKE; strokeWidth = 3f; strokeJoin = Paint.Join.ROUND }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 76, 141, 255); style = Paint.Style.FILL }
        private var flashUntil = 0L

        fun flash() { flashUntil = System.currentTimeMillis() + 180; invalidate(); postDelayed({ invalidate() }, 200) }

        override fun onDraw(c: Canvas) {
            val s = width / 36f
            if (System.currentTimeMillis() < flashUntil) c.drawCircle(4 * s, 4 * s, 12 * s, ring)
            val p = Path().apply {
                moveTo(2 * s, 2 * s); lineTo(2 * s, 26 * s); lineTo(8.5f * s, 20 * s)
                lineTo(13 * s, 30 * s); lineTo(17 * s, 28.5f * s); lineTo(12.5f * s, 18.5f * s)
                lineTo(21 * s, 18.5f * s); close()
            }
            c.drawPath(p, fill); c.drawPath(p, edge)
        }
    }
}

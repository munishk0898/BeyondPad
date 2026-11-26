package com.beyondpad.app.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbItem(val name: String, val ids: String, val isHub: Boolean)

data class HiddenDisplay(val id: Int, val name: String, val reason: String)

/**
 * Independent evidence that a monitor is physically attached, even when OxygenOS does not hand the
 * display to apps. A DisplayPort/HDMI sink with audio registers an HDMI audio output with Android;
 * the USB hub itself shows up as a USB device. Neither needs special permissions.
 */
data class Signals(
    val hdmiAudio: Boolean = false,
    val hdmiAudioName: String? = null,
    val usb: List<UsbItem> = emptyList(),
    val hidden: List<HiddenDisplay> = emptyList(),
)

/** What the tablet can tell about the cable, from strongest to weakest evidence. */
enum class LinkState {
    /** A separate display is available to apps. */
    READY,
    /** The monitor is attached (HDMI audio sink or a disabled/hidden display exists) but apps can't use it. */
    HIDDEN_BY_SYSTEM,
    /** A USB hub/adapter is attached but no video sink is seen. */
    ADAPTER_NO_VIDEO,
    /** Nothing attached. */
    NOTHING,
}

object ConnectionSignals {
    private const val CATEGORY_ALL_INCLUDING_DISABLED = "android.hardware.display.category.ALL_INCLUDING_DISABLED"

    private lateinit var ctx: Context
    private var started = false
    private val handler = Handler(Looper.getMainLooper())
    private val _signals = MutableStateFlow(Signals())
    val signals: StateFlow<Signals> = _signals.asStateFlow()

    private val audioCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Let the system settle (displays and audio routes appear a moment after the cable).
            handler.postDelayed({ refresh(); DisplayRepository.refresh() }, 600)
        }
    }

    fun init(context: Context) {
        if (started) return
        ctx = context.applicationContext
        started = true
        ctx.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(audioCb, handler)
        val f = IntentFilter().apply {
            addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun refresh() {
        if (!started) return
        val before = _signals.value
        val am = ctx.getSystemService(AudioManager::class.java)
        val hdmi = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_HDMI_EARC)
        }
        val usb = runCatching {
            ctx.getSystemService(UsbManager::class.java)?.deviceList?.values?.map { it.toItem() }
        }.getOrNull().orEmpty()
        val now = Signals(hdmi != null, hdmi?.productName?.toString()?.takeIf { it.isNotBlank() }, usb, findHidden())
        _signals.value = now
        if (now.hdmiAudio != before.hdmiAudio) {
            DisplayRepository.log(if (now.hdmiAudio) "Monitor signal: HDMI/DP audio sink appeared (${now.hdmiAudioName ?: "unnamed"})" else "Monitor signal: HDMI/DP audio sink gone")
        }
        if (now.usb.size != before.usb.size) {
            DisplayRepository.log("USB devices on the port: ${if (now.usb.isEmpty()) "none" else now.usb.joinToString { it.name }}")
        }
    }

    fun linkState(visibleExternal: Int, s: Signals = _signals.value): LinkState = when {
        visibleExternal > 0 -> LinkState.READY
        s.hdmiAudio || s.hidden.isNotEmpty() -> LinkState.HIDDEN_BY_SYSTEM
        s.usb.isNotEmpty() -> LinkState.ADAPTER_NO_VIDEO
        else -> LinkState.NOTHING
    }

    /**
     * Looks for displays the normal list leaves out: private ones, and ones the system keeps
     * disabled (Android 15+ keeps a new monitor disabled until the user picks mirror/extend).
     * The disabled-display category is an undocumented value of the public getDisplays(String)
     * call; it is used read-only here, for diagnosis, and silently skipped if refused.
     */
    private fun findHidden(): List<HiddenDisplay> {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val visible = dm.displays.map { it.displayId }.toSet()
        val out = linkedMapOf<Int, HiddenDisplay>()
        runCatching { dm.getDisplays(CATEGORY_ALL_INCLUDING_DISABLED) }.getOrNull()?.forEach { d ->
            if (d.displayId !in visible) out[d.displayId] = HiddenDisplay(d.displayId, d.name, "disabled by the system")
        }
        dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE != 0 }.forEach { d ->
            out[d.displayId] = HiddenDisplay(d.displayId, d.name, "private (owned by another app)")
        }
        for (id in 1..63) {
            if (id in visible || id in out) continue
            dm.getDisplay(id)?.let { d -> out[id] = HiddenDisplay(id, d.name, "reachable by ID but not listed") }
        }
        return out.values.toList()
    }

    private fun UsbDevice.toItem(): UsbItem {
        val hub = deviceClass == UsbConstants.USB_CLASS_HUB
        val label = listOfNotNull(manufacturerName, productName).joinToString(" ").ifBlank { if (hub) "USB hub" else "USB device" }
        return UsbItem(label, "%04x:%04x".format(vendorId, productId), hub)
    }
}

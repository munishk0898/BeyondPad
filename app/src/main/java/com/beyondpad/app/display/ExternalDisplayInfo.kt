package com.beyondpad.app.display

import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi

/** One supported mode of a display, copied out of [Display.Mode] so it is safe to keep in UI state. */
data class ModeInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val hdrTypes: List<String>,
) {
    val pixels: Long get() = width.toLong() * height.toLong()
    val label: String get() = "${width} × ${height} @ ${"%.2f".format(refreshRate).trimEnd('0').trimEnd('.')} Hz"
}

/** Snapshot of everything the platform exposes to a normal app about an external display. */
data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val manufacturer: String?,
    val productName: String?,
    val modelYear: Int?,
    val currentMode: ModeInfo?,
    val nativeMode: ModeInfo?,
    val supportedModes: List<ModeInfo>,
    val densityDpi: Int,
    val xdpi: Float,
    val ydpi: Float,
    val rotation: Int,
    val hdrTypes: List<String>,
    val isHdr: Boolean,
    val isWideColorGamut: Boolean,
    val isPresentation: Boolean,
    val isSecure: Boolean,
    val isPrivate: Boolean,
    val state: Int,
) {
    val orientationLabel: String
        get() = when (rotation) {
            Surface.ROTATION_0 -> "0° (natural)"
            Surface.ROTATION_90 -> "90°"
            Surface.ROTATION_180 -> "180°"
            Surface.ROTATION_270 -> "270°"
            else -> "Unknown"
        }

    val stateLabel: String
        get() = when (state) {
            Display.STATE_ON -> "On"
            Display.STATE_OFF -> "Off"
            Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "Doze"
            Display.STATE_ON_SUSPEND -> "On (suspended)"
            else -> "Unknown"
        }

    val densityLabel: String
        get() {
            val bucket = when {
                densityDpi >= DisplayMetrics.DENSITY_XXXHIGH -> "xxxhdpi"
                densityDpi >= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
                densityDpi >= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
                densityDpi >= DisplayMetrics.DENSITY_HIGH -> "hdpi"
                densityDpi >= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
                else -> "ldpi"
            }
            return "$densityDpi dpi ($bucket)"
        }

    companion object {
        fun hdrName(type: Int): String = when (type) {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            else -> "Type $type"
        }

        fun from(display: Display): ExternalDisplayInfo {
            val modes = display.supportedModes.map { it.toInfo() }
                .sortedWith(compareByDescending<ModeInfo> { it.pixels }.thenByDescending { it.refreshRate })
            val current = display.mode.toInfo()
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)

            @Suppress("DEPRECATION")
            val hdrTypes = display.hdrCapabilities?.supportedHdrTypes?.map { hdrName(it) } ?: emptyList()

            val product = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) productOf(display) else null
            val flags = display.flags
            return ExternalDisplayInfo(
                displayId = display.displayId,
                name = display.name ?: "Display ${display.displayId}",
                manufacturer = product?.manufacturer,
                productName = product?.name,
                modelYear = product?.year,
                currentMode = current,
                nativeMode = NativeModePicker.native(modes),
                supportedModes = modes,
                densityDpi = metrics.densityDpi,
                xdpi = metrics.xdpi,
                ydpi = metrics.ydpi,
                rotation = display.rotation,
                hdrTypes = hdrTypes,
                isHdr = display.isHdr,
                isWideColorGamut = display.isWideColorGamut,
                isPresentation = flags and Display.FLAG_PRESENTATION != 0,
                isSecure = flags and Display.FLAG_SECURE != 0,
                isPrivate = flags and Display.FLAG_PRIVATE != 0,
                state = display.state,
            )
        }

        private data class Product(val manufacturer: String?, val name: String?, val year: Int?)

        @RequiresApi(Build.VERSION_CODES.S)
        private fun productOf(display: Display): Product? {
            val p = display.deviceProductInfo ?: return null
            val year = p.modelYear.takeIf { it > 0 } ?: p.manufactureYear.takeIf { it > 0 }
            return Product(p.manufacturerPnpId, p.name, year)
        }

        private fun Display.Mode.toInfo(): ModeInfo {
            val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                supportedHdrTypes.map { hdrName(it) }
            } else emptyList()
            return ModeInfo(modeId, physicalWidth, physicalHeight, refreshRate, hdr)
        }
    }
}

/**
 * Android does not expose the EDID "preferred timing" to apps, so "native" is taken as the
 * largest pixel count the display offers, which is the monitor's native panel resolution in
 * practice. "Best" adds the highest refresh rate available at that resolution.
 */
object NativeModePicker {
    fun native(modes: List<ModeInfo>): ModeInfo? {
        val maxPixels = modes.maxOfOrNull { it.pixels } ?: return null
        return modes.filter { it.pixels == maxPixels }.maxByOrNull { it.refreshRate }
    }

    fun best(modes: List<ModeInfo>): ModeInfo? = native(modes)
}

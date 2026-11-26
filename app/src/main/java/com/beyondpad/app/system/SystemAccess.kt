package com.beyondpad.app.system

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * Wraps every privileged thing BeyondPad can touch, and only through documented routes:
 *  - Settings.Global developer keys, which need WRITE_SECURE_SETTINGS. A normal app cannot get that
 *    permission by itself; the user grants it once over ADB ("pm grant"). Without it these are read-only.
 *  - Overlay and usage-access permissions, which the user grants in system Settings.
 */
object SystemAccess {
    /** AOSP "Force desktop mode" developer option (Settings > Developer options). Takes effect after reboot. */
    const val KEY_FORCE_DESKTOP = "force_desktop_mode_on_external_displays"
    /** AOSP "Enable freeform windows" developer option. */
    const val KEY_FREEFORM = "enable_freeform_support"
    /** AOSP "Force activities to be resizable" developer option. */
    const val KEY_FORCE_RESIZABLE = "force_resizable_activities"

    fun hasWriteSecureSettings(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Returns null when the key is unset or the ROM blocks apps from reading it. */
    fun readGlobal(ctx: Context, key: String): String? = try {
        Settings.Global.getString(ctx.contentResolver, key)
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun isOn(ctx: Context, key: String): Boolean? = readGlobal(ctx, key)?.let { it == "1" }

    /** Returns true only if the write was accepted and reads back as the new value. */
    fun writeGlobal(ctx: Context, key: String, on: Boolean): Boolean {
        if (!hasWriteSecureSettings(ctx)) return false
        return try {
            Settings.Global.putInt(ctx.contentResolver, key, if (on) 1 else 0)
            isOn(ctx, key) == on
        } catch (_: SecurityException) {
            false
        }
    }

    fun developerOptionsEnabled(ctx: Context): Boolean =
        readGlobal(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == "1"

    fun canDrawOverlays(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlaySettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun displaySettingsIntent() = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun castSettingsIntent() = Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun developerSettingsIntent() =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The exact command the user runs once from a computer to unlock the developer toggles. */
    fun grantCommand(ctx: Context) = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
}

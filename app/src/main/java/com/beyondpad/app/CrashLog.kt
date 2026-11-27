package com.beyondpad.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the device (app-private file, never sent anywhere) so it can be read in
 * Settings › Diagnostics and fixed without connecting a computer.
 */
object CrashLog {
    private lateinit var file: File

    fun install(ctx: Context) {
        file = File(ctx.filesDir, "last_crash.txt")
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText("BeyondPad $version · $stamp · thread ${thread.name}\n\n${sw.toString().take(12_000)}")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    fun clear() { runCatching { file.delete() } }
}

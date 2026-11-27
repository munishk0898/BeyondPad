package com.beyondpad.app.desktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Desktop wallpaper picked with the system photo picker (no storage permission). A downscaled copy
 * is kept in app storage so the original can be moved or deleted, and memory stays small.
 */
object Wallpaper {
    /** Bumped whenever the wallpaper changes, so the monitor reloads it. */
    var version by mutableIntStateOf(0)
        private set

    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun save(ctx: Context, uri: Uri): Boolean = try {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 2560) {
                val f = 2560f / longest
                dec.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        version++
        true
    } catch (_: Exception) {
        false
    }

    fun load(ctx: Context): ImageBitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        version++
    }
}

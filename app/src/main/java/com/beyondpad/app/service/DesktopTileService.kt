package com.beyondpad.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.beyondpad.app.desktop.OpenDesktopActivity
import com.beyondpad.app.display.DisplayRepository

/** Quick Settings tile: one tap shows the BeyondPad desktop on the monitor. */
class DesktopTileService : TileService() {

    override fun onStartListening() {
        DisplayRepository.refresh()
        val ext = DisplayRepository.primaryExternal()
        qsTile?.apply {
            state = if (ext != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = ext?.let { it.productName ?: it.name } ?: "No monitor"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, OpenDesktopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

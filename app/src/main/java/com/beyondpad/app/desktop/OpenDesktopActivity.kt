package com.beyondpad.app.desktop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.ui.MainActivity

/** Invisible hop used by the Quick Settings tile and notifications: opens the desktop on the monitor. */
class OpenDesktopActivity : Activity() {
    companion object { const val EXTRA_TOUCHPAD = "touchpad" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_TOUCHPAD, false)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_TOUCHPAD, true))
        } else {
            val d = DisplayRepository.primaryExternal()
            if (d == null) {
                Toast.makeText(this, "No monitor is available to apps right now.", Toast.LENGTH_SHORT).show()
            } else {
                DesktopLauncher.open(this, d.displayId)
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}

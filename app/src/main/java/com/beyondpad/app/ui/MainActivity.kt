package com.beyondpad.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.beyondpad.app.Prefs
import com.beyondpad.app.desktop.DesktopLauncher
import com.beyondpad.app.desktop.StatusPresentation
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.ConnectionSignals
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.display.ModeController
import com.beyondpad.app.service.DisplayMonitorService
import com.beyondpad.app.ui.theme.BP
import com.beyondpad.app.ui.theme.BeyondPadTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DESKTOP = "open_desktop"
        const val EXTRA_TOUCHPAD = "open_touchpad"
    }

    /** Which screen is showing: "home", "settings", "touchpad" or "help". */
    val screen = mutableStateOf("home")

    private var presentation: StatusPresentation? = null
    /** Bumped to make the dashboard re-read permission state after returning from Settings. */
    val resumeTick = mutableIntStateOf(0)

    private val onDisconnect: (Int) -> Unit = { id ->
        if (presentation?.display?.displayId == id) { presentation?.dismiss(); presentation = null; presentationOn.value = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DisplayRepository.addDisconnectListener(onDisconnect)
        if (Prefs.settings.value.monitorService) DisplayMonitorService.start(this)
        BP.apply(this, Prefs.settings.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { Prefs.settings.collect { BP.apply(this@MainActivity, it) } }
        }
        setContent { BeyondPadTheme { Dashboard(this) } }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        ConnectionSignals.refresh()
        DisplayRepository.refresh()
        CapabilityProbe.refreshStatic(this)
        resumeTick.intValue++
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        BP.apply(this, Prefs.settings.value)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TOUCHPAD, false) == true) {
            intent.removeExtra(EXTRA_TOUCHPAD)
            screen.value = "touchpad"
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_DESKTOP, false) == true) {
            intent.removeExtra(EXTRA_OPEN_DESKTOP)
            openDesktop()
        }
    }

    fun openDesktop() {
        val d = DisplayRepository.primaryExternal()
        if (d == null) { toast("The monitor isn't available yet. Check the cable and that the monitor is on."); return }
        val r = DesktopLauncher.open(this, d.displayId)
        toast(r.message)
    }

    fun togglePresentation() {
        presentation?.let { it.dismiss(); presentation = null; presentationOn.value = false; return }
        val info = DisplayRepository.primaryExternal() ?: run { toast("The monitor isn't available yet."); return }
        val display = DisplayRepository.display(info.displayId) ?: return
        try {
            presentation = StatusPresentation(this, display, ModeController.targetMode(info)?.modeId).also { it.show() }
            presentationOn.value = true
            DisplayRepository.log("Presentation output shown on display ${info.displayId}")
        } catch (e: Exception) {
            presentation = null
            toast("The system refused the presentation window: ${e.message}")
        }
    }

    val presentationOn = mutableStateOf(false)

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        DisplayRepository.removeDisconnectListener(onDisconnect)
        presentation?.dismiss()
        presentation = null
        super.onDestroy()
    }
}

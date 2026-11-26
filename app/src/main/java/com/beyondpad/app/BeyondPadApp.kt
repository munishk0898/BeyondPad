package com.beyondpad.app

import android.app.Application
import com.beyondpad.app.display.CapabilityProbe
import com.beyondpad.app.display.DisplayRepository
import com.beyondpad.app.service.DisplayMonitorService

class BeyondPadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        DisplayRepository.init(this)
        CapabilityProbe.refreshStatic(this)
        DisplayRepository.addDisconnectListener { CapabilityProbe.clearForDisconnect() }
        DisplayRepository.addConnectListener { CapabilityProbe.refreshStatic(this) }
        if (Prefs.settings.value.monitorService) DisplayMonitorService.start(this)
    }
}

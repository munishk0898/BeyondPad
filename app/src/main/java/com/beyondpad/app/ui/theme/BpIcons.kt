package com.beyondpad.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The five extra icons BeyondPad needs, so the multi-megabyte extended icon library isn't bundled. */
object BpIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

    val Apps by lazy {
        icon("apps", "M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4h-4zm-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z")
    }
    val Window by lazy {
        icon("window", "M3,5v4h2V5h4V3H5c-1.1,0 -2,0.9 -2,2zm2,10H3v4c0,1.1 0.9,2 2,2h4v-2H5v-4zm14,4h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4zm0,-16h-4v2h4v4h2V5c0,-1.1 -0.9,-2 -2,-2z")
    }
    val Fullscreen by lazy {
        icon("fullscreen", "M7,14H5v5h5v-2H7v-3zm-2,-4h2V7h3V5H5v5zm12,7h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z")
    }
    val Monitor by lazy {
        icon("monitor", "M20,3H4c-1.1,0 -2,0.9 -2,2v11c0,1.1 0.9,2 2,2h3l-1,1v2h12v-2l-1,-1h3c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zm0,13H4V5h16v11z")
    }
}

package com.beyondpad.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.beyondpad.app.ui.theme.BP

/** Two screens: home and settings. Settings slides in from the right; back slides it away. */
@Composable
fun Dashboard(activity: MainActivity) {
    var inSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = inSettings) { inSettings = false }

    Box(Modifier.fillMaxSize().background(BP.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedContent(
            targetState = inSettings,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(340)) { -it / 8 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(340)) { -it / 8 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(340)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "screens",
        ) { settings ->
            if (settings) SettingsScreen(activity) { inSettings = false }
            else HomeScreen(activity) { inSettings = true }
        }
    }
}

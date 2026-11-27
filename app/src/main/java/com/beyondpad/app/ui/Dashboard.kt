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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beyondpad.app.Prefs
import com.beyondpad.app.ui.theme.BP

/** Root: first-run walkthrough, then home with Settings, Touchpad and Help sliding in from the right. */
@Composable
fun Dashboard(activity: MainActivity) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val screen by activity.screen
    val current = if (!settings.onboarded) "onboarding" else screen
    BackHandler(enabled = current != "home" && current != "onboarding") {
        activity.screen.value = if (current == "help") "settings" else "home"
    }

    Box(Modifier.fillMaxSize().background(BP.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = depth(targetState) >= depth(initialState)
                if (forward) {
                    (slideInHorizontally(tween(340)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(340)) { -it / 8 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(340)) { -it / 8 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(340)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "screens",
        ) { s ->
            when (s) {
                "onboarding" -> Onboarding(activity)
                "settings" -> SettingsScreen(activity, onBack = { activity.screen.value = "home" }, onHelp = { activity.screen.value = "help" })
                "touchpad" -> TouchpadScreen(activity) { activity.screen.value = "home" }
                "help" -> HelpScreen { activity.screen.value = "settings" }
                else -> HomeScreen(activity, onSettings = { activity.screen.value = "settings" }, onTouchpad = { activity.screen.value = "touchpad" })
            }
        }
    }
}

private fun depth(s: String) = when (s) { "onboarding" -> 0; "home" -> 1; "help" -> 3; else -> 2 }

package com.beyondpad.app.touch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Standard edition: no Accessibility service, so the touchpad is not available. */
class TouchpadService private constructor() {
    companion object {
        val instance: TouchpadService? = null
        val connected: StateFlow<Boolean> = MutableStateFlow(false)
        fun isEnabled(ctx: Context): Boolean = false
    }

    fun attach(id: Int): Boolean = false
    fun detach() = Unit
    fun move(dx: Float, dy: Float) = Unit
    fun tap() = Unit
    fun longPress() = Unit
    fun scroll(dx: Float, dy: Float) = Unit
    fun back(): Boolean = false
    fun type(text: String, enter: Boolean): Boolean = false
    fun deleteLast(): Boolean = false
}

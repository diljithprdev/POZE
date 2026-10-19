package com.poze.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge helper: adds the system-bar insets on top of a view's existing
 * padding, so nothing collides with the status or navigation bars on devices
 * with different bar sizes. Call once, after the view has its XML padding.
 */
fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false) {
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            top = if (top) baseTop + bars.top else baseTop,
            bottom = if (bottom) baseBottom + bars.bottom else baseBottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

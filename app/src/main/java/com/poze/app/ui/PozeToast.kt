package com.poze.app.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.poze.app.R

/**
 * Lightweight in-app toast drawn with POZE styling instead of the system
 * Toast. Fades and rises in, then fades out and removes itself.
 */
object PozeToast {

    private const val TAG = "poze_toast"
    private const val VISIBLE_MS = 2000L

    fun show(activity: Activity, message: CharSequence) {
        if (activity.isFinishing) return
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        container.findViewWithTag<View>(TAG)?.let { container.removeView(it) }

        val view = LayoutInflater.from(activity)
            .inflate(R.layout.view_poze_toast, container, false)
        view.tag = TAG
        view.findViewById<TextView>(R.id.tvToastMessage).text = message
        container.addView(view)

        if (PozeMotion.enabled(activity)) {
            view.alpha = 0f
            view.translationY = 24f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(PozeMotion.BASE)
                .setInterpolator(PozeMotion.EASE_OUT)
                .start()
        }

        view.postDelayed({
            if (view.parent == null) return@postDelayed
            if (!PozeMotion.enabled(activity)) {
                container.removeView(view)
                return@postDelayed
            }
            view.animate()
                .alpha(0f)
                .translationY(14f)
                .setDuration(PozeMotion.FAST)
                .withEndAction {
                    (view.parent as? ViewGroup)?.removeView(view)
                }
                .start()
        }, VISIBLE_MS)
    }
}

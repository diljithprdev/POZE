package com.poze.app.ui

import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator

/**
 * Shared motion tokens and helpers so every transition in POZE uses the
 * same calm timing curves. All motion is skipped when the system animator
 * duration scale is 0 (reduced-motion friendly).
 */
object PozeMotion {

    const val FAST = 150L
    const val BASE = 220L
    const val SLOW = 300L

    val EASE_OUT: Interpolator = DecelerateInterpolator(1.6f)
    val EASE_IN_OUT: Interpolator = AccelerateDecelerateInterpolator()
    val OVERSHOOT: Interpolator = OvershootInterpolator(1.1f)

    fun enabled(context: Context): Boolean {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        } catch (e: Exception) {
            true
        }
    }

    /** Fade + rise entrance. Renders the final state instantly when motion is off. */
    fun reveal(
        view: View,
        context: Context,
        delay: Long = 0L,
        offsetY: Float = 12f,
        duration: Long = BASE
    ) {
        if (!enabled(context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = offsetY
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .start()
    }

    /** Fade only — for elements that should not move. */
    fun fadeIn(view: View, context: Context, delay: Long = 0L, duration: Long = BASE) {
        if (!enabled(context)) {
            view.alpha = 1f
            return
        }
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setStartDelay(delay).setDuration(duration).start()
    }

    /** Springy pressed feedback: dips toward [scale] then settles back. */
    fun bump(view: View, context: Context, scale: Float = 0.9f, downDuration: Long = 90L) {
        if (!enabled(context)) return
        view.animate().cancel()
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(downDuration)
            .setInterpolator(EASE_OUT)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(170)
                    .setInterpolator(OVERSHOOT)
                    .start()
            }
            .start()
    }

    /**
     * Attaches pressed-state scaling without consuming touch, so ripple and
     * click handling keep working. Motion is a no-op when disabled.
     */
    fun attachPress(view: View, context: Context, scale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (enabled(context)) {
                        v.animate().cancel()
                        v.animate()
                            .scaleX(scale)
                            .scaleY(scale)
                            .setDuration(110)
                            .setInterpolator(EASE_OUT)
                            .start()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (enabled(context)) {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(190)
                            .setInterpolator(OVERSHOOT)
                            .start()
                    }
                }
            }
            false
        }
    }
}

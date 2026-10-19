package com.poze.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.poze.app.R
import kotlin.math.roundToInt

/**
 * Rounded rectangular slider used for overlay opacity.
 * Drag the thumb or tap the track; mirrors the 25/50/75/100 segments.
 * Horizontal inside a panel, vertical when it sits inline in the tool bar.
 */
class RectSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var fraction: Float = 1f
        private set

    /** Vertical sliders fill from the bottom and grow upward. */
    var isVertical: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onFractionChanged: ((Float) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.poze_toggle_off)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.poze_accent)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.poze_accent_soft)
    }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private val trackRect = RectF()
    private val fillRect = RectF()
    private val thumbRect = RectF()

    private var isDragging = false

    init {
        isFocusable = true
        isClickable = true
    }

    fun setFraction(value: Float, notify: Boolean = false) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped != fraction) {
            fraction = clamped
            invalidate()
            if (notify) onFractionChanged?.invoke(fraction)
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val track = 8f * d
        val radius = track / 2f
        val thumbGrow = if (isDragging) 1.2f else 1f
        val thumbW = 22f * d * thumbGrow
        val thumbH = 16f * d * thumbGrow

        if (isVertical) {
            val centerX = width / 2f
            trackRect.set(centerX - radius, 0f, centerX + radius, height.toFloat())
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

            val fillH = height * fraction
            if (fillH > 0f) {
                fillRect.set(centerX - radius, height - fillH, centerX + radius, height.toFloat())
                canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
            }

            val thumbY = (height * (1f - fraction) - thumbH / 2f)
                .coerceIn(0f, (height - thumbH).coerceAtLeast(0f))
            thumbRect.set(centerX - thumbW / 2f, thumbY, centerX + thumbW / 2f, thumbY + thumbH)
        } else {
            val centerY = height / 2f
            trackRect.set(0f, centerY - radius, width.toFloat(), centerY + radius)
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

            val fillW = width * fraction
            if (fillW > 0f) {
                fillRect.set(0f, centerY - radius, fillW, centerY + radius)
                canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
            }

            val thumbX = (width * fraction - thumbW / 2f)
                .coerceIn(0f, (width - thumbW).coerceAtLeast(0f))
            thumbRect.set(thumbX, centerY - thumbH / 2f, thumbX + thumbW, centerY + thumbH / 2f)
        }

        val thumbR = 5f * d
        canvas.drawRoundRect(thumbRect, thumbR, thumbR, thumbPaint)
        canvas.drawRoundRect(thumbRect, thumbR, thumbR, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!isDragging) {
                    isDragging = true
                    invalidate()
                }
                val fraction = if (isVertical) {
                    1f - event.y / height.coerceAtLeast(1)
                } else {
                    event.x / width.coerceAtLeast(1)
                }
                setFraction(fraction, notify = true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging) {
                    isDragging = false
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

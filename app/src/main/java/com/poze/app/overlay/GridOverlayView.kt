package com.poze.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 60
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 110
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }

    /** Density-independent cell size: one sixth of the view width, floored at 48dp. */
    private val gridSpacing = 48f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val columns = (width / gridSpacing).toInt().coerceAtLeast(1)
        val spacingX = width / columns
        val rows = (height / gridSpacing).toInt().coerceAtLeast(1)
        val spacingY = height / rows

        var x = spacingX
        while (x < width - 1f) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += spacingX
        }

        var y = spacingY
        while (y < height - 1f) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += spacingY
        }

        // Center crosshair: the anchor photographers align to.
        val cx = width / 2f
        val cy = height / 2f
        val arm = 14f * density
        canvas.drawLine(cx - arm, cy, cx + arm, cy, centerPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, centerPaint)
    }
}

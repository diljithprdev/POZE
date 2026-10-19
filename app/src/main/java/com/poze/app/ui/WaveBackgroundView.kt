package com.poze.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.content.ContextCompat
import com.poze.app.R
import kotlin.math.ceil

/**
 * Home-screen backdrop: "POZE" outlines tiled tight, drifting downward in a
 * seamless loop. One tile is rendered once into a soft-blurred bitmap, then
 * blitted — cheap enough to run as the window background.
 *
 * Honours reduced-motion: when animators are disabled the pattern renders as a
 * single static frame. The loop also stops when the view is hidden.
 */
class WaveBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
        style = Paint.Style.STROKE
        letterSpacing = LETTER_SPACING
        color = ContextCompat.getColor(context, R.color.poze_sand)
    }

    private var tile: Bitmap? = null
    private var periodX = 0f
    private var pitchY = 0f
    private var pixelsPerSecond = 0f
    private var offsetY = 0f

    private var running = false
    private var lastFrameNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0f
            else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos
            if (pitchY > 0f && dt > 0f) {
                offsetY = (offsetY + pixelsPerSecond * dt) % pitchY
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        buildTile()
    }

    private fun buildTile() {
        val density = resources.displayMetrics.density
        val textSize = TILE_SIZE_DP * density
        glyphPaint.textSize = textSize
        glyphPaint.strokeWidth = STROKE_WIDTH_DP * density
        glyphPaint.alpha = (BRIGHTNESS * 255f).toInt()

        val blur = BLUR_RADIUS_DP * density
        glyphPaint.maskFilter =
            if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null

        val metrics = glyphPaint.fontMetrics
        val pad = blur + glyphPaint.strokeWidth + density
        val width = ceil(glyphPaint.measureText(TEXT) + pad * 2f).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent + pad * 2f).toInt().coerceAtLeast(1)

        tile = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(TEXT, pad, pad - metrics.ascent, glyphPaint)
        }

        periodX = width + GAP_EM * textSize
        pitchY = textSize * ROW_TIGHTNESS
        pixelsPerSecond = pitchY * (BLOCK_ROWS / LOOP_SECONDS)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = tile ?: return
        if (pitchY <= 0f || periodX <= 0f) return

        // Anchor one tile column to the middle of the view so both edges are
        // cut symmetrically, whatever the screen width.
        var startX = width / 2f - bitmap.width / 2f
        while (startX > 0f) startX -= periodX

        var y = offsetY % pitchY - pitchY
        while (y < height) {
            var x = startX
            while (x < width) {
                canvas.drawBitmap(bitmap, x, y, null)
                x += periodX
            }
            y += pitchY
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    private fun start() {
        if (running || !PozeMotion.enabled(context)) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private companion object {
        const val TEXT = "POZE"
        const val TILE_SIZE_DP = 46f
        const val ROW_TIGHTNESS = 0.82f
        const val STROKE_WIDTH_DP = 1.4f
        const val BRIGHTNESS = 0.55f
        const val LETTER_SPACING = 0.08f
        const val BLUR_RADIUS_DP = 2f
        const val GAP_EM = 0.5f
        const val LOOP_SECONDS = 24f
        const val BLOCK_ROWS = 44f
    }
}

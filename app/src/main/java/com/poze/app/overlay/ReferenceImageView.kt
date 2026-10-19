package com.poze.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import android.view.Display
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View

class ReferenceImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawMatrix = Matrix()

    private var posX = 0f
    private var posY = 0f
    private var scale = 1f
    private var rotation = 0f
    private var fitScale = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var onPositionChanged: ((Float, Float, Float) -> Unit)? = null

    private var isLocked = false
    private var viewWidth = 0
    private var viewHeight = 0
    private var displayRotationDeg = 0f

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isLocked) return false
                val scaleFactor = detector.scaleFactor
                val newScale = scale * scaleFactor
                if (newScale in 0.1f..5f) {
                    scale = newScale
                    updateDrawMatrix()
                    invalidate()
                    onPositionChanged?.invoke(posX, posY, scale)
                }
                return true
            }
        })
    }

    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        posX = 0f
        posY = 0f
        scale = 1f
        fitScale = 0f
        updateDrawMatrix()
        invalidate()
    }

    fun setImagePosition(x: Float, y: Float) {
        posX = x
        posY = y
        updateDrawMatrix()
        invalidate()
    }

    fun setImageZoom(zoom: Float) {
        scale = zoom
        updateDrawMatrix()
        invalidate()
    }

    fun setLocked(locked: Boolean) {
        isLocked = locked
    }

    fun setOpacity(opacity: Float) {
        imagePaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        invalidate()
    }

    fun setImageRotation(degrees: Float) {
        rotation = degrees
        updateDrawMatrix()
        invalidate()
    }

    fun getRotationDegrees() = rotation

    fun setOnPositionChangedListener(listener: (Float, Float, Float) -> Unit) {
        onPositionChanged = listener
    }

    fun getPosition() = Pair(posX, posY)
    fun getZoom() = scale

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        canvas.save()
        // The overlay window is rotated with the display, so the reference would
        // turn with the screen. Counter-rotate around the window centre (which is
        // the device centre for a full-screen window) so the image stays glued to
        // the device: same physical spot, same physical orientation.
        if (displayRotationDeg != 0f) {
            canvas.rotate(-displayRotationDeg, width / 2f, height / 2f)
        }
        canvas.drawBitmap(bmp, drawMatrix, imagePaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) return false

        scaleGestureDetector?.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scaleGestureDetector?.isInProgress!!) {
                    activePointerId = event.getPointerId(0)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleGestureDetector?.isInProgress!!) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        posX += x - lastTouchX
                        posY += y - lastTouchY
                        lastTouchX = x
                        lastTouchY = y
                        updateDrawMatrix()
                        invalidate()
                        onPositionChanged?.invoke(posX, posY, scale)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    if (newPointerIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newPointerIndex)
                        lastTouchX = event.getX(newPointerIndex)
                        lastTouchY = event.getY(newPointerIndex)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
            }
        }

        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // posX / posY are measured from the window centre, and a full-screen
        // window centre is the same physical point in every orientation, so they
        // are deliberately left untouched: the image keeps sitting on the same
        // spot of the device while refreshDisplayRotation() cancels the turn.
        refreshDisplayRotation()
        updateDrawMatrix()
    }

    /** Re-reads the display rotation; call on every configuration change. */
    fun refreshDisplayRotation() {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = manager?.getDisplay(Display.DEFAULT_DISPLAY)
        val degrees = when (display?.rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
        if (degrees != displayRotationDeg) {
            displayRotationDeg = degrees
            invalidate()
        }
    }

    private fun updateDrawMatrix() {
        val bmp = bitmap ?: return
        if (viewWidth == 0 || viewHeight == 0) return

        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()

        // Computed once so the reference keeps its physical size when the
        // window is resized (e.g. on rotation) rather than re-fitting.
        if (fitScale == 0f) {
            fitScale = minOf(viewWidth.toFloat() / bmpWidth, viewHeight.toFloat() / bmpHeight)
        }

        drawMatrix.reset()
        drawMatrix.postScale(fitScale, fitScale)
        drawMatrix.postTranslate(
            (viewWidth - bmpWidth * fitScale) / 2f,
            (viewHeight - bmpHeight * fitScale) / 2f
        )
        drawMatrix.postRotate(rotation, viewWidth / 2f, viewHeight / 2f)
        drawMatrix.postScale(scale, scale, viewWidth / 2f, viewHeight / 2f)
        drawMatrix.postTranslate(posX, posY)
    }

    fun resetTransformation() {
        posX = 0f
        posY = 0f
        scale = 1f
        rotation = 0f
        fitScale = 0f
        updateDrawMatrix()
        invalidate()
    }
}

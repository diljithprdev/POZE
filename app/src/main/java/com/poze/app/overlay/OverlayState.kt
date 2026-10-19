package com.poze.app.overlay

import android.graphics.Bitmap
import android.graphics.Matrix

data class OverlayState(
    val imageUri: android.net.Uri? = null,
    val bitmap: Bitmap? = null,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val zoom: Float = 1f,
    val opacity: Float = 1f,
    val rotation: Float = 0f,
    val isLocked: Boolean = false,
    val isToolMode: Boolean = false,
    val isGridVisible: Boolean = false,
    val bubbleX: Float = 100f,
    val bubbleY: Float = 100f,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    fun getImageMatrix(): Matrix {
        val matrix = Matrix()
        matrix.postScale(zoom, zoom)
        matrix.postTranslate(positionX, positionY)
        return matrix
    }

    fun resetTransformation(): OverlayState {
        return copy(
            positionX = 0f,
            positionY = 0f,
            zoom = 1f
        )
    }
}

package com.poze.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowMetrics
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.ImageViewCompat
import com.poze.app.GalleryActivity
import com.poze.app.MainActivity
import com.poze.app.R
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null
    private var referenceImageView: ReferenceImageView? = null
    private var gridOverlayView: GridOverlayView? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var referenceParams: WindowManager.LayoutParams? = null
    private var gridParams: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var state = OverlayState()
    private var isMenuVisible = false
    private var barExpanded = false
    private var barAbove = false
    private var dismissingMenu: View? = null
    private var imageBitmap: Bitmap? = null

    companion object {
        private const val CHANNEL_ID = "poze_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_IMAGE_URI = "extra_image_uri"

        private const val BUBBLE_SIZE_DP = 56
        private const val TOOL_BAR_GAP_DP = 12
        private const val REVEAL_DURATION = 320L
        private const val ICON_REVEAL_DURATION = 240L
        private const val EXIT_DURATION = 150L
        private const val PANEL_DURATION = 220L
        private const val STAGGER_MS = 35
        private const val BAR_SLIDE_DP = 18
        private const val ICON_SLIDE_DP = 10

        fun start(context: Context, imageUri: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun getScreenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
            point
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val imageUriString = intent?.getStringExtra(EXTRA_IMAGE_URI)
        if (imageUriString != null) {
            val uri = android.net.Uri.parse(imageUriString)
            loadImage(uri)
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        referenceImageView?.refreshDisplayRotation()
        repositionViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViews()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POZE Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "POZE overlay service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("POZE")
                .setContentText("Overlay active")
                .setSmallIcon(R.drawable.ic_poze)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("POZE")
                .setContentText("Overlay active")
                .setSmallIcon(R.drawable.ic_poze)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun loadImage(uri: android.net.Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                imageBitmap = bitmap
                state = state.copy(
                    imageUri = uri,
                    bitmap = bitmap
                )
                showOverlay()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverlay() {
        removeAllViews()
        createReferenceImageView()
        createGridOverlayView()
        createBubbleView()
    }

    private fun createReferenceImageView() {
        val bmp = state.bitmap ?: return

        referenceImageView = ReferenceImageView(this).apply {
            setBitmap(bmp)
            setOnPositionChangedListener { x, y, zoom ->
                state = state.copy(
                    positionX = x,
                    positionY = y,
                    zoom = zoom
                )
            }
        }

        val point = getScreenSize()

        referenceParams = WindowManager.LayoutParams(
            point.x,
            point.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(referenceImageView, referenceParams)
    }

    private fun createGridOverlayView() {
        gridOverlayView = GridOverlayView(this)

        val point = getScreenSize()

        gridParams = WindowManager.LayoutParams(
            point.x,
            point.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        if (state.isGridVisible) {
            windowManager.addView(gridOverlayView, gridParams)
        }
    }

    private fun createBubbleView() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val point = getScreenSize()
        val bubbleSize = (56 * resources.displayMetrics.density).toInt()

        bubbleParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = state.bubbleX.toInt()
            y = state.bubbleY.toInt()
        }

        setupBubbleTouchListener()
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun setupBubbleTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams?.x ?: 0
                    initialY = bubbleParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        bubbleParams?.x = initialX + dx.toInt()
                        bubbleParams?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        state = state.copy(
                            bubbleX = (bubbleParams?.x ?: 0).toFloat(),
                            bubbleY = (bubbleParams?.y ?: 0).toFloat()
                        )
                        if (isMenuVisible) {
                            positionToolBar()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleMenu()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        if (menuView != null) return

        menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
        barExpanded = false

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = state.bubbleX.toInt()
            y = state.bubbleY.toInt()
        }

        setupMenuListeners()
        try {
            windowManager.addView(menuView, menuParams)
        } catch (e: Exception) {
            e.printStackTrace()
            menuView = null
            return
        }
        isMenuVisible = true
        refreshToolState()

        menuView?.findViewById<View>(R.id.tbrow)?.addOnLayoutChangeListener {
            _, _, _, _, _, _, _, _, _ ->
            syncBarBackground()
            positionToolBar()
        }

        menuView?.post {
            syncBarBackground()
            positionToolBar()
            animateToolBarReveal()
        }

        state = state.copy(isToolMode = true)
        if (state.isGridVisible) {
            showGrid()
        }
    }

    private fun hideMenu() {
        val menu = menuView ?: return
        menuView = null
        isMenuVisible = false
        barExpanded = false

        state = state.copy(isToolMode = false)
        hideGrid()

        val remove = Runnable {
            if (menu.parent != null) {
                try {
                    windowManager.removeView(menu)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (dismissingMenu === menu) {
                dismissingMenu = null
            }
        }

        val bg = menu.findViewById<View>(R.id.barBg)
        val row = menu.findViewById<LinearLayout>(R.id.tbrow)

        if (!motionEnabled() || bg == null) {
            remove.run()
            return
        }

        dismissingMenu = menu
        val travel = if (barAbove) dp(14).toFloat() else -dp(14).toFloat()

        row?.animate()
            ?.alpha(0f)
            ?.translationY(travel)
            ?.setDuration(EXIT_DURATION)
            ?.start()

        bg.animate()
            .alpha(0f)
            .translationY(travel)
            .setDuration(EXIT_DURATION)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction(remove)
            .start()
    }

    /**
     * The bar lives in the bubble's own column, directly under it, so the floating
     * button never moves and nothing else on screen is covered. It only flips above
     * the bubble when there is no room left below it.
     */
    private fun positionToolBar() {
        val menu = menuView ?: return
        val params = menuParams ?: return
        if (menu.parent == null) return

        val point = getScreenSize()
        val edge = dp(8)
        val gap = dp(TOOL_BAR_GAP_DP)
        val bubbleSize = dp(BUBBLE_SIZE_DP)

        val row = menu.findViewById<View>(R.id.tbrow)
        val barW = row?.width?.takeIf { it > 0 }
            ?: menu.width.takeIf { it > 0 }
            ?: bubbleSize
        val barH = row?.height?.takeIf { it > 0 }
            ?: menu.height.takeIf { it > 0 }
            ?: bubbleSize

        val targetX = (state.bubbleX + (bubbleSize - barW) / 2f)
            .roundToInt()
            .coerceIn(edge, (point.x - barW - edge).coerceAtLeast(edge))

        val roomBelow = point.y - edge - (state.bubbleY + bubbleSize + gap)
        val roomAbove = state.bubbleY - gap - edge
        val placeAbove = roomBelow < barH && roomAbove > roomBelow

        val targetY = if (placeAbove) {
            (state.bubbleY - gap - barH).roundToInt()
        } else {
            (state.bubbleY + bubbleSize + gap).roundToInt()
        }.coerceIn(edge, (point.y - barH - edge).coerceAtLeast(edge))

        barAbove = placeAbove

        if (params.x != targetX || params.y != targetY) {
            params.x = targetX
            params.y = targetY
            try {
                windowManager.updateViewLayout(menu, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * The pill background is a plain View, so a match_parent inside a wrap_content
     * overlay window would stretch to the whole screen. Keep it locked to the row.
     */
    private fun syncBarBackground() {
        val menu = menuView ?: return
        val bg = menu.findViewById<View>(R.id.barBg) ?: return
        val row = menu.findViewById<View>(R.id.tbrow) ?: return
        if (row.width <= 0 || row.height <= 0) return
        val lp = bg.layoutParams
        if (lp.width != row.width || lp.height != row.height) {
            bg.layoutParams = FrameLayout.LayoutParams(row.width, row.height)
        }
    }

    /**
     * Reveal: the bar slides out from behind the bubble — the pill and its column
     * travel down out of the bubble's edge while the icons arrive one by one.
     */
    private fun animateToolBarReveal() {
        val menu = menuView ?: return
        val bg = menu.findViewById<View>(R.id.barBg) ?: return
        val row = menu.findViewById<LinearLayout>(R.id.tbrow) ?: return

        val travel = if (barAbove) dp(BAR_SLIDE_DP).toFloat() else -dp(BAR_SLIDE_DP).toFloat()
        val iconTravel = if (barAbove) dp(ICON_SLIDE_DP).toFloat() else -dp(ICON_SLIDE_DP).toFloat()

        if (!motionEnabled()) {
            bg.alpha = 1f
            bg.translationY = 0f
            row.alpha = 1f
            row.translationY = 0f
            for (i in 0 until row.childCount) {
                row.getChildAt(i).apply {
                    alpha = 1f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
            return
        }

        bg.alpha = 0f
        bg.translationY = travel
        bg.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(REVEAL_DURATION)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        row.alpha = 0f
        row.translationY = travel
        row.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(REVEAL_DURATION)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            child.alpha = 0f
            child.translationY = iconTravel
            child.scaleX = 0.9f
            child.scaleY = 0.9f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((i * STAGGER_MS).toLong())
                .setDuration(ICON_REVEAL_DURATION)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    private fun setupMenuListeners() {
        menuView?.findViewById<View>(R.id.btnLock)?.setOnClickListener {
            setLocked(!state.isLocked)
        }
        menuView?.findViewById<View>(R.id.btnGrid)?.setOnClickListener {
            setGridVisible(!state.isGridVisible)
        }
        menuView?.findViewById<View>(R.id.btnRotate)?.setOnClickListener {
            rotateImage()
        }
        menuView?.findViewById<View>(R.id.btnOpacity)?.setOnClickListener {
            toggleOpacityPanel()
        }
        menuView?.findViewById<View>(R.id.btnGallery)?.setOnClickListener {
            openGallery()
        }
        menuView?.findViewById<View>(R.id.btnReset)?.setOnClickListener {
            resetImage()
        }
        menuView?.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            hideMenu()
        }
        menuView?.findViewById<View>(R.id.btnStop)?.setOnClickListener {
            removeAllViews()
            stopSelf()
        }

        val slider = menuView?.findViewById<RectSlider>(R.id.rectSlider)
        slider?.isVertical = true
        slider?.setFraction(state.opacity)
        slider?.onFractionChanged = { fraction -> setOpacity(fraction) }
    }

    /** Variant B: the opacity slider expands inline and vertically inside the bar. */
    private fun toggleOpacityPanel() {
        val menu = menuView ?: return
        barExpanded = !barExpanded
        setToolIconActive(menu.findViewById(R.id.btnOpacity), barExpanded)

        val wrap = menu.findViewById<View>(R.id.opacityWrap) ?: return
        val lp = wrap.layoutParams
        val start = lp.height.coerceAtLeast(0)

        // Measure the inline slider with an unconstrained height pass so the exact
        // expanded height is animated, whatever the font scale happens to be.
        wrap.measure(
            View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val target = if (barExpanded) wrap.measuredHeight else 0

        if (!motionEnabled()) {
            lp.height = target
            wrap.requestLayout()
            menu.post {
                syncBarBackground()
                positionToolBar()
            }
            return
        }

        ValueAnimator.ofInt(start, target).apply {
            duration = PANEL_DURATION
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                lp.height = anim.animatedValue as Int
                wrap.requestLayout()
                positionToolBar()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    syncBarBackground()
                    positionToolBar()
                }
            })
            start()
        }
    }

    private fun openGallery() {
        removeAllViews()
        stopSelf()
        try {
            startActivity(
                Intent(this, GalleryActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        state = state.copy(opacity = clamped)
        referenceImageView?.setOpacity(clamped)
        val percent = (clamped * 100).roundToInt()
        menuView?.findViewById<android.widget.TextView>(R.id.tvOpacityValue)?.text =
            getString(R.string.opacity_percent, percent)
        menuView?.findViewById<RectSlider>(R.id.rectSlider)?.apply {
            contentDescription = getString(R.string.opacity_step_description, percent)
            setFraction(clamped)
        }
    }

    private fun refreshToolState() {
        setToolIconActive(menuView?.findViewById(R.id.btnLock), state.isLocked)
        setToolIconActive(menuView?.findViewById(R.id.btnGrid), state.isGridVisible)
        setToolIconActive(menuView?.findViewById(R.id.btnOpacity), barExpanded)
    }

    private fun setToolIconActive(button: View?, active: Boolean) {
        val image = button as? ImageView ?: return
        button.setBackgroundResource(
            if (active) R.drawable.bg_tool_icon_active else R.drawable.ripple_circle
        )
        ImageViewCompat.setImageTintList(
            image,
            ColorStateList.valueOf(
                resources.getColor(
                    if (active) R.color.poze_on_accent else R.color.poze_on_surface_dim,
                    theme
                )
            )
        )
    }

    private fun motionEnabled(): Boolean {
        val scale = android.provider.Settings.Global.getFloat(
            contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale > 0f
    }

    private fun setLocked(locked: Boolean) {
        if (locked == state.isLocked) {
            refreshToolState()
            return
        }
        state = state.copy(isLocked = locked)
        referenceImageView?.setLocked(locked)

        if (locked) {
            referenceParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            referenceParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        referenceImageView?.let {
            windowManager.updateViewLayout(it, referenceParams)
        }
        refreshToolState()
    }

    private fun resetImage() {
        referenceImageView?.resetTransformation()
        state = state.copy(
            positionX = 0f,
            positionY = 0f,
            zoom = 1f,
            rotation = 0f
        )
    }

    private fun rotateImage() {
        val newRotation = (state.rotation + 90f) % 360f
        state = state.copy(rotation = newRotation)
        referenceImageView?.setImageRotation(newRotation)
    }

    private fun setGridVisible(visible: Boolean) {
        if (visible == state.isGridVisible) {
            refreshToolState()
            return
        }
        state = state.copy(isGridVisible = visible)

        if (visible) {
            showGrid()
        } else {
            hideGrid()
        }
        refreshToolState()
    }

    private fun showGrid() {
        if (gridOverlayView?.parent == null) {
            windowManager.addView(gridOverlayView, gridParams)
        }
    }

    private fun hideGrid() {
        gridOverlayView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
        }
    }

    private fun updateScreenDimensions() {
        val point = getScreenSize()
        state = state.copy(
            screenWidth = point.x,
            screenHeight = point.y
        )
    }

    private fun repositionViews() {
        val point = getScreenSize()

        referenceParams?.apply {
            width = point.x
            height = point.y
        }
        referenceImageView?.let {
            if (it.parent != null) {
                windowManager.updateViewLayout(it, referenceParams)
            }
        }

        gridParams?.apply {
            width = point.x
            height = point.y
        }
        gridOverlayView?.let {
            if (it.parent != null) {
                windowManager.updateViewLayout(it, gridParams)
            }
        }

        // The floating button keeps its position across rotation. It is only
        // nudged back when the new bounds would leave it completely off-screen,
        // so it can never become unreachable.
        val bubbleSize = (56 * resources.displayMetrics.density).toInt()
        val maxX = point.x - bubbleSize
        val maxY = point.y - bubbleSize
        val storedX = state.bubbleX.toInt()
        val storedY = state.bubbleY.toInt()
        val bubbleX = when {
            storedX > maxX -> maxX
            storedX + bubbleSize < 0 -> 0
            else -> storedX
        }
        val bubbleY = when {
            storedY > maxY -> maxY
            storedY + bubbleSize < 0 -> 0
            else -> storedY
        }
        bubbleParams?.apply {
            if (x != bubbleX) x = bubbleX
            if (y != bubbleY) y = bubbleY
        }
        bubbleView?.let {
            if (it.parent != null && (bubbleParams?.x != storedX || bubbleParams?.y != storedY)) {
                windowManager.updateViewLayout(it, bubbleParams)
            }
        }

        if (isMenuVisible) {
            positionToolBar()
        }
    }

    private fun removeAllViews() {
        bubbleView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
        }
        bubbleView = null

        menuView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
        }
        menuView = null
        isMenuVisible = false

        dismissingMenu?.let {
            if (it.parent != null) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        dismissingMenu = null

        referenceImageView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
        }
        referenceImageView = null

        gridOverlayView?.let {
            if (it.parent != null) {
                windowManager.removeView(it)
            }
        }
        gridOverlayView = null
    }
}

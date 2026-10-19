package com.poze.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.ImageViewCompat
import com.poze.app.databinding.ActivityGalleryBinding
import com.poze.app.databinding.ItemSavedImageBinding
import com.poze.app.overlay.OverlayService
import com.poze.app.ui.PozeDialog
import com.poze.app.ui.PozeMotion
import com.poze.app.ui.PozeToast
import com.poze.app.ui.padForSystemBars
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private var deleteMode = false
    private var pendingTransientUri: Uri? = null
    private var pendingGrantedAction: (() -> Unit)? = null
    private var entrancePlayed = false

    private val savedDir: File by lazy {
        File(filesDir, "saved_images").apply { mkdirs() }
    }

    private val pickAndSaveLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) savePickedImages(uris)
    }

    private val pickTransientLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (Settings.canDrawOverlays(this)) {
                launchOverlay(it)
            } else {
                pendingTransientUri = it
                showPermissionDialog {
                    pendingTransientUri?.let { pending -> launchOverlay(pending) }
                    pendingTransientUri = null
                }
            }
        }
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            pendingGrantedAction?.invoke()
        }
        pendingGrantedAction = null
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.galContent.padForSystemBars(top = true, bottom = true)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener {
            pickAndSaveLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnTransient.setOnClickListener { pickTransientLauncher.launch("image/*") }
        binding.btnDelete.setOnClickListener { toggleDeleteMode() }

        PozeMotion.attachPress(binding.btnAdd, this)
        PozeMotion.attachPress(binding.btnTransient, this, scale = 0.98f)
        PozeMotion.attachPress(binding.btnBack, this, scale = 0.9f)
        PozeMotion.attachPress(binding.btnDelete, this, scale = 0.9f)

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.poze_zoom_in, R.anim.poze_slide_out)
    }

    private fun launchOverlay(uri: Uri) {
        OverlayService.start(this, uri.toString())
        moveTaskToBack(true)
    }

    private fun toggleDeleteMode() {
        deleteMode = !deleteMode
        applyDeleteModeUi()
        refreshGrid()
        if (!deleteMode) {
            PozeMotion.bump(binding.btnDelete, this, scale = 0.88f)
        }
    }

    private fun applyDeleteModeUi() {
        if (deleteMode) {
            binding.btnDelete.setBackgroundResource(R.drawable.bg_circle_red)
            ImageViewCompat.setImageTintList(
                binding.ivDeleteIcon,
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.poze_on_primary))
            )
            PozeToast.show(this, getString(R.string.tap_minus_to_delete))
        } else {
            binding.btnDelete.setBackgroundResource(R.drawable.bg_circle_ghost)
            ImageViewCompat.setImageTintList(
                binding.ivDeleteIcon,
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.poze_on_surface_dim))
            )
        }
    }

    private fun refresh() {
        applyDeleteModeUi()
        refreshGrid()
    }

    private fun savedFiles(): List<File> {
        return savedDir.listFiles { f ->
            f.isFile && (f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun refreshGrid() {
        val files = savedFiles()
        binding.savedGrid.removeAllViews()

        val isEmpty = files.isEmpty()
        if (binding.emptyState.visibility != View.VISIBLE && isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            PozeMotion.fadeIn(binding.emptyState, this, duration = PozeMotion.SLOW)
        } else if (!isEmpty) {
            binding.emptyState.visibility = View.GONE
        }

        binding.btnDelete.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty && deleteMode) {
            deleteMode = false
            applyDeleteModeUi()
        }

        val inflater = LayoutInflater.from(this)
        files.forEachIndexed { index, file ->
            val card = ItemSavedImageBinding.inflate(inflater, binding.savedGrid, false)
            card.tvName.text = file.nameWithoutExtension.uppercase()
            card.tvDate.text = SimpleDateFormat("d MMM", Locale.getDefault())
                .format(Date(file.lastModified()))
            card.ivThumb.setImageBitmap(decodeThumbnail(file))
            card.ivThumb.contentDescription =
                getString(R.string.gallery_thumbnail_description, card.tvName.text.toString())

            card.badgeDelete.visibility = if (deleteMode) View.VISIBLE else View.GONE
            card.badgeDelete.setOnClickListener {
                PozeMotion.bump(card.badgeDelete, this, scale = 0.86f)
                confirmDelete(file)
            }

            card.root.isClickable = !deleteMode
            if (deleteMode) {
                card.root.setOnClickListener(null)
            } else {
                card.root.setOnClickListener {
                    if (Settings.canDrawOverlays(this)) {
                        launchOverlay(Uri.fromFile(file))
                    } else {
                        showPermissionDialog { launchOverlay(Uri.fromFile(file)) }
                    }
                }
            }

            val params = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(index % 2, 1f)
            }
            binding.savedGrid.addView(card.root, params)

            if (motionOn() && (!entrancePlayed || deleteMode)) {
                PozeMotion.reveal(
                    card.root, this,
                    delay = (40L * index).coerceAtMost(240L),
                    offsetY = 12f
                )
            }
        }

        if (!isEmpty) {
            entrancePlayed = true
        }
    }

    private fun motionOn(): Boolean = PozeMotion.enabled(this)

    private fun decodeThumbnail(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 206 && bounds.outHeight / (sample * 2) >= 206) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun savePickedImages(uris: List<Uri>) {
        var saved = 0
        var skipped = 0
        uris.forEachIndexed { index, uri ->
            try {
                val stamp = System.currentTimeMillis() + index
                val outFile = File(savedDir, "IMG_$stamp.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    skipped++
                    return@forEachIndexed
                }
                saved++
            } catch (e: Exception) {
                skipped++
            }
        }
        when {
            saved == 0 -> PozeToast.show(this, getString(R.string.save_failed))
            skipped > 0 ->
                PozeToast.show(
                    this,
                    getString(R.string.save_partial, saved, uris.size)
                )
            saved == 1 -> PozeToast.show(this, getString(R.string.image_saved))
            else -> PozeToast.show(
                this,
                resources.getQuantityString(R.plurals.images_saved, saved, saved)
            )
        }
        refresh()
    }

    private fun confirmDelete(file: File) {
        PozeDialog.Builder(this)
            .title(getString(R.string.delete_confirm_title))
            .message(getString(R.string.delete_confirm_message))
            .icon(R.drawable.ic_delete)
            .destructive(true)
            .positive(getString(R.string.delete)) {
                if (file.delete()) {
                    PozeToast.show(this, getString(R.string.image_deleted))
                }
                refresh()
            }
            .negative(getString(R.string.cancel))
            .build()
            .show()
    }

    private fun showPermissionDialog(onGranted: () -> Unit) {
        PozeDialog.Builder(this)
            .title(getString(R.string.permission_dialog_title))
            .message(getString(R.string.permission_dialog_message))
            .icon(R.drawable.ic_lock)
            .positive(getString(R.string.grant_permission)) {
                pendingGrantedAction = onGranted
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlaySettingsLauncher.launch(intent)
            }
            .negative(getString(R.string.cancel))
            .build()
            .show()
    }
}

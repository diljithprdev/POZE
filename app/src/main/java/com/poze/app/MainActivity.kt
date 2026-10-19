package com.poze.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.poze.app.databinding.ActivityMainBinding
import com.poze.app.ui.PozeDialog
import com.poze.app.ui.PozeMotion
import com.poze.app.ui.PozeToast
import com.poze.app.ui.padForSystemBars

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var permissionRowVisible: Boolean? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(this)) {
            PozeToast.show(this, getString(R.string.permission_required))
        }
        updatePermissionRow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.homeContent.padForSystemBars(top = true, bottom = true)

        binding.btnSelectImage.setOnClickListener { openGallery() }
        binding.btnGrant.setOnClickListener { requestOverlayPermission() }

        PozeMotion.attachPress(binding.btnSelectImage, this)
        PozeMotion.attachPress(binding.btnGrant, this)

        updatePermissionRow()
        playEntranceMotion()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionRow()
    }

    private fun openGallery() {
        startActivity(Intent(this, GalleryActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.poze_slide_in, R.anim.poze_zoom_out)
    }

    private fun updatePermissionRow() {
        val shouldShow = !Settings.canDrawOverlays(this)
        if (shouldShow == permissionRowVisible) return
        permissionRowVisible = shouldShow

        if (!shouldShow) {
            binding.permissionRow.animate().cancel()
            binding.permissionRow.visibility = View.GONE
            binding.permissionRow.alpha = 1f
            binding.permissionRow.translationY = 0f
            return
        }

        binding.permissionRow.visibility = View.VISIBLE
        PozeMotion.reveal(binding.permissionRow, this, offsetY = 8f)
    }

    private fun playEntranceMotion() {
        PozeMotion.reveal(binding.brandBlock, this, offsetY = 16f, duration = PozeMotion.SLOW)
        PozeMotion.reveal(
            binding.btnSelectImage, this, delay = 120, offsetY = 16f, duration = PozeMotion.SLOW
        )
        PozeMotion.fadeIn(binding.tvHint, this, delay = 220, duration = PozeMotion.SLOW)
    }

    private fun requestOverlayPermission() {
        PozeDialog.Builder(this)
            .title(getString(R.string.permission_dialog_title))
            .message(getString(R.string.permission_dialog_message))
            .icon(R.drawable.ic_lock)
            .positive(getString(R.string.grant_permission)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .negative(getString(R.string.cancel))
            .build()
            .show()
    }
}

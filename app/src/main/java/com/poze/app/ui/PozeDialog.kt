package com.poze.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.poze.app.R

/**
 * Flat, Deep-Calm styled dialog that replaces the platform AlertDialog.
 * Entrance and exit are animated (scrim fade + card rise/settle).
 */
class PozeDialog private constructor(context: Context) :
    Dialog(context, R.style.Theme_POZE_Dialog) {

    class Builder(private val context: Context) {
        private var titleText: CharSequence = ""
        private var messageText: CharSequence = ""
        private var positiveText: CharSequence = ""
        private var negativeText: CharSequence = ""
        private var iconRes: Int = R.drawable.ic_lock
        private var destructive: Boolean = false
        private var cancelable: Boolean = true
        private var onPositive: (() -> Unit)? = null
        private var onNegative: (() -> Unit)? = null

        fun title(text: CharSequence) = apply { titleText = text }
        fun message(text: CharSequence) = apply { messageText = text }
        fun icon(resId: Int) = apply { iconRes = resId }
        fun destructive(value: Boolean) = apply { destructive = value }
        fun cancelable(value: Boolean) = apply { cancelable = value }
        fun positive(text: CharSequence, action: (() -> Unit)? = null) =
            apply { positiveText = text; onPositive = action }

        fun negative(text: CharSequence, action: (() -> Unit)? = null) =
            apply { negativeText = text; onNegative = action }

        fun build(): PozeDialog = PozeDialog(context).apply { bind(this@Builder) }

        internal fun titleValue() = titleText
        internal fun messageValue() = messageText
        internal fun positiveValue() = positiveText
        internal fun negativeValue() = negativeText
        internal fun iconValue() = iconRes
        internal fun isDestructive() = destructive
        internal fun isCancelable() = cancelable
        internal fun positiveAction() = onPositive
        internal fun negativeAction() = onNegative
    }

    private lateinit var scrim: View
    private lateinit var card: View
    private var dismissing = false

    private fun bind(config: Builder) {
        setContentView(R.layout.dialog_poze)

        window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        setCanceledOnTouchOutside(config.isCancelable())
        setCancelable(config.isCancelable())

        scrim = findViewById(R.id.dialogScrim)
        card = findViewById(R.id.dialogCard)
        val iconWrap = findViewById<View>(R.id.dialogIconWrap)
        val icon = findViewById<ImageView>(R.id.ivDialogIcon)
        val title = findViewById<TextView>(R.id.tvDialogTitle)
        val message = findViewById<TextView>(R.id.tvDialogMessage)
        val positive = findViewById<TextView>(R.id.btnDialogPositive)
        val negative = findViewById<TextView>(R.id.btnDialogNegative)

        title.text = config.titleValue()
        message.text = config.messageValue()
        icon.setImageResource(config.iconValue())
        positive.text = config.positiveValue()

        if (config.negativeValue().isEmpty()) {
            negative.visibility = View.GONE
        } else {
            negative.text = config.negativeValue()
            negative.visibility = View.VISIBLE
            negative.setOnClickListener {
                val action = config.negativeAction()
                dismiss()
                action?.invoke()
            }
        }

        if (config.isDestructive()) {
            positive.setBackgroundResource(R.drawable.bg_button_danger)
            positive.setTextColor(ContextCompat.getColor(context, R.color.poze_on_primary))
            iconWrap.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.poze_tint_danger))
            icon.setColorFilter(ContextCompat.getColor(context, R.color.poze_red_stop))
        } else {
            positive.setBackgroundResource(R.drawable.bg_grant_button)
            positive.setTextColor(ContextCompat.getColor(context, R.color.poze_on_accent))
            iconWrap.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.poze_tint_accent))
            icon.setColorFilter(ContextCompat.getColor(context, R.color.poze_accent_soft))
        }

        positive.setOnClickListener {
            val action = config.positiveAction()
            dismiss()
            action?.invoke()
        }
    }

    override fun show() {
        super.show()
        playEnter()
    }

    override fun dismiss() {
        if (dismissing) return
        dismissing = true
        if (!PozeMotion.enabled(context)) {
            super.dismiss()
            return
        }
        scrim.animate().alpha(0f).setDuration(PozeMotion.FAST).start()
        card.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(PozeMotion.FAST)
            .setInterpolator(PozeMotion.EASE_OUT)
            .withEndAction { finishDismiss() }
            .start()
    }

    private fun finishDismiss() {
        super.dismiss()
    }

    private fun playEnter() {
        if (!PozeMotion.enabled(context)) {
            scrim.alpha = 1f
            card.alpha = 1f
            return
        }
        scrim.animate().alpha(1f).setDuration(PozeMotion.SLOW).start()
        card.alpha = 0f
        card.scaleX = 0.94f
        card.scaleY = 0.94f
        card.translationY = 16f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(PozeMotion.SLOW)
            .setInterpolator(PozeMotion.EASE_OUT)
            .start()
    }
}

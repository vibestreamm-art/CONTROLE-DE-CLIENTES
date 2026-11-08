package br.com.controleclientes.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object Palette {
    const val background = 0xFFF5F7FB.toInt()
    const val surface = 0xFFFFFFFF.toInt()
    const val ink = 0xFF172033.toInt()
    const val muted = 0xFF718096.toInt()
    const val line = 0xFFE6EAF1.toInt()
    const val blue = 0xFF2457D6.toInt()
    const val paleBlue = 0xFFEAF0FF.toInt()
    const val green = 0xFF16825D.toInt()
    const val paleGreen = 0xFFE7F5EE.toInt()
    const val red = 0xFFBB3D45.toInt()
    const val paleRed = 0xFFFCEBEC.toInt()
    const val amber = 0xFF9A6600.toInt()
    const val paleAmber = 0xFFFFF4D8.toInt()
}

object Ui {
    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun text(
        context: Context,
        value: CharSequence,
        size: Float = 14f,
        color: Int = Palette.ink,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    fun column(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

    fun row(context: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
        }

    fun card(context: Context, padding: Int = 16): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
            background = rounded(context, Palette.surface, Palette.line, 18)
            elevation = dp(context, 1).toFloat()
        }

    fun rounded(
        context: Context,
        fill: Int,
        stroke: Int? = null,
        radius: Int = 14
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radius).toFloat()
        if (stroke != null) setStroke(dp(context, 1), stroke)
    }

    fun button(
        context: Context,
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): TextView = text(
        context,
        label,
        14f,
        if (primary) 0xFFFFFFFF.toInt() else Palette.blue,
        true
    ).apply {
        gravity = Gravity.CENTER
        setPadding(dp(context, 15), dp(context, 12), dp(context, 15), dp(context, 12))
        background = rounded(context, if (primary) Palette.blue else Palette.paleBlue, null, 12)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun addSpace(layout: LinearLayout, context: Context, height: Int = 10) {
        layout.addView(View(context), LinearLayout.LayoutParams(1, dp(context, height)))
    }

    fun horizontalSpace(layout: LinearLayout, context: Context, width: Int = 8) {
        layout.addView(View(context), LinearLayout.LayoutParams(dp(context, width), 1))
    }
}

package app.sidepeek

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15+ (targetSdk 35+) always draws apps edge to edge: make room for the status bar
 * on top, and for the navigation bar / cutouts on the sides and bottom.
 */
fun View.padForSystemBars() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    if (this is ViewGroup) clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        // The content area starts right under the action bar's height measured from the very
        // top, so it is still overlapped by the status-bar height: push it down by that much.
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * The status bar is transparent when edge to edge, and scrolled content showed through it
 * above the action bar. Cover the status bar area with the action bar's own color.
 */
fun Activity.addStatusBarScrim() {
    val decor = window.decorView as? ViewGroup ?: return
    val scrim = View(this).apply {
        isClickable = false; isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        elevation = 1000f
    }
    decor.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
    fun color(): Int {
        val bar = decor.findViewById<View>(androidx.appcompat.R.id.action_bar_container)
        (bar?.background as? ColorDrawable)?.let { return it.color }
        (bar?.background as? com.google.android.material.shape.MaterialShapeDrawable)?.fillColor?.let { return it.defaultColor }
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        val c = a.getColor(0, Color.BLACK); a.recycle(); return c
    }
    ViewCompat.setOnApplyWindowInsetsListener(scrim) { v, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
        v.layoutParams = v.layoutParams.apply { height = top }
        v.setBackgroundColor(color())
        insets
    }
    ViewCompat.requestApplyInsets(scrim)
}

package app.sidepeek

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * The real side panel, shrunk into a phone outline at the top of 側邊欄「外觀」.
 *
 * [PanelController.buildMiniature] builds the same views the panel uses (with the newest few
 * messages) for this phone's full screen size; they are laid out at full size and scaled down, so
 * width, height, position, corner radius, text size, lines per message, message spacing and the
 * dimmed background all look exactly as they will. While a size, position or corner slider is
 * dragged, only the card's size and corners change (every frame); the full rebuild (needed for
 * text, lines, spacing, dimming, and the narrow-panel layout) runs at most every [REBUILD_MS]
 * during a drag and once more when it stops, so dragging a slider updates it live and the real panel no longer has to pop up over the
 * settings page. Touches pass through to the page (the miniature is only a picture).
 *
 * Its height is [previewHeightPx] when set (the 側邊欄外觀 page sizes it to fill the screen, so the
 * whole page fits without scrolling), else [DEFAULT_HEIGHT_DP].
 */
class PanelMiniature @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    private val dp = resources.displayMetrics.density
    private fun px(v: Float) = v * dp

    private var screenW = 0
    private var screenH = 0
    private val phone = RectF()
    private val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px(1.5f) }
    private var dirty = true
    private var rebuildPending = false
    private val rebuild = Runnable { rebuildPending = false; build() }

    private val night get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** Height in px chosen by the page (0 = [DEFAULT_HEIGHT_DP]). */
    var previewHeightPx = 0
        set(v) { if (field != v) { field = v; requestLayout() } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Prefs.sp(context).registerOnSharedPreferenceChangeListener(this)
        dirty = true
        if (isShown) post(rebuild)
    }

    override fun onDetachedFromWindow() {
        Prefs.sp(context).unregisterOnSharedPreferenceChangeListener(this)
        removeCallbacks(rebuild)
        super.onDetachedFromWindow()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        dirty = true
        if (!isShown) return
        if (key in GEOMETRY_KEYS) applyGeometry()
        // Sliders fire many changes a second: rebuild at most every REBUILD_MS, without waiting
        // for the changes to stop (a trailing rebuild still catches the last value).
        if (!rebuildPending) { rebuildPending = true; postDelayed(rebuild, REBUILD_MS) }
    }

    /** Size, position and corners of the card straight from the prefs, without rebuilding. */
    private fun applyGeometry() {
        val root = getChildAt(0) as? ViewGroup ?: return
        val card = root.getChildAt(0) ?: return
        val ctx = context
        val margin = (8 * dp).toInt()
        val w = Prefs.panelWidth(ctx)
        val lp = card.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = (screenW * w / 100f).toInt().coerceAtMost(screenW - margin)
        lp.height = (screenH * Prefs.panelHeight(ctx) / 100f).toInt()
        val v = when (Prefs.panelPosition(ctx)) {
            Prefs.POS_TOP -> Gravity.TOP
            Prefs.POS_BOTTOM -> Gravity.BOTTOM
            else -> Gravity.CENTER_VERTICAL
        }
        val left = Prefs.handleSide(ctx) == Prefs.SIDE_LEFT
        lp.gravity = (if (left) Gravity.START else Gravity.END) or v
        val edge = if (w >= 100) 0 else margin
        lp.leftMargin = if (left) edge else 0
        lp.rightMargin = if (left) 0 else edge
        lp.topMargin = if (v == Gravity.TOP) (28 * dp).toInt() else 0
        lp.bottomMargin = if (v == Gravity.BOTTOM) (28 * dp).toInt() else 0
        card.layoutParams = lp
        (card.background as? GradientDrawable)?.cornerRadius = Prefs.panelRadius(ctx) * dp
        card.invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && dirty) post(rebuild)
    }

    private fun build() {
        if (!isAttachedToWindow) return
        dirty = false
        readScreen()
        val v = try {
            PanelController(context, false).buildMiniature(screenW, screenH)
        } catch (e: Exception) {
            Diagnostics.note(context, "PANEL miniature failed: $e"); null
        }
        removeAllViews()
        if (v != null) addView(v)
        requestLayout(); invalidate()
    }

    private fun readScreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            screenW = b.width(); screenH = b.height()
        } else {
            val dm = resources.displayMetrics
            screenW = dm.widthPixels; screenH = dm.heightPixels
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (screenW == 0) readScreen()
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (previewHeightPx > 0) previewHeightPx else px(DEFAULT_HEIGHT_DP).toInt()
        setMeasuredDimension(w, h)
        // The panel is laid out at full screen size, then scaled down in onLayout.
        for (i in 0 until childCount) getChildAt(i).measure(
            MeasureSpec.makeMeasureSpec(screenW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(screenH, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Fit the phone inside the view both ways (it may sit in a narrow column), centred.
        val scale = minOf((height - px(8f)) / screenH.coerceAtLeast(1), (width - px(8f)) / screenW.coerceAtLeast(1))
        val pw = screenW * scale
        val ph = screenH * scale
        val left = (width - pw) / 2f
        val top = (height - ph) / 2f
        phone.set(left, top, left + pw, top + ph)
        clip.reset()
        clip.addRoundRect(phone, px(16f), px(16f), Path.Direction.CW)
        for (i in 0 until childCount) getChildAt(i).apply {
            layout(0, 0, screenW, screenH)
            pivotX = 0f; pivotY = 0f
            scaleX = scale; scaleY = scale
            translationX = phone.left; translationY = phone.top
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Home screen behind the panel: a plain screen with a grid of app icons.
        canvas.save()
        canvas.clipPath(clip)
        // A soft wallpaper, lighter than the panel, so the panel's edges and corners show.
        paint.shader = LinearGradient(phone.left, phone.top, phone.right, phone.bottom,
            if (night) intArrayOf(0xFF4B5D72.toInt(), 0xFF3A6B5A.toInt()) else intArrayOf(0xFFB9CCE4.toInt(), 0xFFB8E0C8.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(phone, paint)
        paint.shader = null
        paint.color = if (night) 0x40FFFFFF else 0x59FFFFFF
        val icon = phone.width() / 7f
        for (row in 0 until 7) for (col in 0 until 4) {
            val x = phone.left + icon * (0.75f + col * 1.5f)
            val y = phone.top + icon * (1.6f + row * 1.6f)
            canvas.drawRoundRect(x, y, x + icon, y + icon, icon / 3, icon / 3, paint)
        }
        super.dispatchDraw(canvas)
        canvas.restore()
        stroke.color = if (night) 0x59FFFFFF else 0x40000000
        canvas.drawRoundRect(phone, px(16f), px(16f), stroke)
    }

    private companion object {
        const val REBUILD_MS = 120L
        const val DEFAULT_HEIGHT_DP = 300f
        val GEOMETRY_KEYS = setOf(Prefs.KEY_PANEL_WIDTH, Prefs.KEY_PANEL_HEIGHT, Prefs.KEY_PANEL_POS, Prefs.KEY_PANEL_RADIUS)
    }

    /** Only a picture: touches go to the settings page (scrolling still works). */
    override fun onInterceptTouchEvent(ev: MotionEvent) = true
    override fun onTouchEvent(event: MotionEvent) = false
}

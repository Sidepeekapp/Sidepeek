package app.sidepeek

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator

/**
 * 「Peek 條樣式」頁頂端的即時預覽：一支直立的手機（看得到 Peek 條在整支手機上的位置、高度、
 * 左右邊），旁邊一個圓形放大鏡，放大 Peek 條那一段螢幕邊緣，寬度、不透明度、距離邊緣、
 * 系統返回手勢的範圍和要滑過的虛線都看得清楚。改靈敏度、左右邊，或點一下圖，放大鏡裡會
 * 播手指往內滑、側邊欄滑出來的動畫。Peek 條在右邊時手機在左、放大鏡在右；在左邊時左右對調。
 *
 * It reads [Prefs] directly and redraws whenever a preference changes, so the settings code does
 * not have to call it. (The side panel page uses [PanelMiniature].)
 */
class SettingPreview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    private val dp = resources.displayMetrics.density
    private fun px(v: Float) = v * dp

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = px(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(px(4f), px(3f)), 0f)
    }
    private val r = RectF()
    private val phone = RectF()
    private val path = Path()

    /** Finger animation, 0 → 1 (0 = not playing). */
    private var swipe = 0f
    private var swipeAnim: ValueAnimator? = null

    private val night get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val green get() = androidx.core.content.ContextCompat.getColor(context, R.color.section_text)
    private val screenColor get() = if (night) 0xFF2C2C30.toInt() else 0xFFE4E4E9.toInt()
    private val inkColor get() = if (night) 0x33FFFFFF else 0x24000000
    private val frameColor get() = if (night) 0x59FFFFFF else 0x40000000
    private val handleColor get() = if (night) 0xFFFFFFFF.toInt() else 0xFF3A3A3C.toInt()

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener { playSwipe() }
    }

    /** Height in px chosen by the Peek 條樣式 page so it fits the screen (0 = 170 dp). */
    var previewHeightPx = 0
        set(v) { if (field != v) { field = v; requestLayout() } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Prefs.sp(context).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        Prefs.sp(context).unregisterOnSharedPreferenceChangeListener(this)
        swipeAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        invalidate()
        if ((key == Prefs.KEY_SWIPE_SENS || key == Prefs.KEY_HANDLE_SIDE)) playSwipe()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // Show the swipe once when the card is opened.
        if (isVisible && isLaidOut) postDelayed({ playSwipe() }, 250)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (previewHeightPx > 0) previewHeightPx else px(170f).toInt()
        setMeasuredDimension(w, h)
    }

    fun playSwipe() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        swipeAnim?.cancel()
        swipeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700
            interpolator = null
            addUpdateListener { swipe = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { swipe = 0f; invalidate() }
            })
            start()
        }
    }

    /** This phone's screen in px (the Peek 條 position is stored in screen px). */
    private fun screenPx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return b.width() to b.height()
        }
        val dm = resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    override fun onDraw(c: Canvas) {
        val ctx = context
        val left = Prefs.handleSide(ctx) == Prefs.SIDE_LEFT
        val (sw, sh) = screenPx()
        val pad = px(6f)

        // ---- the whole phone ----------------------------------------------------------------
        val ph = height - 2 * pad
        val pw = ph * sw / sh.toFloat()
        val gap = px(30f)
        // Phone and magnifier side by side, centred; the magnifier sits on the Peek 條 side.
        val lensR = minOf(ph * 0.4f, (width - 2 * pad - pw - gap) / 2f).coerceAtLeast(px(30f))
        val groupW = pw + gap + lensR * 2
        val gx = (width - groupW) / 2f
        val phoneX = if (left) gx + lensR * 2 + gap else gx
        phone.set(phoneX, pad, phoneX + pw, pad + ph)
        val corner = pw * 0.12f
        paint.color = screenColor
        c.drawRoundRect(phone, corner, corner, paint)
        c.save()
        path.reset(); path.addRoundRect(phone, corner, corner, Path.Direction.CW); c.clipPath(path)
        // Faint app icons in a 4-column grid
        paint.color = inkColor
        val cell = pw / 4f
        val icon = cell * 0.56f
        var row = 0
        while (true) {
            val y = phone.top + ph * 0.16f + row * cell * 1.05f
            if (y + icon > phone.bottom - ph * 0.08f) break
            for (col in 0 until 4) {
                val x = phone.left + col * cell + (cell - icon) / 2
                c.drawRoundRect(x, y, x + icon, y + icon, icon * 0.3f, icon * 0.3f, paint)
            }
            row++
        }
        // Peek 條 at its real proportions (thin bars keep a minimum width so they stay visible)
        val k = pw / sw                                   // phone px per screen px
        val dpk = dp * k                                   // phone px per real dp
        val hw = maxOf(Prefs.handleWidth(ctx) * dpk, px(1.5f))
        val hh = Prefs.handleHeight(ctx) * dp * k
        val inset = Prefs.handleInset(ctx) * dpk
        val savedY = Prefs.handleY(ctx)
        val topPx = if (savedY < 0) sh * 0.75f - Prefs.handleHeight(ctx) * dp / 2 else savedY.toFloat()
        val by0 = phone.top + topPx * k
        val bx0 = if (left) phone.left + inset else phone.right - inset - hw
        val alpha = Prefs.handleOpacity(ctx) / 100f
        if (alpha > 0.01f) {
            paint.color = handleColor; paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            c.drawRoundRect(bx0, by0, bx0 + hw, by0 + hh, hw / 2, hw / 2, paint)
            paint.alpha = 255
        }
        c.restore()
        stroke.color = frameColor; stroke.strokeWidth = px(1.5f)
        c.drawRoundRect(phone, corner, corner, stroke)

        // ---- the magnifier ---------------------------------------------------------------------
        val focusX = if (left) phone.left else phone.right
        val focusY = by0 + hh / 2
        val lx = if (left) gx + lensR else gx + pw + gap + lensR
        val ly = pad + ph / 2
        // A ring on the phone around the Peek 條, joined to the lens
        val ringR = px(11f)
        stroke.color = green; stroke.alpha = 150; stroke.strokeWidth = px(1.2f)
        c.drawCircle(focusX, focusY, ringR, stroke)
        val dir = if (left) -1f else 1f
        c.drawLine(focusX + dir * ringR * 0.7f, focusY - ringR * 0.7f, lx - dir * lensR * 0.7f, ly - lensR * 0.7f, stroke)
        c.drawLine(focusX + dir * ringR * 0.7f, focusY + ringR * 0.7f, lx - dir * lensR * 0.7f, ly + lensR * 0.7f, stroke)
        stroke.alpha = 255

        c.save()
        path.reset(); path.addCircle(lx, ly, lensR, Path.Direction.CW); c.clipPath(path)
        // Close-up: 0.9 px per real dp of the screen; the screen edge sits a bit past the centre.
        val z = 0.9f * dp
        val edgeX = lx + dir * lensR * 0.6f
        val inward = -dir
        // Screen
        paint.color = screenColor
        if (left) c.drawRect(edgeX, ly - lensR, lx + lensR, ly + lensR, paint)
        else c.drawRect(lx - lensR, ly - lensR, edgeX, ly + lensR, paint)
        // Bezel beyond the edge
        paint.color = if (night) 0xFF0B0B0D.toInt() else 0xFF9A9AA2.toInt()
        if (left) c.drawRect(lx - lensR, ly - lensR, edgeX, ly + lensR, paint)
        else c.drawRect(edgeX, ly - lensR, lx + lensR, ly + lensR, paint)
        // App icons near the edge
        paint.color = inkColor
        val ic = px(26f); val ig = px(16f)
        var iy = ly - lensR + px(14f)
        while (iy < ly + lensR) {
            var ix = edgeX + inward * px(20f)
            for (n in 0 until 4) {
                val x0 = if (left) ix else ix - ic
                c.drawRoundRect(x0, iy, x0 + ic, iy + ic, px(8f), px(8f), paint)
                ix += inward * (ic + ig)
            }
            iy += ic + ig
        }
        // System back-gesture strip (about 12 dp)
        paint.color = if (night) 0x1AFFFFFF else 0x14000000
        val back = 12f * z
        c.drawRect(minOf(edgeX, edgeX + inward * back), ly - lensR, maxOf(edgeX, edgeX + inward * back), ly + lensR, paint)
        // The Peek 條 close up
        val zw = Prefs.handleWidth(ctx) * z
        val zh = Prefs.handleHeight(ctx) * z
        val zi = Prefs.handleInset(ctx) * z
        val hx0 = edgeX + inward * zi
        val hx1 = hx0 + inward * zw
        r.set(minOf(hx0, hx1), ly - zh / 2, maxOf(hx0, hx1), ly + zh / 2)
        val hr = minOf(r.width(), r.height()) / 2
        if (alpha > 0.01f) {
            paint.color = handleColor; paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            c.drawRoundRect(r, hr, hr, paint); paint.alpha = 255
        } else {
            // Invisible, but still there to swipe: show where it is.
            dash.color = green; c.drawRoundRect(r, hr, hr, dash)
        }
        // How far to swipe before the side panel opens
        val open = Prefs.swipeOpenDp(Prefs.swipeSensitivity(ctx)) * z
        val markX = hx1 + inward * open
        dash.color = green
        c.drawLine(markX, ly - zh / 2 - px(12f), markX, ly + zh / 2 + px(12f), dash)
        // Swipe: a finger moves inward past the line, then the panel slides in.
        if (swipe > 0f) {
            val t = EASE.getInterpolation((swipe / 0.55f).coerceAtMost(1f))
            val fx = hx1 + inward * (open + px(14f)) * t
            val fade = if (swipe < 0.85f) 1f else (1f - swipe) / 0.15f
            if (swipe > 0.45f) {
                val p = EASE.getInterpolation(((swipe - 0.45f) / 0.25f).coerceIn(0f, 1f))
                val panelW = lensR * 1.3f * p
                paint.color = if (night) 0xF23A3A3E.toInt() else 0xF2FFFFFF.toInt()
                paint.alpha = (fade * 242).toInt()
                val top = ly - lensR * 0.62f; val bottom = ly + lensR * 0.62f
                if (left) c.drawRoundRect(edgeX - px(20f), top, edgeX + panelW, bottom, px(16f), px(16f), paint)
                else c.drawRoundRect(edgeX - panelW, top, edgeX + px(20f), bottom, px(16f), px(16f), paint)
                paint.alpha = 255
            }
            paint.color = green
            paint.alpha = (fade * 0x55).toInt(); c.drawCircle(fx, ly, px(17f), paint)
            paint.alpha = (fade * 0xCC).toInt(); c.drawCircle(fx, ly, px(9f), paint)
            paint.alpha = 255
        }
        c.restore()
        // Lens rim with a soft highlight
        stroke.color = frameColor; stroke.strokeWidth = px(2f)
        c.drawCircle(lx, ly, lensR, stroke)
        stroke.shader = LinearGradient(lx - lensR, ly - lensR, lx, ly, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        stroke.strokeWidth = px(1.2f)
        c.drawCircle(lx, ly, lensR - px(2f), stroke)
        stroke.shader = null
    }

    private companion object {
        val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}

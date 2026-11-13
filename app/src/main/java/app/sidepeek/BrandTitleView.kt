package app.sidepeek

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator

/**
 * 左上角標題列的「Sidepeek」字標：「Side」用標題列的文字色，「peek」是品牌綠漸層（跟功能介紹影片的
 * 字標一樣），測試版後面的「Test」淡一點、小一點。打開 App 時有一道光從左掃到右，之後每隔
 * 一段時間再掃一次；點一下也會掃。系統關閉動畫時不掃。
 */
class BrandTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val sp = resources.displayMetrics.scaledDensity
    private val label = context.getString(R.string.app_name)
    private val brand = "Sidepeek"
    private val suffix = label.removePrefix(brand)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    private val night get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Sweep start time (0 = none running). */
    private var sweepStart = 0L

    init {
        paint.textSize = 21f * sp
        small.textSize = 13f * sp
        contentDescription = label
        setOnClickListener { sweep() }
    }

    private fun sweep() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        sweepStart = SystemClock.uptimeMillis(); invalidate()
    }

    private val again = object : Runnable {
        override fun run() { if (isShown) sweep(); postDelayed(this, REPEAT_MS) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed({ sweep() }, 450)
        postDelayed(again, REPEAT_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(again)
        super.onDetachedFromWindow()
    }

    private fun sideW() = paint.measureText("Side")
    private fun peekW() = paint.measureText("peek")
    private fun suffixW() = if (suffix.isBlank()) 0f else small.measureText(suffix)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (sideW() + peekW() + suffixW() + 4 * dp).toInt()
        val h = (paint.fontSpacing + 4 * dp).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(c: Canvas) {
        val base = (height - paint.ascent() - paint.descent()) / 2f
        val layer = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        // 「Side」: the action bar's text colour (white on the green light-mode bar, light grey on dark).
        paint.shader = null
        paint.color = if (night) 0xFFE8E8EC.toInt() else 0xFFFFFFFF.toInt()
        c.drawText("Side", 0f, base, paint)
        // 「peek」: brand green gradient (lighter mint on the green light-mode bar so it still stands out).
        val x = sideW()
        paint.shader = LinearGradient(x, 0f, x + peekW(), 0f,
            if (night) intArrayOf(0xFF86EFAC.toInt(), 0xFF22C55E.toInt()) else intArrayOf(0xFFECFDF5.toInt(), 0xFFBBF7D0.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawText("peek", x, base, paint)
        paint.shader = null
        if (suffix.isNotBlank()) {
            small.color = if (night) 0x99E8E8EC.toInt() else 0xB3FFFFFF.toInt()
            c.drawText(suffix, x + peekW(), base, small)
        }
        // The sweep: a soft white band drawn only over the letters (SRC_ATOP).
        if (sweepStart > 0L) {
            val t = (SystemClock.uptimeMillis() - sweepStart) / SWEEP_MS.toFloat()
            if (t >= 1f) sweepStart = 0L
            else {
                val f = EASE.getInterpolation(t)
                val band = 46 * dp
                val cx = -band + (width + 2 * band) * f
                shine.shader = LinearGradient(cx - band / 2, 0f, cx + band / 2, height.toFloat(),
                    intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shine)
                postInvalidateOnAnimation()
            }
        }
        c.restoreToCount(layer)
    }

    private companion object {
        const val SWEEP_MS = 1100L
        const val REPEAT_MS = 12_000L
        val EASE = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}

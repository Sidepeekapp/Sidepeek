package app.sidepeek

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/**
 * 首頁「怎麼用」和設定精靈最後一步的三格說明圖（原本是 img_steps_overview 圖片）：
 * ① 通知整理成對話 ② 邊緣的 Peek 條 ③ 往內滑打開側邊欄。
 *
 * 用程式畫，所有文字（步驟標題、說明、示範的群組和訊息）都從 strings.xml 讀（`how_*`），所以每種
 * 語言都有自己的版本。固定深色，跟原圖一樣。畫在 360 × 213 的設計座標裡，再縮放到實際大小；
 * 標題放不下時自動縮小，說明最多三行。
 */
class StepsOverviewDrawable(private val ctx: Context) : Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val r = RectF()
    private val path = Path()
    private fun s(id: Int) = ctx.getString(id)

    private val W = 360f
    private val H = 236f

    override fun getIntrinsicWidth() = (W * 3).toInt()
    override fun getIntrinsicHeight() = (H * 3).toInt()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val k = minOf(b.width() / W, b.height() / H)
        c.save()
        c.translate(b.left + (b.width() - W * k) / 2f, b.top + (b.height() - H * k) / 2f)
        c.scale(k, k)

        // Card background
        r.set(0f, 0f, W, H)
        p.shader = LinearGradient(0f, 0f, 0f, H, 0xFF1C1E25.toInt(), 0xFF121318.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 14f, 14f, p); p.shader = null
        st.color = 0x22FFFFFF; st.strokeWidth = 0.8f
        c.drawRoundRect(r, 14f, 14f, st)

        drawNotification(c)
        chevron(c, 121f, 80f)
        drawPeekPhone(c)
        chevron(c, 238f, 80f)
        drawPanelPhone(c)

        val cols = floatArrayOf(13f, 130f, 247f)
        val titles = intArrayOf(R.string.how_step1_title, R.string.how_step2_title, R.string.how_step3_title)
        val subs = intArrayOf(R.string.how_step1_sub, R.string.how_step2_sub, R.string.how_step3_sub)
        for (i in 0 until 3) caption(c, cols[i], i + 1, s(titles[i]), s(subs[i]))
        c.restore()
    }

    // ---- step 1: a stacked conversation notification ------------------------------------------

    private fun drawNotification(c: Canvas) {
        // Two cards peeking out behind
        box(c, 25f, 29f, 103f, 70f, 9f, 0xFF262830.toInt())
        box(c, 19f, 37f, 109f, 80f, 9f, 0xFF2A2C35.toInt())
        val x0 = 13f; val y0 = 45f; val x1 = 113f; val y1 = 116f
        box(c, x0, y0, x1, y1, 10f, 0xFF30323C.toInt())
        st.color = 0x26FFFFFF; st.strokeWidth = 0.6f
        r.set(x0, y0, x1, y1); c.drawRoundRect(r, 10f, 10f, st)
        avatar(c, s(R.string.how_group_av), 28f, 59f, 6.5f, 0xFF3B82F6.toInt())
        text(c, s(R.string.how_time), x1 - 7f, 62f, 6.2f, 0xFF9CA3AF.toInt(), right = true)
        tp.textSize = 6.2f
        val timeW = tp.measureText(s(R.string.how_time))
        text(c, s(R.string.how_group), 39f, 62.5f, 7.6f, 0xFFF4F5F8.toInt(), b = true, maxW = x1 - 7f - timeW - 4f - 39f)
        val lines = listOf(R.string.how_m1_sender to R.string.how_m1, R.string.how_m2_sender to R.string.how_m2,
            R.string.how_m3_sender to R.string.how_m3)
        lines.forEachIndexed { i, (who, what) ->
            val y = 80f + i * 12f
            tp.textSize = 6.6f; tp.typeface = bold
            val name = s(who) + s(R.string.name_sep)
            val nw = tp.measureText(name)
            text(c, name, 20f, y, 6.6f, 0xFFF4F5F8.toInt(), b = true)
            text(c, s(what), 20f + nw, y, 6.6f, 0xFFD1D5DB.toInt(), maxW = x1 - 7f - 20f - nw)
        }
    }

    // ---- step 2: a phone with the Peek bar at the edge -----------------------------------------

    private fun drawPeekPhone(c: Canvas) {
        val x0 = 137f; val y0 = 15f; val x1 = 223f; val y1 = 141f
        r.set(x0, y0, x1, y1)
        p.shader = LinearGradient(x0, y0, x1, y1, 0xFF262B3A.toInt(), 0xFF1E2A26.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 12f, 12f, p); p.shader = null
        c.save()
        path.reset(); path.addRoundRect(r, 12f, 12f, Path.Direction.CW); c.clipPath(path)
        blob(c, 196f, 30f, 40f, 0xB03B5BA0.toInt())
        blob(c, 158f, 112f, 46f, 0xA0306A4C.toInt())
        c.restore()
        st.color = 0x33FFFFFF; st.strokeWidth = 0.8f
        c.drawRoundRect(r, 12f, 12f, st)
        // Peek bar with a soft glow and three dots
        blob(c, 212f, 89f, 16f, 0x334ADE80)
        box(c, 207f, 65f, 216f, 114f, 4.5f, 0x55FFFFFF)
        st.color = 0xB3FFFFFF.toInt(); st.strokeWidth = 0.9f
        r.set(207f, 65f, 216f, 114f); c.drawRoundRect(r, 4.5f, 4.5f, st)
        for (i in 0 until 3) circle(c, 211.5f, 83f + i * 6f, 1.1f, 0xE6FFFFFF.toInt())
    }

    // ---- step 3: the side panel opened with a swipe ---------------------------------------------

    private fun drawPanelPhone(c: Canvas) {
        val x0 = 254f; val y0 = 15f; val x1 = 340f; val y1 = 141f
        box(c, x0, y0, x1, y1, 12f, 0xFF1D2027.toInt())
        st.color = 0x33FFFFFF; st.strokeWidth = 0.8f
        r.set(x0, y0, x1, y1); c.drawRoundRect(r, 12f, 12f, st)
        val px0 = 273f; val py0 = 24f; val px1 = 335f; val py1 = 132f
        box(c, px0, py0, px1, py1, 9f, 0xFF0F1014.toInt())
        st.color = 0x40FFFFFF; r.set(px0, py0, px1, py1); c.drawRoundRect(r, 9f, 9f, st)
        val colors = intArrayOf(0xFF3B82F6.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(), 0xFFA855F7.toInt(), 0xFF3B82F6.toInt())
        val avs = s(R.string.how_avatars).split(',').map { it.trim() }
        for (i in 0 until 5) {
            val y = 35f + i * 16.5f
            avatar(c, avs.getOrElse(i) { "" }, 284f, y, 5.3f, colors[i])
            box(c, 293f, y - 3.2f, 293f + 26f - (i % 3) * 3f, y - 1.2f, 1f, 0xFFE5E7EB.toInt())
            box(c, 293f, y + 1.6f, 293f + 36f - (i % 2) * 6f, y + 3.4f, 1f, 0xFF6B7280.toInt())
        }
        // Swipe arrow
        st.color = 0xFF4ADE80.toInt(); st.strokeWidth = 1.6f; st.strokeCap = Paint.Cap.ROUND
        c.drawLine(298f, 131f, 344f, 131f, st)
        p.color = 0xFF4ADE80.toInt(); p.alpha = 255
        path.reset(); path.moveTo(293f, 131f); path.lineTo(301f, 126.5f); path.lineTo(301f, 135.5f); path.close()
        c.drawPath(path, p)
        st.strokeCap = Paint.Cap.BUTT
    }

    // ---- captions under each picture -------------------------------------------------------------

    private fun caption(c: Canvas, x: Float, n: Int, title: String, sub: String) {
        val colW = 108f
        // Number badge
        circle(c, x + 8f, 162f, 8f, 0xFF86EFAC.toInt())
        text(c, "$n", x + 8f, 165f, 9f, 0xFF052E16.toInt(), b = true, center = true)
        // Title: shrink until it fits next to the badge
        val tx = x + 20f; val maxW = colW - 20f
        var size = 15f
        tp.typeface = bold
        while (size > 7.5f) { tp.textSize = size; if (tp.measureText(title) <= maxW) break; size -= 0.5f }
        text(c, title, tx, 167.5f, size, 0xFFF4F5F8.toInt(), b = true, maxW = maxW)
        // Description: up to three lines
        tp.typeface = Typeface.DEFAULT; tp.color = 0xFF9CA3AF.toInt(); tp.alpha = 255
        var sz = 10f
        var layout: StaticLayout
        while (true) {
            tp.textSize = sz
            layout = StaticLayout.Builder.obtain(sub, 0, sub.length, TextPaint(tp), (colW - 4f).toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.6f, 1f).setIncludePad(false)
                .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
            if (layout.height <= 50f || sz <= 8f) break
            sz -= 0.4f
        }
        c.save(); c.translate(x, 179f); layout.draw(c); c.restore()
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private fun chevron(c: Canvas, x: Float, y: Float) {
        st.color = 0x80FFFFFF.toInt(); st.strokeWidth = 1.4f; st.strokeCap = Paint.Cap.ROUND
        path.reset(); path.moveTo(x - 2.5f, y - 5f); path.lineTo(x + 2.5f, y); path.lineTo(x - 2.5f, y + 5f)
        c.drawPath(path, st); st.strokeCap = Paint.Cap.BUTT
    }

    private fun blob(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        p.shader = RadialGradient(x, y, rad, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); p.shader = null
    }

    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, color: Int) {
        p.shader = null; p.color = color
        r.set(x0, y0, x1, y1); c.drawRoundRect(r, rad, rad, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        p.shader = null; p.color = color; c.drawCircle(x, y, rad, p)
    }

    private fun avatar(c: Canvas, letter: String, x: Float, y: Float, rad: Float, color: Int) {
        circle(c, x, y, rad, color)
        tp.textSize = rad * 1.05f; tp.typeface = bold; tp.color = 0xFFFFFFFF.toInt()
        val w = tp.measureText(letter)
        c.drawText(letter, x - w / 2, y - (tp.ascent() + tp.descent()) / 2, tp)
    }

    private fun text(c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int, b: Boolean = false,
                     maxW: Float = 0f, center: Boolean = false, right: Boolean = false) {
        tp.textSize = size; tp.typeface = if (b) bold else Typeface.DEFAULT; tp.color = color
        val t = if (maxW > 0f) TextUtils.ellipsize(str, tp, maxW, TextUtils.TruncateAt.END).toString() else str
        val w = tp.measureText(t)
        c.drawText(t, when { center -> x - w / 2; right -> x - w; else -> x }, y, tp)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

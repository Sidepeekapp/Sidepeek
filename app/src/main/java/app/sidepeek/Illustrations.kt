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
 * 說明圖的共用底：在 [designW] × [designH] 的設計座標裡畫，再等比例縮放到 ImageView 的大小。
 * 文字全部從 strings.xml 讀，所以每種語言都有自己的版本（取代原本內含中文的 webp 圖）。固定深色。
 */
abstract class IllustrationDrawable(protected val ctx: Context, private val designW: Float, private val designH: Float) : Drawable() {

    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    protected val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    protected val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    protected val r = RectF()
    protected val path = Path()
    protected fun s(id: Int) = ctx.getString(id)

    override fun getIntrinsicWidth() = (designW * 3).toInt()
    override fun getIntrinsicHeight() = (designH * 3).toInt()

    final override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val k = minOf(b.width() / designW, b.height() / designH)
        c.save()
        c.translate(b.left + (b.width() - designW * k) / 2f, b.top + (b.height() - designH * k) / 2f)
        c.scale(k, k)
        r.set(0f, 0f, designW, designH)
        p.shader = LinearGradient(0f, 0f, 0f, designH, 0xFF1C1E25.toInt(), 0xFF121318.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 14f, 14f, p); p.shader = null
        st.color = 0x22FFFFFF; st.strokeWidth = 0.8f
        c.drawRoundRect(r, 14f, 14f, st)
        c.save(); path.reset(); path.addRoundRect(r, 14f, 14f, Path.Direction.CW); c.clipPath(path)
        paint(c)
        c.restore()
        c.restore()
    }

    protected abstract fun paint(c: Canvas)

    protected fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, color: Int) {
        p.shader = null; p.color = color; r.set(x0, y0, x1, y1); c.drawRoundRect(r, rad, rad, p)
    }
    protected fun outline(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, color: Int, w: Float = 0.8f) {
        st.color = color; st.strokeWidth = w; r.set(x0, y0, x1, y1); c.drawRoundRect(r, rad, rad, st)
    }
    protected fun circle(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        p.shader = null; p.color = color; c.drawCircle(x, y, rad, p)
    }
    protected fun blob(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        p.shader = RadialGradient(x, y, rad, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); p.shader = null
    }
    protected fun avatar(c: Canvas, letter: String, x: Float, y: Float, rad: Float, color: Int) {
        circle(c, x, y, rad, color)
        tp.textSize = rad * 1.05f; tp.typeface = bold; tp.color = 0xFFFFFFFF.toInt()
        val w = tp.measureText(letter)
        c.drawText(letter, x - w / 2, y - (tp.ascent() + tp.descent()) / 2, tp)
    }
    protected fun textW(str: String, size: Float, b: Boolean = false): Float {
        tp.textSize = size; tp.typeface = if (b) bold else Typeface.DEFAULT; return tp.measureText(str)
    }
    protected fun text(c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int, b: Boolean = false,
                       maxW: Float = 0f, center: Boolean = false, right: Boolean = false) {
        tp.textSize = size; tp.typeface = if (b) bold else Typeface.DEFAULT; tp.color = color
        val t = if (maxW > 0f) TextUtils.ellipsize(str, tp, maxW, TextUtils.TruncateAt.END).toString() else str
        val w = tp.measureText(t)
        c.drawText(t, when { center -> x - w / 2; right -> x - w; else -> x }, y, tp)
    }
    /** Size that fits [str] on one line within [maxW], from [size] down to [min]. */
    protected fun fit(str: String, maxW: Float, size: Float, min: Float, b: Boolean): Float {
        var sz = size
        while (sz > min && textW(str, sz, b) > maxW) sz -= 0.5f
        return sz
    }
    /** Wrapped text starting at (x, top), at most [maxLines] lines; returns its height. */
    protected fun para(c: Canvas, str: String, x: Float, top: Float, w: Float, size: Float, color: Int,
                       b: Boolean = false, maxLines: Int = 3): Float {
        tp.textSize = size; tp.typeface = if (b) bold else Typeface.DEFAULT; tp.color = color
        val l = StaticLayout.Builder.obtain(str, 0, str.length, TextPaint(tp), w.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(size * 0.22f, 1f).setIncludePad(false)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(x, top); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * 「認識 Sidepeek」最上面的比較圖（原 img_faq_header）：同一支手機右邊，上面是三星側拉標籤
 * （系統、App 捷徑），下面是 Sidepeek 的 Peek 條（訊息），兩個各自獨立、可以錯開位置。
 */
class EdgeHandlesDrawable(ctx: Context) : IllustrationDrawable(ctx, 360f, 165f) {
    override fun paint(c: Canvas) {
        // Phone
        val x0 = 16f; val y0 = 14f; val x1 = 116f; val y1 = 178f
        r.set(x0, y0, x1, y1)
        p.shader = LinearGradient(x0, y0, x1, y1, 0xFF2A2D36.toInt(), 0xFF1E2028.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 16f, 16f, p); p.shader = null
        c.save(); path.reset(); path.addRoundRect(r, 16f, 16f, android.graphics.Path.Direction.CW); c.clipPath(path)
        blob(c, 40f, 150f, 55f, 0x8C2A4A80.toInt())
        c.restore()
        outline(c, x0, y0, x1, y1, 16f, 0x40FFFFFF)
        box(c, 52f, 20f, 80f, 27f, 3.5f, 0xFF0B0C10.toInt())
        // Samsung Edge panel handle (top) and the Peek bar (lower)
        box(c, 108f, 34f, 113f, 70f, 2.5f, 0x8CFFFFFF.toInt())
        blob(c, 106f, 108f, 16f, 0x404ADE80)
        box(c, 102f, 84f, 112f, 132f, 5f, 0x40FFFFFF)
        outline(c, 102f, 84f, 112f, 132f, 5f, 0xB3FFFFFF.toInt(), 0.9f)
        for (i in 0 until 3) circle(c, 107f, 101f + i * 6f, 1.2f, 0xE6FFFFFF.toInt())
        // Leader lines
        st.strokeCap = Paint.Cap.ROUND
        st.color = 0x80FFFFFF.toInt(); st.strokeWidth = 1.2f; c.drawLine(122f, 50f, 140f, 50f, st)
        st.color = 0xFF4ADE80.toInt(); c.drawLine(122f, 108f, 140f, 108f, st)
        st.strokeCap = Paint.Cap.BUTT
        // Labels
        val tx = 148f; val w = 360f - tx - 12f
        val t1 = s(R.string.edge_samsung_title)
        text(c, t1, tx, 54f, fit(t1, w, 15f, 10f, true), 0xFFF4F5F8.toInt(), b = true, maxW = w)
        text(c, s(R.string.edge_samsung_sub), tx, 72f, 12f, 0xFFA3A8B8.toInt(), maxW = w)
        val t2 = s(R.string.edge_peek_title)
        text(c, t2, tx, 112f, fit(t2, w, 15f, 10f, true), 0xFF4ADE80.toInt(), b = true, maxW = w)
        text(c, s(R.string.edge_peek_sub), tx, 130f, 12f, 0xFFA3A8B8.toInt(), maxW = w)
        // Note pill
        val note = s(R.string.edge_note)
        val ns = fit(note, w - 16f, 11f, 8f, false)
        val nw = textW(note, ns, false) + 16f
        box(c, tx - 4f, 140f, tx - 4f + nw, 157f, 8.5f, 0x1FFFFFFF)
        text(c, note, tx + 4f, 152.3f, ns, 0xFFD1D5DB.toInt(), maxW = w - 16f)
    }
}

/**
 * 設定精靈「打開側邊欄」那一步的圖（原 img_step_sidebar）：手機右邊的 Peek 條往內滑，
 * 側邊欄滑出來，列出最近的訊息。
 */
class SidebarStepDrawable(ctx: Context) : IllustrationDrawable(ctx, 360f, 240f) {
    override fun paint(c: Canvas) {
        // Phone (runs off the bottom edge)
        val x0 = 16f; val y0 = 14f; val x1 = 236f; val y1 = 270f
        r.set(x0, y0, x1, y1)
        p.shader = LinearGradient(x0, y0, x1, y1, 0xFF262B34.toInt(), 0xFF1B1E26.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18f, 18f, p); p.shader = null
        c.save(); path.reset(); path.addRoundRect(r, 18f, 18f, android.graphics.Path.Direction.CW); c.clipPath(path)
        blob(c, 200f, 40f, 60f, 0x66306A5C)
        c.restore()
        outline(c, x0, y0, x1, y1, 18f, 0x33FFFFFF)
        text(c, s(R.string.side_clock), 28f, 31f, 8.5f, 0xFFA3A8B8.toInt())
        // Side panel
        val px0 = 28f; val py0 = 38f; val px1 = 202f; val py1 = 236f
        box(c, px0, py0, px1, py1, 14f, 0xFF0F1014.toInt())
        outline(c, px0, py0, px1, py1, 14f, 0x40FFFFFF)
        text(c, s(R.string.panel_title), 38f, 58f, 13f, 0xFFF4F5F8.toInt(), b = true, maxW = 150f)
        data class Row(val av: String, val name: String, val time: String, val sender: String?, val msg: String, val color: Int, val link: Boolean)
        val rows = listOf(
            Row(s(R.string.how_group_av), s(R.string.how_group), s(R.string.how_time), s(R.string.how_m1_sender), s(R.string.how_m1), 0xFF3B82F6.toInt(), false),
            Row(s(R.string.side_c2_av), s(R.string.side_c2_name), s(R.string.side_c2_time), null, s(R.string.side_c2_msg), 0xFFF59E0B.toInt(), false),
            Row(s(R.string.side_c3_av), s(R.string.side_c3_name), s(R.string.side_c3_time), null, s(R.string.side_c3_msg), 0xFF22C55E.toInt(), true),
        )
        var y = 70f
        for (row in rows) {
            avatar(c, row.av, 42f, y + 3f, 8.5f, row.color)
            val tw = textW(row.time, 8.5f)
            text(c, row.time, 194f, y + 6f, 8.5f, 0xFF8E8E93.toInt(), right = true)
            text(c, row.name, 56f, y + 7f, 10.5f, 0xFFF4F5F8.toInt(), b = true, maxW = 194f - tw - 6f - 56f)
            val bh = if (row.link) 38f else 22f
            box(c, 56f, y + 12f, 194f, y + 12f + bh, 8f, 0xFF2A2C36.toInt())
            val line = if (row.sender != null) row.sender + s(R.string.name_sep) + row.msg else row.msg
            text(c, line, 63f, y + 26.5f, fit(line, 124f, 9.5f, 8f, false), 0xFFE5E7EB.toInt(), maxW = 124f)
            if (row.link) {
                val lab = s(R.string.action_open_link)
                val lw = textW(lab, 8.5f, true) + 14f
                box(c, 63f, y + 31f, 63f + lw, y + 45f, 7f, 0xFF1F4D33.toInt())
                text(c, lab, 70f, y + 41f, 8.5f, 0xFF86EFAC.toInt(), b = true)
            }
            y += 20f + bh + 8f
        }
        // Peek bar at the right edge of the phone
        blob(c, 222f, 120f, 18f, 0x404ADE80)
        box(c, 217f, 94f, 226f, 146f, 4.5f, 0x40FFFFFF)
        outline(c, 217f, 94f, 226f, 146f, 4.5f, 0xB3FFFFFF.toInt(), 0.9f)
        for (i in 0 until 3) circle(c, 221.5f, 114f + i * 6f, 1.2f, 0xE6FFFFFF.toInt())
        // Right side: arrow and the two lines of text
        val tx = 248f; val w = 360f - tx - 10f
        para(c, s(R.string.side_title), tx, 52f, w, 15f, 0xFF4ADE80.toInt(), b = true, maxLines = 3)
        st.color = 0xFF4ADE80.toInt(); st.strokeWidth = 2f; st.strokeCap = Paint.Cap.ROUND
        c.drawLine(244f, 122f, 346f, 122f, st); st.strokeCap = Paint.Cap.BUTT
        p.color = 0xFF4ADE80.toInt()
        path.reset(); path.moveTo(236f, 122f); path.lineTo(246f, 116f); path.lineTo(246f, 128f); path.close(); c.drawPath(path, p)
        para(c, s(R.string.side_sub), tx, 138f, w, 12f, 0xFFA3A8B8.toInt(), maxLines = 4)
    }
}

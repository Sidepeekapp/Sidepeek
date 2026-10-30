package app.sidepeek

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache

/** Deterministic initial avatars used when a notification names a person but supplies no icon. */
object AvatarBadge {
    private val palette = intArrayOf(
        // 12 clearly different hues, each readable with white text (contrast >= 4.6). No green,
        // so a badge never looks like the brand colour.
        0xFFBE123C.toInt(), // rose
        0xFFDB2777.toInt(), // pink
        0xFFC026D3.toInt(), // magenta
        0xFF7C3AED.toInt(), // purple
        0xFF4F46E5.toInt(), // indigo
        0xFF2563EB.toInt(), // blue
        0xFF0E7490.toInt(), // cyan
        0xFF0F766E.toInt(), // teal
        0xFFA16207.toInt(), // amber
        0xFFC2410C.toInt(), // orange
        0xFF78350F.toInt(), // brown
        0xFF475569.toInt(), // slate
    )

    private val cache = object : LruCache<String, Bitmap>(40) {}

    fun initial(label: String): String {
        val s = label.trim()
        return if (s.isEmpty()) "?" else String(Character.toChars(s.codePointAt(0)))
    }

    fun colorFor(label: String): Int = palette[Math.floorMod(label.trim().hashCode(), palette.size)]

    fun bitmap(label: String, px: Int, color: Int = colorFor(label)): Bitmap {
        val size = px.coerceAtLeast(1)
        val key = "$label|$size|$color"
        cache.get(key)?.let { return it }

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = size * 0.46f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val fm = paint.fontMetrics
        canvas.drawText(initial(label), size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
        cache.put(key, bmp)
        return bmp
    }
}

package app.sidepeek

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.drawable.IconCompat
import java.io.File

/**
 * The small icon of Sidepeek's notifications (status bar and notification header), chosen in
 * 設定 → 通知外觀 → 狀態列小圖示: one of the built-in [CHOICES] or the user's own picture ([CUSTOM]).
 *
 * Android draws the small icon as a one-colour silhouette from its alpha channel, so a custom
 * picture is turned into a white mask first ([makeSilhouette]) and stored in filesDir; the
 * picker shows that mask, i.e. what the status bar will show. The App badge at the bottom right
 * of a conversation notification is the launcher icon and cannot be changed.
 */
object NotifIcons {
    data class Choice(val id: String, val res: Int, val label: Int)

    const val DEFAULT = "sidepeek"
    const val CUSTOM = "custom"

    val CHOICES = listOf(
        Choice(DEFAULT, R.drawable.ic_stat_msg, R.string.app_name),
        Choice("chat", R.drawable.ic_stat_chat, R.string.icon_chat),
        Choice("forum", R.drawable.ic_stat_forum, R.string.icon_forum),
        Choice("bell", R.drawable.ic_stat_bell, R.string.icon_bell),
        Choice("mail", R.drawable.ic_stat_mail, R.string.icon_mail),
        Choice("heart", R.drawable.ic_stat_heart, R.string.icon_heart),
        Choice("star", R.drawable.ic_stat_star, R.string.icon_star),
        Choice("bolt", R.drawable.ic_stat_bolt, R.string.icon_bolt),
    )

    /** Size of the stored custom mask (status bar icons are 24dp; 96px is sharp up to 4x). */
    private const val SIZE = 96

    fun file(ctx: Context) = File(ctx.filesDir, "notif_icon.png")
    fun hasCustom(ctx: Context) = file(ctx).exists()

    @Volatile private var cached: Pair<Long, Bitmap>? = null

    fun customBitmap(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val stamp = f.lastModified()
        cached?.let { if (it.first == stamp) return it.second }
        return BitmapFactory.decodeFile(f.path)?.also { cached = stamp to it }
    }

    /** The icon for new notifications; falls back to the default when anything is missing. */
    fun current(ctx: Context): IconCompat {
        val id = Prefs.notifIcon(ctx)
        if (id == CUSTOM) customBitmap(ctx)?.let { return IconCompat.createWithBitmap(it) }
        val res = (CHOICES.firstOrNull { it.id == id } ?: CHOICES[0]).res
        return IconCompat.createWithResource(ctx, res)
    }

    /** Reads [uri], turns it into a silhouette and stores it. Returns false if it cannot be used. */
    fun saveCustom(ctx: Context, uri: Uri): Boolean {
        val src = decode(ctx, uri) ?: return false
        val mask = makeSilhouette(src) ?: return false
        return try {
            file(ctx).outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cached = null
            true
        } catch (e: Exception) {
            Diagnostics.note(ctx, "NOTIF icon save failed: $e"); false
        }
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        Diagnostics.note(ctx, "NOTIF icon decode failed: $e"); null
    }

    /**
     * Square, [SIZE]px, white where the picture has its subject and transparent elsewhere.
     * A picture with its own transparency keeps it; otherwise the subject is whatever differs
     * from the background colour (taken from the corners). Null if nothing usable is left.
     */
    fun makeSilhouette(src: Bitmap): Bitmap? {
        // Fit the whole picture into a square (no cropping), on transparent.
        val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val scale = SIZE.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val left = (SIZE - w) / 2; val top = (SIZE - h) / 2
        Canvas(square).drawBitmap(src, null, Rect(left, top, left + w, top + h),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(SIZE * SIZE)
        square.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val inside = { i: Int -> (i % SIZE) in left until left + w && (i / SIZE) in top until top + h }

        // Own transparency: a noticeable share of see-through pixels inside the picture.
        val seeThrough = px.indices.count { inside(it) && Color.alpha(px[it]) < 128 }
        val useAlpha = seeThrough > w * h / 20
        val out = IntArray(px.size)
        if (useAlpha) {
            for (i in px.indices) out[i] = Color.argb(Color.alpha(px[i]), 255, 255, 255)
        } else {
            // Background = average of the four corners of the picture.
            val corners = listOf(top * SIZE + left, top * SIZE + left + w - 1,
                (top + h - 1) * SIZE + left, (top + h - 1) * SIZE + left + w - 1).map { px[it] }
            val br = corners.sumOf { Color.red(it) } / 4
            val bg = corners.sumOf { Color.green(it) } / 4
            val bb = corners.sumOf { Color.blue(it) } / 4
            for (i in px.indices) {
                if (!inside(i)) continue
                val c = px[i]
                val d = Math.abs(Color.red(c) - br) + Math.abs(Color.green(c) - bg) + Math.abs(Color.blue(c) - bb)
                // Soft edge between 60 and 120 (sum of channel differences).
                val a = ((d - 60) * 255 / 60).coerceIn(0, 255)
                out[i] = Color.argb(a, 255, 255, 255)
            }
        }
        val solid = out.count { Color.alpha(it) >= 128 }
        // Nothing, or the whole square: there is no shape to show.
        if (solid < SIZE * SIZE / 50 || solid > SIZE * SIZE * 97 / 100) return null
        return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }
}

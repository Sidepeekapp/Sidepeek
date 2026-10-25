package app.sidepeek

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

/** An app whose notifications go into the side panel. */
data class SourceApp(
    val key: String,
    val name: String,
    val packages: List<String>,
    val color: Int,
    /** Short text shown in the colored badge in the side panel. */
    val badge: String,
)

/** One installed, launchable app the user can pick in the source picker. */
data class InstalledApp(val pkg: String, val label: String, val icon: Drawable?)

/**
 * Which apps feed the side panel.
 *
 * The user picks any installed app; the choice is stored as a set of package names. On first use
 * LINE, Instagram, Threads, Messenger and Teams are on by default. Those well-known apps keep their
 * brand color and short badge; any other app shows its own label and a color taken from its icon.
 * Only LINE is ever "taken over" (re-posted + original hidden); every other app is panel-only.
 */
object Sources {
    val LINE = SourceApp("line", "LINE", listOf("jp.naver.line.android", "com.linecorp.linelite"), 0xFF16A34A.toInt(), "LINE")
    val MESSENGER = SourceApp("messenger", "Messenger", listOf("com.facebook.orca", "com.facebook.mlite"), 0xFF0A7CFF.toInt(), "Messenger")
    val INSTAGRAM = SourceApp("instagram", "Instagram", listOf("com.instagram.android"), 0xFFE1306C.toInt(), "IG")
    val THREADS = SourceApp("threads", "Threads", listOf("com.instagram.barcelona"), 0xFF8E8E93.toInt(), "Threads")
    val TEAMS = SourceApp("teams", "Microsoft Teams", listOf("com.microsoft.teams"), 0xFF6264A7.toInt(), "Teams")

    /** Apps with a hand-picked color/badge. Also the default selection. */
    val KNOWN = listOf(LINE, INSTAGRAM, THREADS, MESSENGER, TEAMS)
    private val KNOWN_PACKAGES: Set<String> = KNOWN.flatMap { it.packages }.toSet()
    val DEFAULT_PACKAGES: Set<String> = KNOWN_PACKAGES

    private const val KEY_SELECTED = "src_pkgs"
    private const val FALLBACK_COLOR = 0xFF64748B.toInt()

    @Volatile private var selectedCache: Set<String>? = null
    private val dynamic = ConcurrentHashMap<String, SourceApp>()

    // ------------------------------------------------------------------ selection

    /** Package names whose notifications go into the side panel. */
    fun selected(ctx: Context): Set<String> {
        selectedCache?.let { return it }
        val sp = Prefs.sp(ctx)
        val stored = sp.getStringSet(KEY_SELECTED, null)
        val set = if (stored != null) HashSet(stored) else {
            // First run (or upgrade from the old per-app switches "src_<key>").
            val init = KNOWN.filter { sp.getBoolean("src_${it.key}", true) }.flatMap { it.packages }.toSet()
            sp.edit().putStringSet(KEY_SELECTED, init).apply()
            init
        }
        selectedCache = set
        return set
    }

    fun setSelected(ctx: Context, pkgs: Set<String>) {
        val copy = HashSet(pkgs).apply { remove(ctx.packageName) }
        Prefs.sp(ctx).edit().putStringSet(KEY_SELECTED, copy).apply()
        selectedCache = copy
    }

    fun isSelected(ctx: Context, pkg: String) = pkg in selected(ctx)

    fun setPackageSelected(ctx: Context, pkg: String, on: Boolean) {
        val s = HashSet(selected(ctx))
        if (on) s += pkg else s -= pkg
        setSelected(ctx, s)
    }

    /** Should notifications from [pkg] be looked at at all? (LINE also when take-over is on.) */
    fun isTracked(ctx: Context, pkg: String): Boolean =
        pkg in selected(ctx) || (pkg in LINE.packages && Prefs.enabled(ctx))

    fun isEnabled(ctx: Context, src: SourceApp) = src.packages.any { it in selected(ctx) }

    fun setEnabled(ctx: Context, src: SourceApp, on: Boolean) {
        val s = HashSet(selected(ctx))
        if (on) s += src.packages else s -= src.packages.toSet()
        setSelected(ctx, s)
    }

    // ------------------------------------------------------------------ lookup

    fun known(pkg: String): SourceApp? = KNOWN.firstOrNull { pkg in it.packages }

    /** Source for a package: a known app, otherwise built from the app's own label and icon. */
    fun forPackage(ctx: Context, pkg: String): SourceApp? {
        known(pkg)?.let { return it }
        dynamic[pkg]?.let { return it }
        val pm = ctx.packageManager
        val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
        val label = info?.let { pm.getApplicationLabel(it).toString() }?.ifBlank { null } ?: pkg.substringAfterLast('.')
        val color = try { info?.let { iconColor(pm.getApplicationIcon(it)) } } catch (_: Exception) { null } ?: FALLBACK_COLOR
        return SourceApp(pkg, label, listOf(pkg), color, shortBadge(label)).also { dynamic[pkg] = it }
    }

    /** Known-app lookup only (no Context needed); null for user-picked apps. */
    fun forPackage(pkg: String): SourceApp? = known(pkg)

    fun displayName(ctx: Context, pkg: String): String = when (pkg) {
        "com.linecorp.linelite" -> "LINE Lite"
        "com.facebook.mlite" -> "Messenger Lite"
        else -> forPackage(ctx, pkg)?.name ?: pkg
    }

    /** Display name without a Context (known apps only). */
    fun displayName(pkg: String): String = when (pkg) {
        "com.linecorp.linelite" -> "LINE Lite"
        "com.facebook.mlite" -> "Messenger Lite"
        else -> known(pkg)?.name ?: pkg
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    fun isInstalled(ctx: Context, src: SourceApp): Boolean = src.packages.any { isInstalled(ctx, it) }

    /** Every launchable app on the phone except this one, sorted by label. */
    fun installedApps(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .mapNotNull { pkg ->
                    try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        InstalledApp(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
                    } catch (_: Exception) { null }
                }
                .sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.TAIWAN)) { it.label })
        } catch (_: Exception) { emptyList() }
    }

    // ------------------------------------------------------------------ helpers

    private fun shortBadge(label: String): String = if (label.length <= 10) label else label.take(9) + "…"

    /** Average color of the icon's saturated pixels, darkened enough for white badge text. */
    private fun iconColor(d: Drawable): Int? {
        val size = 24
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size); d.draw(c)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val hsv = FloatArray(3)
        for (y in 0 until size) for (x in 0 until size) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 200) continue
            Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
        }
        bmp.recycle()
        if (n < 10) return null
        Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[2] = hsv[2].coerceAtMost(0.62f)   // keep white badge text readable
        return Color.HSVToColor(hsv)
    }
}

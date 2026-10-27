package app.sidepeek

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「自動檢查新版本」: when the app is opened, at most once per [INTERVAL_MS], ask GitHub for the
 * latest published Release (drafts and pre-releases are ignored by this endpoint). A release is
 * newer when its tag (v1.0.3) is a higher version than ours (versionName), or, for releases made
 * before tags were compared, when an APK is named "…build<N>.apk" with N above our versionCode.
 * No data about the user is sent; it is a plain anonymous GET.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/Sidepeekapp/sidepeek/releases/latest"
    private const val INTERVAL_MS = 20 * 3600_000L
    private val BUILD_RE = Regex("""build(\d+)\.apk$""", RegexOption.IGNORE_CASE)

    const val KEY_ENABLED = "update_check"
    private const val KEY_LAST = "update_last_check"
    private const val KEY_BUILD = "update_build"
    private const val KEY_TAG = "update_tag"
    private const val KEY_URL = "update_url"

    data class Update(val tag: String, val build: Int, val url: String)

    /** -1 / 0 / 1 comparing dotted versions numerically ("1.0.10" > "1.0.9"; a leading "v" is ignored). */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-', ' ')
            .map { it.takeWhile(Char::isDigit) }.takeWhile { it.isNotEmpty() }.map { it.toInt() }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun isNewer(u: Update): Boolean =
        (u.tag.isNotBlank() && compareVersions(u.tag, BuildConfig.VERSION_NAME) > 0) ||
            u.build > BuildConfig.VERSION_CODE

    fun enabled(ctx: Context) = Prefs.sp(ctx).getBoolean(KEY_ENABLED, true)

    /** The newer release found by the last check, or null if we are up to date. */
    fun available(ctx: Context): Update? {
        if (!enabled(ctx)) return null
        val sp = Prefs.sp(ctx)
        val url = sp.getString(KEY_URL, null) ?: return null
        val u = Update(sp.getString(KEY_TAG, "") ?: "", sp.getInt(KEY_BUILD, 0), url)
        return if (isNewer(u)) u else null
    }

    /** Check in the background if due; [onDone] runs on the main thread when something changed. */
    fun checkIfDue(ctx: Context, onDone: () -> Unit) {
        if (!enabled(ctx)) return
        val app = ctx.applicationContext
        val sp = Prefs.sp(app)
        val now = System.currentTimeMillis()
        if (now - sp.getLong(KEY_LAST, 0L) < INTERVAL_MS) return
        sp.edit().putLong(KEY_LAST, now).apply()
        Thread {
            val found = try { fetchLatest() } catch (e: Exception) {
                Diagnostics.note(app, "UPDATE check failed: ${e.javaClass.simpleName}"); null
            } ?: return@Thread
            sp.edit().putInt(KEY_BUILD, found.build).putString(KEY_TAG, found.tag).putString(KEY_URL, found.url).apply()
            Diagnostics.note(app, "UPDATE latest=${found.tag} build=${found.build} (installed ${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE})")
            if (isNewer(found)) Handler(Looper.getMainLooper()).post(onDone)
        }.start()
    }

    private fun fetchLatest(): Update? {
        val c = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Sidepeek/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (c.responseCode != 200) return null
            val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return parse(body)
        } finally {
            c.disconnect()
        }
    }

    /** Parse a GitHub "latest release" response; null when it has no version tag and no build number. */
    fun parse(json: String): Update? {
        val o = JSONObject(json)
        val url = o.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        var best = 0
        for (i in 0 until assets.length()) {
            val name = assets.optJSONObject(i)?.optString("name").orEmpty()
            BUILD_RE.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it > best) best = it }
        }
        val tag = o.optString("tag_name")
        if (best == 0 && compareVersions(tag, "0") == 0) return null
        return Update(tag, best, url)
    }
}

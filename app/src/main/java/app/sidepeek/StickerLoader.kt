package app.sidepeek

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discovers LINE sticker CDN URLs from notification extras (and MessagingStyle message extras)
 * and downloads them. Modern LINE often sends text「傳送了貼圖」without a direct
 * line.sticker.url — we reconstruct candidates from STKID / STKPKGID / STKVER and nested walks.
 */
object StickerLoader {
    const val KEY_STICKER_URL = "line.sticker.url"
    private val URL_RE = Regex(
        """https?://[^\s"'<>]*(?:line-scdn\.net|stickershop\.line-cdn\.net|line\.me)[^\s"'<>]*\.(?:png|gif|webp|jpg|jpeg)""",
        RegexOption.IGNORE_CASE,
    )
    /** Whole-message sticker labels only — never match「貼圖」inside longer chat text. */
    private val STICKER_TEXT_ONLY_RE = Regex(
        """^(?:傳送了貼圖|傳送貼圖|送出了貼圖|貼圖|スタンプ|sent a sticker|sticker)$""",
        RegexOption.IGNORE_CASE,
    )

    data class Discovery(
        val candidates: List<String>,
        val stickerKeys: List<String>,
        val stkId: String?,
        val stkPkgId: String?,
        val stkVer: String?,
    )

    fun looksLikeStickerText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return STICKER_TEXT_ONLY_RE.matches(text.trim())
    }

    /** Back-compat: first candidate URL, or null. */
    fun findStickerUrl(extras: Bundle?): String? = discover(extras).candidates.firstOrNull()

    @Suppress("DEPRECATION")
    fun discover(vararg bundles: Bundle?): Discovery {
        val urls = LinkedHashSet<String>()
        val keys = LinkedHashSet<String>()
        var stkId: String? = null
        var stkPkgId: String? = null
        var stkVer: String? = null

        fun digitId(v: Any?): String? = when (v) {
            is String -> v.trim().takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
            is Int -> v.toString()
            is Long -> v.toString()
            else -> null
        }

        fun considerKey(k: String, v: Any?) {
            val kl = k.lowercase()
            if (kl.contains("sticker") || kl.contains("stk") || kl.contains("stamp")) keys += k
            when {
                k.equals(KEY_STICKER_URL, true) || (kl.contains("sticker") && kl.contains("url")) -> {
                    val s = v?.toString()?.trim().orEmpty()
                    if (s.startsWith("http")) urls += s
                    URL_RE.findAll(s).forEach { urls += it.value }
                }
                k.equals("STKID", true) || kl == "stickerid" || kl.endsWith("sticker.id") ||
                    kl.endsWith("sticker_id") || kl == "line.sticker.id" -> {
                    digitId(v)?.let { if (stkId == null) stkId = it }
                }
                k.equals("STKPKGID", true) || kl == "packageid" || kl.contains("sticker.package") ||
                    kl.contains("stickerpackage") || kl == "line.sticker.package.id" ||
                    kl.endsWith("pkgid") && kl.contains("stk") -> {
                    digitId(v)?.let { if (stkPkgId == null) stkPkgId = it }
                }
                k.equals("STKVER", true) || kl.contains("sticker.ver") || kl == "stickerversion" ||
                    kl == "line.sticker.version" -> {
                    digitId(v)?.let { if (stkVer == null) stkVer = it }
                }
                v is String -> URL_RE.findAll(v).forEach { urls += it.value }
            }
        }

        fun walk(b: Bundle?, depth: Int) {
            if (b == null || depth > 8) return
            val ks = try { b.keySet() } catch (_: Throwable) { return }
            for (k in ks) {
                val v = try { b.get(k) } catch (_: Throwable) { null }
                considerKey(k, v)
                when (v) {
                    is Bundle -> walk(v, depth + 1)
                    is Array<*> -> v.forEach { item ->
                        when (item) {
                            is Bundle -> walk(item, depth + 1)
                            is String -> URL_RE.findAll(item).forEach { urls += it.value }
                            is Parcelable -> try {
                                // MessagingStyle.Message-like: reflect extras if present
                                val m = item.javaClass.methods.firstOrNull { it.name == "getExtras" && it.parameterCount == 0 }
                                val ex = m?.invoke(item) as? Bundle
                                if (ex != null) walk(ex, depth + 1)
                            } catch (_: Throwable) {}
                        }
                    }
                    is java.util.ArrayList<*> -> v.forEach { item ->
                        when (item) {
                            is Bundle -> walk(item, depth + 1)
                            is String -> URL_RE.findAll(item).forEach { urls += it.value }
                            is Parcelable -> try {
                                val m = item.javaClass.methods.firstOrNull { it.name == "getExtras" && it.parameterCount == 0 }
                                val ex = m?.invoke(item) as? Bundle
                                if (ex != null) walk(ex, depth + 1)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }

        for (b in bundles) walk(b, 0)

        val built = ArrayList<String>()
        // Prefer exact / regex URLs first.
        built += urls
        val id = stkId
        if (id != null) {
            built += "https://stickershop.line-scdn.net/stickershop/v1/sticker/$id/android/sticker.png"
            built += "https://stickershop.line-scdn.net/stickershop/v1/sticker/$id/ANDROID/sticker.png"
            built += "https://stickershop.line-scdn.net/stickershop/v1/sticker/$id/android/sticker_key.png"
            built += "https://stickershop.line-scdn.net/stickershop/v1/sticker/$id/iPhone/sticker.png"
            val pkg = stkPkgId
            if (pkg != null) {
                val vers = linkedSetOf<String>()
                stkVer?.let { vers += it }
                vers += listOf("1", "2", "9", "0")
                for (ver in vers) {
                    built += "https://stickershop.line-scdn.net/products/0/0/$ver/$pkg/android/stickers/$id.png"
                    built += "https://stickershop.line-scdn.net/products/0/0/$ver/$pkg/ANDROID/stickers/$id.png"
                }
            }
        }
        // De-dupe preserving order
        val candidates = built.map { it.trim() }.filter { it.startsWith("http") }.distinct()
        return Discovery(candidates, keys.toList().sorted(), stkId, stkPkgId, stkVer)
    }

    /**
     * Try each candidate (with one retry per URL). Returns the bitmap and the URL that worked.
     * Must be called off the main thread.
     */
    fun loadBest(ctx: Context, candidates: List<String>): Pair<Bitmap, String>? {
        if (candidates.isEmpty()) return null
        for (url in candidates) {
            load(ctx, url)?.let { return it to url }
            // one retry
            load(ctx, url)?.let { return it to url }
        }
        return null
    }

    /**
     * Diagnostic dump of sticker-related extras (key + value type / truncated value).
     * Never dumps full message bodies; values truncated at 200 chars.
     */
    @Suppress("DEPRECATION")
    fun dumpStickerExtras(vararg bundles: Bundle?): String {
        val parts = ArrayList<String>()
        for (b in bundles) {
            if (b == null) continue
            for (k in b.keySet()) {
                val kl = k.lowercase()
                if (!(kl.contains("sticker") || kl.contains("stk") || kl.contains("stamp") ||
                        kl.contains("emoji") || kl.contains("media") || kl.contains("image") ||
                        kl.contains("uri") || kl.contains("data"))) continue
                val v = try { b.get(k) } catch (_: Exception) { null }
                val type = v?.javaClass?.simpleName ?: "null"
                val preview = when (v) {
                    null -> "null"
                    is ByteArray -> "ByteArray(${v.size})"
                    is Bundle -> "Bundle(keys=${v.keySet().take(8)})"
                    is Array<*> -> "Array(${v.size})"
                    is IntArray -> "IntArray(${v.size})"
                    else -> v.toString().take(200)
                }
                parts += "$k:$type=$preview"
            }
        }
        return if (parts.isEmpty()) "(no sticker-ish keys)" else parts.joinToString(" | ").take(1500)
    }

    /** Download (with on-disk cache) and decode. Must be called off the main thread. */
    fun load(ctx: Context, url: String): Bitmap? {
        val dir = File(ctx.cacheDir, "stickers").apply { mkdirs() }
        val f = File(dir, url.hashCode().toUInt().toString(16) + ".img")
        if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath)?.let { return it }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Line/14.0.0")
                setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.i("Sidepeek", "sticker HTTP $code $url")
                conn.disconnect()
                f.delete()
                return null
            }
            conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp == null) f.delete()
            bmp
        } catch (e: Exception) {
            Log.i("Sidepeek", "sticker download failed $url: $e")
            f.delete()
            null
        }
    }
}

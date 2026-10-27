package app.sidepeek

import java.net.URI

/**
 * Extract up to [MAX] http(s) URLs from message text.
 *
 * Pure Kotlin (no Android APIs) so it can be unit-tested on the JVM.
 *
 * What counts as a link:
 *  - anything written with an explicit scheme: `https://…` / `http://…`
 *  - `www.…`
 *  - a bare domain **with a path**: `shop.example.com/item/1`
 *  - a bare domain without a path only when it ends in a common website suffix
 *    (`google.com`, `pchome.com.tw`, …)
 *
 * What does NOT count: user names that merely look like domains, e.g.
 * Threads / IG handles `sam.fu.tw`, `amy.lu`, `anny.0923`, and anything right after `@`.
 * The same address written as http:// and https:// (or with/without scheme) is listed once.
 */
object LinkExtractor {
    const val MAX = 3

    /** URL body characters (ASCII only, so CJK text right after a link is not swallowed). */
    private const val URL_CHARS = "[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]"
    private const val HOST = "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}"

    // Not `\b`: on Android (ICU) and Java 17 a CJK character counts as a word character, so
    // 「看這個https://…」 had no word boundary before "https" and the link was missed.
    private val SCHEME_RE = Regex("(?i)(?<![A-Za-z0-9])https?://$URL_CHARS+")
    private val WWW_RE = Regex("(?i)(?<![A-Za-z0-9@._/_-])www\\.$HOST(?::\\d{1,5})?(?:/$URL_CHARS*)?")
    private val BARE_RE = Regex("(?<![A-Za-z0-9@._/:_-])($HOST)(?::\\d{1,5})?(/$URL_CHARS*)?")

    /** Suffixes that make a bare `name.suffix` (no path) count as a website. */
    private val SITE_SUFFIXES = listOf(
        ".com", ".net", ".org", ".io", ".app", ".dev", ".ai", ".edu", ".gov",
        ".com.tw", ".org.tw", ".net.tw", ".gov.tw", ".edu.tw", ".idv.tw",
    )

    fun extract(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val found = ArrayList<Pair<IntRange, String>>()
        fun overlaps(r: IntRange) = found.any { it.first.first <= r.last && r.first <= it.first.last }

        for (m in SCHEME_RE.findAll(text)) {
            val raw = trimTail(m.value)
            if (raw.length > "https://".length) found += m.range to raw
        }
        for (m in WWW_RE.findAll(text)) {
            if (!overlaps(m.range)) found += m.range to trimTail(m.value)
        }
        for (m in BARE_RE.findAll(text)) {
            if (overlaps(m.range)) continue
            val host = m.groupValues[1]
            val path = trimTail(m.groupValues[2]).removePrefix("/")
            val hasPath = path.isNotEmpty()
            if (!hasPath && SITE_SUFFIXES.none { host.lowercase().endsWith(it) }) continue
            found += m.range to trimTail(m.value)
        }

        val out = LinkedHashMap<String, String>()   // dedupe key -> url
        for ((_, raw) in found.sortedBy { it.first.first }) {
            val url = normalize(raw) ?: continue
            val key = dedupeKey(url)
            if (key !in out) out[key] = url
            if (out.size >= MAX) break
        }
        return out.values.toList()
    }

    /** Drop trailing punctuation that is almost never part of a URL; keep balanced ')'. */
    private fun trimTail(s: String): String {
        var u = s
        while (u.isNotEmpty()) {
            val c = u.last()
            val drop = when (c) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '>', ']' -> true
                ')' -> u.count { it == '(' } < u.count { it == ')' }
                else -> false
            }
            if (!drop) break
            u = u.dropLast(1)
        }
        return u
    }

    private fun dedupeKey(url: String): String = try {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        val path = (uri.rawPath.orEmpty()).trimEnd('/')
        val q = uri.rawQuery?.let { "?$it" }.orEmpty()
        host + path + q
    } catch (_: Exception) {
        url.lowercase().substringAfter("://").removePrefix("www.").trimEnd('/')
    }

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var u = trimTail(raw.trim())
        if (u.isEmpty()) return null
        if (!u.contains("://")) u = "https://$u"
        return try {
            val uri = URI(u)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            uri.toString()
        } catch (_: Exception) {
            if (u.startsWith("http://") || u.startsWith("https://")) u else null
        }
    }

    /**
     * Short form of a link for display: "instagram.com/reel/…", "youtube.com/watch…", "example.com".
     * Tapping still uses the full URL; this only changes what is shown.
     */
    fun shortLabel(url: String): String = try {
        val u = URI(url)
        val host = u.host?.lowercase()?.removePrefix("www.")?.ifBlank { null } ?: throw IllegalArgumentException()
        val segs = u.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        val more = segs.size > 1 || !u.rawQuery.isNullOrEmpty() || !u.rawFragment.isNullOrEmpty()
        val first = segs.firstOrNull()?.let { if (it.length > 20) it.take(18) + "…" else it }
        when {
            first == null -> if (more) "$host/…" else host
            segs.size > 1 -> "$host/$first/…"
            more -> "$host/$first…"
            else -> "$host/$first"
        }
    } catch (_: Exception) {
        url
    }

    /** [text] with every written-out link (https://…, www.…) replaced by its [shortLabel]. */
    fun shortenInText(text: String): String {
        if (!text.contains("://") && !text.contains("www.", ignoreCase = true)) return text
        val found = ArrayList<Pair<IntRange, String>>()
        fun overlaps(r: IntRange) = found.any { it.first.first <= r.last && r.first <= it.first.last }
        for (m in SCHEME_RE.findAll(text)) found += m.range to m.value
        for (m in WWW_RE.findAll(text)) if (!overlaps(m.range)) found += m.range to m.value
        if (found.isEmpty()) return text
        val sb = StringBuilder()
        var pos = 0
        for ((range, raw) in found.sortedBy { it.first.first }) {
            val kept = trimTail(raw)                       // trailing punctuation stays as text
            val url = normalize(kept)
            val label = url?.let { shortLabel(it) }
            sb.append(text, pos, range.first)
            if (label != null && label.length < kept.length) sb.append(label) else sb.append(kept)
            sb.append(raw, kept.length, raw.length)
            pos = range.last + 1
        }
        sb.append(text, pos, text.length)
        return sb.toString()
    }

    fun hostLabel(url: String): String = try {
        URI(url).host?.removePrefix("www.")?.ifBlank { null } ?: url.take(28)
    } catch (_: Exception) {
        url.take(28)
    }
}

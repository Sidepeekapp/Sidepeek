package app.sidepeek

import java.text.Normalizer

/**
 * 進階清理: decides which tidied LINE chat is open from the texts in the top bar of a LINE screen.
 * It only compares against chat names Sidepeek already has (its own notifications); nothing is stored.
 * When unsure (no match, or more than one) it returns null and nothing is cleared.
 */
object ChatTitleMatch {

    private const val MIN_PREFIX = 4
    private val memberCount = Regex("""\s*[(（]\s*\d+\s*[)）]\s*$""")
    private val ellipsis = Regex("""(…|\.{3})\s*$""")

    /** Full/half width unified, spaces collapsed, group member count like 「(5)」 dropped. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ").trim()
            .replace(memberCount, "").trim()

    /**
     * @param screenTexts texts (and content descriptions) of the top bar of the LINE screen
     * @param chats chat key -> chat name, for the tidied LINE chats now in the notification shade
     * @return the one chat key the top bar names, or null when none or several match
     */
    fun match(screenTexts: List<String>, chats: Map<String, String>): String? {
        val names = chats.mapValues { normalize(it.value) }.filterValues { it.isNotEmpty() }
        val hits = mutableSetOf<String>()
        for (raw in screenTexts) {
            val t = normalize(raw)
            if (t.isEmpty()) continue
            val cut = ellipsis.containsMatchIn(t)
            val head = if (cut) normalize(t.replace(ellipsis, "")) else t
            for ((key, name) in names) {
                if (name == t || (cut && head.length >= MIN_PREFIX && name.startsWith(head))) hits += key
            }
        }
        return hits.singleOrNull()
    }
}

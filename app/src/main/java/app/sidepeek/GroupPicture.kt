package app.sidepeek

/**
 * LINE group chats: is the notification's large icon the chat's own picture or the latest sender's?
 * Ordinary groups put the sender there, communities (社群) put the community's picture there, and
 * the notification does not say which. Judge it from what we see over a few messages.
 *
 * Pictures are compared as small [THUMB]×[THUMB] ARGB thumbnails. LINE hands out the same picture
 * at different sizes and crops (a member's Person icon vs. the large icon), so two thumbnails count
 * as the same picture when they look alike ([diff] ≤ [SAME_MAX]), not only when every pixel matches.
 */
object GroupPicture {
    enum class Verdict { CHAT, SENDER, UNKNOWN }

    const val THUMB = 12
    /** Largest [diff] (average per-channel difference, 0..255) still taken as the same picture. */
    const val SAME_MAX = 28

    /**
     * Average per-channel difference of the centre of two thumbnails, or null when they cannot be
     * compared. Only the centre is used, so a circle-cropped copy matches its square original;
     * pixels that are transparent in either are skipped.
     */
    fun diff(a: IntArray, b: IntArray): Int? {
        if (a.size != THUMB * THUMB || b.size != a.size) return null
        var sum = 0L
        var n = 0
        for (y in 2 until THUMB - 2) for (x in 2 until THUMB - 2) {
            val p = a[y * THUMB + x]
            val q = b[y * THUMB + x]
            if ((p ushr 24) < 128 || (q ushr 24) < 128) continue
            sum += Math.abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) +
                Math.abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) +
                Math.abs((p and 0xFF) - (q and 0xFF))
            n++
        }
        return if (n < 16) null else (sum / (3L * n)).toInt()
    }

    fun same(a: IntArray, b: IntArray): Boolean = diff(a, b)?.let { it <= SAME_MAX } ?: a.contentEquals(b)

    /**
     * @param prevSender / prevIcon the sender and large-icon thumbnail of the previous message in this chat
     * @param personIcon thumbnail of the sender's own Person icon, when LINE gave one
     */
    fun judge(prevSender: String?, prevIcon: IntArray?, sender: String, icon: IntArray, personIcon: IntArray?): Verdict = when {
        // The sender's own picture came along: the large icon is either that same picture or something else.
        personIcon != null -> if (same(personIcon, icon)) Verdict.SENDER else Verdict.CHAT
        prevSender == null || prevIcon == null || prevSender == sender -> Verdict.UNKNOWN
        // Two different people, one picture: it belongs to the chat.
        same(prevIcon, icon) -> Verdict.CHAT
        else -> Verdict.SENDER
    }
}

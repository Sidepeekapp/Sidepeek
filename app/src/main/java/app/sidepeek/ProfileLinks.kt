package app.sidepeek

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Works out whose avatar was tapped and builds an intent to their profile page.
 * Only apps whose notifications carry the account handle can do this (Threads, Instagram);
 * for everything else this returns null and the caller opens the message instead.
 */
object ProfileLinks {
    private const val THREADS = "com.instagram.barcelona"
    private const val INSTAGRAM = "com.instagram.android"

    /** "chen__.90 說你的回覆讚…", "amy.lu and 3 others liked…", "@bob 在貼文中提及你" */
    private val LEADING = Regex(
        "^@?([A-Za-z0-9._]{1,30})(?:\\s*(?:、|,|，)\\s*@?[A-Za-z0-9._]{1,30})*\\s*" +
            "(?:說|和|及|回覆|提及|標註|開始追蹤|追蹤|分享|轉發|引用|留言|按讚|說讚|傳送|" +
            "liked|replied|mentioned|started|followed|commented|reposted|quoted|tagged|shared|and\\b)"
    )

    /**
     * Strict check for a title/sender used as a handle. Threads/IG titles are often the person's
     * *display name* ("Ash"), and guessing @ash opened a stranger's profile. Only accept names that
     * clearly look like a handle: all lower-case and containing '.', '_' or a digit (chen__.90).
     */
    private val STRICT_HANDLE = Regex("^(?=.*[._0-9])(?=.*[a-z])[a-z0-9._]{2,30}$")

    /**
     * The account handle behind [m], or null when we cannot tell for sure.
     * [others]: stored messages; a notification without the handle borrows it from another one
     * from the same app with the same avatar picture (avatar files are named by their content).
     */
    fun handleFor(m: LineMessage, others: List<LineMessage> = emptyList()): String? {
        if (m.pkg != THREADS && m.pkg != INSTAGRAM) return null
        LEADING.find(m.text.trim())?.let { return it.groupValues[1] }
        val av = m.avatarPath
        // Only content-named avatars ("a_…", see MessageStore.bitmapKey) identify a person; older
        // files were shared by everyone in the same chat title and would pick the wrong account.
        if (av != null && av.substringAfterLast('/').startsWith("a_")) {
            for (o in others) {
                if (o.id == m.id || o.pkg != m.pkg || o.avatarPath != av) continue
                LEADING.find(o.text.trim())?.let { return it.groupValues[1] }
            }
        }
        for (c in listOf(m.sender, m.chatTitle)) {
            val t = c.trim().removePrefix("@")
            if (STRICT_HANDLE.matches(t)) return t
        }
        return null
    }

    fun profileIntent(ctx: Context, m: LineMessage, others: List<LineMessage> = emptyList()): Intent? {
        val h = handleFor(m, others) ?: return null
        val (url, pkg) = when (m.pkg) {
            THREADS -> "https://www.threads.net/@$h" to THREADS
            else -> "https://www.instagram.com/$h/" to INSTAGRAM
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Sources.isInstalled(ctx, pkg)) i.setPackage(pkg)
        return i
    }
}

package app.sidepeek

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** One LINE message as captured from a notification. */
data class LineMessage(
    val id: String,
    val pkg: String,
    val chatKey: String,
    val chatTitle: String,
    val sender: String,
    val text: String,
    val time: Long,
    val imagePath: String? = null,
    val avatarPath: String? = null,
    /** Up to 3 http(s) URLs extracted from [text] at store time. */
    val urls: List<String> = emptyList(),
    /** How many times the app re-posted this same notification (e.g. bubble's 「有新消息。」). */
    val repeat: Int = 1,
)

/**
 * Keeps the last [MAX] messages in memory, persisted as JSON in filesDir.
 * PendingIntents (LINE's contentIntent) can't be persisted, so they are only kept
 * in memory per chat; after a process restart we fall back to launching LINE.
 */
object MessageStore {
    private const val MAX = 200
    private const val FILE = "messages.json"

    private val messages = ArrayList<LineMessage>()
    private val chatIntents = HashMap<String, PendingIntent>()
    /** Reply / mark-as-read actions copied from the latest LINE notification for a chat (in-memory). */
    private val chatActions = HashMap<String, List<androidx.core.app.NotificationCompat.Action>>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                messages.add(
                    LineMessage(
                        id = o.getString("id"),
                        pkg = o.optString("pkg", LineListenerService.LINE_PKG),
                        chatKey = o.getString("chatKey"),
                        chatTitle = o.optString("chatTitle"),
                        sender = o.optString("sender"),
                        text = o.optString("text"),
                        time = o.optLong("time"),
                        imagePath = o.optString("image").ifEmpty { null },
                        avatarPath = o.optString("avatar").ifEmpty { null },
                        // Recompute from text so older, wrongly-detected links (e.g. user names) disappear.
                        urls = LinkExtractor.extract(o.optString("text")),
                        repeat = o.optInt("repeat", 1).coerceAtLeast(1),
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun all(ctx: Context): List<LineMessage> {
        ensureLoaded(ctx); return ArrayList(messages)
    }

    @Synchronized
    fun forChat(ctx: Context, chatKey: String, limit: Int): List<LineMessage> {
        ensureLoaded(ctx)
        return messages.filter { it.chatKey == chatKey }.sortedBy { it.time }.takeLast(limit)
    }

    /** Messages of a chat received after it was last read (LINE cleared it / user opened ours). */
    @Synchronized
    fun unreadForChat(ctx: Context, chatKey: String, limit: Int): List<LineMessage> {
        ensureLoaded(ctx)
        val since = readMarks(ctx).getLong(chatKey, 0L)
        return messages.filter { it.chatKey == chatKey && it.time > since }.sortedBy { it.time }.takeLast(limit)
    }

    private fun readMarks(ctx: Context) = ctx.getSharedPreferences("read_marks", Context.MODE_PRIVATE)

    fun markRead(ctx: Context, chatKey: String) {
        val latest = synchronized(this) { messages.filter { it.chatKey == chatKey }.maxOfOrNull { it.time } ?: 0L }
        readMarks(ctx).edit().putLong(chatKey, maxOf(latest, System.currentTimeMillis())).apply()
    }

    /** Per-chat notification dismiss watermark: postChat only shows messages with time > this. Not markRead. */
    private fun notifWatermarks(ctx: Context) = ctx.getSharedPreferences("notif_watermarks", Context.MODE_PRIVATE)

    fun setNotifWatermark(ctx: Context, chatKey: String, time: Long) {
        notifWatermarks(ctx).edit().putLong(chatKey, time).apply()
    }

    fun notifWatermark(ctx: Context, chatKey: String): Long = notifWatermarks(ctx).getLong(chatKey, 0L)

    fun saveChatAvatar(ctx: Context, chatKey: String, bmp: Bitmap) {
        saveBitmap(ctx, "chatav", chatKey.hashCode().toUInt().toString(), bmp, 192)
    }

    /** LINE groups: what the saved chat avatar (LINE's large icon) shows, see [GroupPicture]. */
    private fun groupPictures(ctx: Context) = ctx.getSharedPreferences("group_pictures", Context.MODE_PRIVATE)

    fun groupPicture(ctx: Context, chatKey: String): GroupPicture.Verdict =
        groupPictures(ctx).getString(chatKey, null)
            ?.let { runCatching { GroupPicture.Verdict.valueOf(it) }.getOrNull() } ?: GroupPicture.Verdict.UNKNOWN

    fun setGroupPicture(ctx: Context, chatKey: String, v: GroupPicture.Verdict) {
        if (v != GroupPicture.Verdict.UNKNOWN && v != groupPicture(ctx, chatKey)) groupPictures(ctx).edit().putString(chatKey, v.name).apply()
    }

    fun chatAvatarPath(ctx: Context, chatKey: String): String? =
        File(File(ctx.filesDir, "chatav"), chatKey.hashCode().toUInt().toString() + ".png").takeIf { it.exists() }?.absolutePath

    @Synchronized
    fun contains(ctx: Context, id: String): Boolean {
        ensureLoaded(ctx); return messages.any { it.id == id }
    }

    @Synchronized
    fun intentFor(chatKey: String): PendingIntent? = chatIntents[chatKey]

    /** Each message's own tap target (the notification's contentIntent), in memory only. */
    private val msgIntents = object : LinkedHashMap<String, PendingIntent>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, PendingIntent>?) = size > 400
    }
    @Synchronized fun setMessageIntent(id: String, pi: PendingIntent?) { if (pi != null) msgIntents[id] = pi }
    @Synchronized fun intentForMessage(id: String): PendingIntent? = msgIntents[id]

    @Synchronized
    fun setIntent(chatKey: String, pi: PendingIntent?) {
        if (pi != null) chatIntents[chatKey] = pi
    }

    fun setLineActions(chatKey: String, actions: List<androidx.core.app.NotificationCompat.Action>) {
        if (actions.isEmpty()) return
        synchronized(this) { chatActions[chatKey] = actions }
    }

    fun lineActionsFor(chatKey: String): List<androidx.core.app.NotificationCompat.Action> =
        synchronized(this) { chatActions[chatKey].orEmpty() }

    private fun midOf(id: String): String? = id.substringAfter("|mid:", "").takeIf { it.isNotEmpty() }
    private fun norm(t: String) = t.trim().replace(Regex("\\s+"), " ")

    /**
     * Returns the reason if [msg] duplicates something already stored, else null.
     *  1. same LINE message id (line.message.id)   2. same internal id
     *  3. same chat + same text + same/blank sender within [windowMs]
     */
    @Synchronized
    fun duplicateReason(ctx: Context, msg: LineMessage, windowMs: Long = 10_000L): String? {
        ensureLoaded(ctx)
        val mid = midOf(msg.id)
        val t = norm(msg.text)
        for (m in messages.asReversed()) {
            if (m.id == msg.id) return "same id"
            val otherMid = midOf(m.id)
            if (mid != null && otherMid == mid && m.chatKey == msg.chatKey) return "same line.message.id"
            // Both carry LINE's own message id and the ids differ: two real messages, even when the
            // text is the same (the same long sentence sent twice must notify twice).
            if (mid != null && otherMid != null) continue
            // Same event posted twice by non-LINE apps under different titles, e.g. Threads
            // 「1 個新的讚」+「chen__.90 說你的回覆讚…」 and 「chen__.90」+「說你的回覆讚…」.
            if (m.pkg == msg.pkg && m.pkg != LineListenerService.LINE_PKG && kotlin.math.abs(m.time - msg.time) <= 15_000L) {
                val a = norm(m.text); val shorter = minOf(a.length, t.length)
                if (shorter >= 8 && (a.endsWith(t) || t.endsWith(a))) return "same event, different title"
            }
            if (m.chatKey == msg.chatKey && norm(m.text) == t &&
                (m.sender == msg.sender || m.sender.isBlank() || msg.sender.isBlank())) {
                val dt = kotlin.math.abs(m.time - msg.time)
                if (dt <= windowMs) return "same chat+sender+text within ${windowMs / 1000}s"
                // Long texts (announcements, official accounts) that LINE re-posts minutes later.
                if (t.length >= LONG_TEXT && dt <= LONG_WINDOW_MS) return "same long text within ${dt / 60000}min"
            }
        }
        return null
    }

    private const val LONG_TEXT = 20
    private const val LONG_WINDOW_MS = 30 * 60_000L

    /** Remove one message (swipe-to-delete in the panel). Files are kept until [purgeFiles] (undo). */
    fun remove(ctx: Context, id: String): LineMessage? {
        val removed = synchronized(this) {
            ensureLoaded(ctx)
            val i = messages.indexOfFirst { it.id == id }
            if (i < 0) null else messages.removeAt(i).also { save(ctx) }
        }
        if (removed != null) notifyListeners()
        return removed
    }

    /** Attach a sticker/image downloaded after the initial post (silent refresh). */
    fun updateImagePath(ctx: Context, id: String, path: String) {
        synchronized(this) {
            ensureLoaded(ctx)
            val i = messages.indexOfFirst { it.id == id }
            if (i < 0) return
            val old = messages[i]
            if (old.imagePath == path) return
            messages[i] = old.copy(imagePath = path)
            save(ctx)
        }
        notifyListeners()
    }

    /** Apply a newly discovered authoritative conversation title to existing cards. */
    fun updateChatTitle(ctx: Context, chatKey: String, title: String) {
        if (title.isBlank()) return
        val changed = synchronized(this) {
            ensureLoaded(ctx)
            var any = false
            for (i in messages.indices) {
                val old = messages[i]
                if (old.chatKey == chatKey && old.chatTitle != title) {
                    messages[i] = old.copy(chatTitle = title)
                    any = true
                }
            }
            if (any) save(ctx)
            any
        }
        if (changed) notifyListeners()
    }

    /** Put a removed message back (undo). Skips dedupe. */
    fun restore(ctx: Context, msg: LineMessage) {
        synchronized(this) {
            ensureLoaded(ctx)
            if (messages.any { it.id == msg.id }) return
            messages.add(msg); messages.sortBy { it.time }; save(ctx)
        }
        notifyListeners()
    }

    /** Newest stored card of a chat, or null. */
    @Synchronized
    fun latestInChat(ctx: Context, chatKey: String): LineMessage? {
        ensureLoaded(ctx)
        return messages.filter { it.chatKey == chatKey }.maxByOrNull { it.time }
    }

    /**
     * The app re-posted the same notification again (same content, new alert): instead of a
     * duplicate card, move the chat's newest card to [time] and count it.
     * @return true if a card was updated
     */
    @Synchronized
    fun bumpRepeat(ctx: Context, chatKey: String, time: Long): Boolean {
        ensureLoaded(ctx)
        val idx = messages.indices.filter { messages[it].chatKey == chatKey }.maxByOrNull { messages[it].time } ?: return false
        val m = messages[idx]
        if (time <= m.time) return false
        messages[idx] = m.copy(time = time, repeat = m.repeat + 1)
        messages.sortBy { it.time }
        save(ctx)
        notifyListeners()
        return true
    }

    /** Newest stored message time for a chat (0 if none). */
    @Synchronized

    fun lastTimeForChat(ctx: Context, chatKey: String): Long {
        ensureLoaded(ctx); return messages.filter { it.chatKey == chatKey }.maxOfOrNull { it.time } ?: 0L
    }

    /** Adds [msg] unless it is a duplicate. Returns null when added, else the reason it was skipped. */
    fun add(ctx: Context, msg: LineMessage): String? {
        synchronized(this) {
            ensureLoaded(ctx)
            duplicateReason(ctx, msg)?.let { return it }
            messages.add(msg)
            messages.sortBy { it.time }
            while (messages.size > MAX) {
                val old = messages.removeAt(0)
                old.imagePath?.let { p -> if (messages.none { it.imagePath == p }) File(p).delete() }
                old.avatarPath?.let { p -> if (messages.none { it.avatarPath == p }) File(p).delete() }
            }
            save(ctx)
        }
        notifyListeners()
        return null
    }

    fun clear(ctx: Context) {
        purgeFiles(ctx, removeAll(ctx))
    }

    /**
     * 「清除」 step 1: drop every message but keep their files, so [restoreAll] can undo it.
     * @return the removed messages
     */
    fun removeAll(ctx: Context): List<LineMessage> {
        val removed = synchronized(this) {
            ensureLoaded(ctx)
            val all = ArrayList(messages)
            messages.clear()
            save(ctx)
            all
        }
        if (removed.isNotEmpty()) notifyListeners()
        return removed
    }

    /** Undo of [removeAll]: put the messages back (skipping any id that exists again). */
    fun restoreAll(ctx: Context, msgs: List<LineMessage>) {
        synchronized(this) {
            ensureLoaded(ctx)
            val ids = messages.mapTo(HashSet()) { it.id }
            msgs.filterTo(messages) { it.id !in ids }
            messages.sortBy { it.time }
            while (messages.size > MAX) messages.removeAt(0)
            save(ctx)
        }
        notifyListeners()
    }

    /**
     * Delete the image / avatar files of [removed] messages that no stored message still uses,
     * and the chat header pictures of chats that have no message left.
     */
    fun purgeFiles(ctx: Context, removed: List<LineMessage>) {
        synchronized(this) {
            val usedImg = messages.mapNotNullTo(HashSet()) { it.imagePath }
            val usedAv = messages.mapNotNullTo(HashSet()) { it.avatarPath }
            val chats = messages.mapTo(HashSet()) { it.chatKey }
            for (m in removed) {
                m.imagePath?.let { if (it !in usedImg) File(it).delete() }
                m.avatarPath?.let { if (it !in usedAv) File(it).delete() }
                if (m.chatKey !in chats) chatAvatarPath(ctx, m.chatKey)?.let { File(it).delete() }
            }
        }
    }

    private fun save(ctx: Context) {
        try {
            val arr = JSONArray()
            for (m in messages) {
                arr.put(JSONObject().apply {
                    put("id", m.id); put("pkg", m.pkg); put("chatKey", m.chatKey)
                    put("chatTitle", m.chatTitle); put("sender", m.sender); put("text", m.text)
                    put("time", m.time); put("image", m.imagePath ?: ""); put("avatar", m.avatarPath ?: "")
                    put("urls", JSONArray(m.urls)); put("repeat", m.repeat)
                })
            }
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(File(ctx.filesDir, FILE))
        } catch (_: Exception) {
        }
    }

    fun addListener(l: () -> Unit) = synchronized(listeners) { listeners.add(l) }
    fun removeListener(l: () -> Unit) = synchronized(listeners) { listeners.remove(l) }
    private fun notifyListeners() {
        val copy = synchronized(listeners) { listeners.toList() }
        main.post { copy.forEach { it() } }
    }

    /** Save a bitmap (scaled down) into filesDir/<dir>/<name>.png and return its path. */
    fun saveBitmap(ctx: Context, dir: String, name: String, bmp: Bitmap, maxSize: Int): String? = try {
        val d = File(ctx.filesDir, dir).apply { mkdirs() }
        val f = File(d, "$name.png")
        val scale = minOf(1f, maxSize.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
        ) else bmp
        FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 90, it) }
        f.absolutePath
    } catch (_: Exception) {
        null
    }

    /** Stable file name from a bitmap's content (same picture → same name, different → different). */
    fun bitmapKey(bmp: Bitmap): String = try {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        if (small !== bmp) small.recycle()
        val crc = java.util.zip.CRC32()
        for (p in px) { crc.update(p ushr 24); crc.update(p ushr 16); crc.update(p ushr 8); crc.update(p) }
        "a_" + java.lang.Long.toHexString(crc.value) + "_" + bmp.width + "x" + bmp.height
    } catch (_: Exception) {
        "a_" + System.nanoTime()
    }

    /**
     * Decoded avatars / images, so the side panel and notifications do not decode the same files
     * from disk again on every refresh. Keyed by path + modification time + size, so a file that
     * is rewritten (sticker downloaded later) or deleted is never served stale.
     */
    private val bitmapCache = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun loadBitmap(path: String?): Bitmap? = try {
        if (path == null) null else {
            val f = File(path)
            val modified = f.lastModified()          // 0 when the file does not exist
            if (modified == 0L) null else {
                val key = "$path|$modified|${f.length()}"
                bitmapCache.get(key) ?: BitmapFactory.decodeFile(path)?.also { bitmapCache.put(key, it) }
            }
        }
    } catch (_: Exception) {
        null
    }
}

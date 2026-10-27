package app.sidepeek

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

class LineListenerService : NotificationListenerService() {

    companion object {
        const val TAG = "Sidepeek"
        const val LINE_PKG = "jp.naver.line.android"
        const val LINE_LITE_PKG = "com.linecorp.linelite"

        /** Categories that are never chat messages (calls, meetings, uploads, system status...). */
        private val SKIP_CATEGORIES = setOf(
            Notification.CATEGORY_CALL, Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS,
            Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_ERROR,
            "missed_call", "stopwatch", "location_sharing",
        )

        @Volatile var connected = false
    }

    /** Parse / post / hide — never runs sticker CDN downloads (those use [stickerPool]). */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Sticker CDN downloads; failures / timeouts must not block [worker]. */
    private val stickerPool = java.util.concurrent.Executors.newFixedThreadPool(2)

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** chatKey -> pending "LINE cleared this chat" reset (cancelled if a new message arrives quickly). */
    private val pendingResets = HashMap<String, Runnable>()
    /** Periodic rebind + orphan recovery while the listener is supposed to be alive. */
    private val healthRunnable = object : Runnable {
        override fun run() {
            try {
                if (!connected) {
                    Diagnostics.note(this@LineListenerService, "LISTENER health: not connected -> requestRebind")
                    try {
                        requestRebind(ComponentName(this@LineListenerService, LineListenerService::class.java))
                    } catch (e: Exception) { Diagnostics.fail(this@LineListenerService, "LISTENER requestRebind", e) }
                } else if (Prefs.enabled(this@LineListenerService)) {
                    worker.execute { recoverOrphans("health-30s") }
                }
            } finally {
                main.postDelayed(this, 30_000L)
            }
        }
    }

    /** Short-term dedupe for posts that don't carry a stable MessagingStyle timestamp. */
    private val recent = LinkedHashMap<String, Long>()
    /** sbn.key -> content fingerprint of the last post we processed (skip identical updates). */
    private val lastContent = object : LinkedHashMap<String, Int>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Int>?) = size > 300
    }
    /** sbn.key -> postTime last accepted (LINE key + postTime dedupe). */
    private val lastKeyPostTime = object : LinkedHashMap<String, Long>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Long>?) = size > 300
    }
    /** sbn.key -> normalized texts already taken from that notification (re-posts at any time = dupes). */
    private val keyTexts = object : LinkedHashMap<String, MutableSet<String>>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, MutableSet<String>>?) = size > 300
    }
    private fun seenOnKey(key: String, text: String): Boolean {
        val t = text.trim().replace(Regex("\\s+"), " ")
        // Short texts ("ok", "？") may be repeated on purpose -> only the 10s rule applies to them.
        if (t.substringAfter('|').length < 20) return false
        return !keyTexts.getOrPut(key) { HashSet() }.add(t)
    }
    /** chatKey -> newest MessagingStyle timestamp already processed. */
    private val lastMsTime = HashMap<String, Long>()

    private fun dedupeLog(msg: String) = Diagnostics.note(this, "DEDUPE $msg")

    /** notification key -> postTime of the previous post (non-LINE apps), to spot re-posts. */
    private val lastPostByKey = HashMap<String, Long>()
    /** notification key -> `when` of the previous post (non-LINE apps). */
    private val lastWhenByKey = HashMap<String, Long>()
    /** notification key -> "sender|text" of the card it last produced (non-LINE apps). */
    private val lastTextByKey = HashMap<String, String>()

    override fun onListenerConnected() {
        connected = true
        Notifier.ensureChannels(this)
        MessageStore.ensureLoaded(this)
        Diagnostics.note(this, "LISTENER connected")
        // LINE notifications posted while we were disconnected / rebinding were never hidden.
        scheduleSweep("connect", 200)
        scheduleSweep("connect+3s", 3000)
        main.removeCallbacks(healthRunnable)
        main.postDelayed(healthRunnable, 30_000L)
        worker.execute { recoverOrphans("connect") }
    }

    override fun onListenerDisconnected() {
        connected = false
        Diagnostics.note(this, "LISTENER disconnected -> requestRebind")
        main.removeCallbacks(healthRunnable)
        try {
            requestRebind(ComponentName(this, LineListenerService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed", e)
            Diagnostics.fail(this, "LISTENER requestRebind", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == LINE_PKG || sbn.packageName == LINE_LITE_PKG) {
            try { observeLineChannel(sbn, rankingMap) } catch (e: Throwable) {
                Diagnostics.fail(applicationContext, "LINE channel observe", e)
            }
        }
        // Default framework path still hits the single-arg overload for processing.
        onNotificationPosted(sbn)
    }

    /** Cache LINE channel id + importance so health UI can warn when heads-up is still possible. */
    private fun observeLineChannel(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (Build.VERSION.SDK_INT < 26) return
        val ranking = Ranking()
        if (!rankingMap.getRanking(sbn.key, ranking)) return
        val chId = ranking.channel?.id ?: sbn.notification.channelId
        val imp = ranking.channel?.importance
            ?: ranking.importance
        Prefs.setLineChannel(applicationContext, chId, imp)
        // Auto-clear manual checklist when we observe a muted-enough channel.
        if (imp < NotificationManager.IMPORTANCE_DEFAULT && Prefs.lineMuteDone(applicationContext).not()) {
            // leave manual flag alone; health page prefers live Ranking when known
        }
        Diagnostics.note(applicationContext, "LINE ch=$chId imp=$imp headsUp=${imp >= NotificationManager.IMPORTANCE_DEFAULT}")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return   // never our own
        val app = applicationContext
        if (!Sources.isTracked(app, sbn.packageName)) return
        val src = Sources.forPackage(app, sbn.packageName) ?: return
        val line = src == Sources.LINE
        // Cancel ASAP on the binder thread BEFORE any sticker download / dump work queues up.
        // Heads-up can appear in <200ms; waiting on the single worker lets raw LINE flash (or stick).
        if (line && hideWanted()) {
            try { hideOriginal(sbn, "asap") } catch (e: Throwable) {
                Diagnostics.fail(app, "HIDE asap", e)
            }
        }
        worker.execute {
            try {
                Diagnostics.record(app, sbn)
                // LINE: processed when reposting is on OR LINE is a side-panel source.
                // Other apps: side panel only, never reposted / hidden.
                if (line && !Prefs.enabled(app) && !Sources.isEnabled(app, src)) return@execute
                if (!line && !Sources.isEnabled(app, src)) return@execute
                if (line) {
                    processLine(sbn, "post", silent = false)
                    if (hideWanted()) {
                        scheduleSweep("post+500ms", 500)
                        scheduleSweep("post+2s", 2000)
                        scheduleSweep("post+8s", 8000)
                    }
                } else {
                    handle(sbn)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "handle failed", e)
                Diagnostics.fail(app, "POST/handle", e)
                if (line && hideWanted()) try { hideOriginal(sbn, "post-fail") } catch (_: Throwable) {}
            }
        }
    }

    // ------------------------------------------------------------------ hiding LINE's originals
    /**
     * Hide LINE's original only when our own notification can be shown. If Sidepeek may not post
     * (permission denied, app notifications or the 「LINE 訊息預覽」 channel turned off), LINE's
     * notification stays so the user is still alerted. Leftovers are picked up by the sweep /
     * 30s health check once posting works again.
     */
    private fun hideWanted(): Boolean {
        if (!Prefs.enabled(this) || !Prefs.hideOriginal(this)) return false
        val ok = Notifier.canRepost(this)
        if (ok != lastCanRepost) {
            lastCanRepost = ok
            Diagnostics.note(this, if (ok) "HIDE resumed: Sidepeek can post again"
                else "HIDE paused: Sidepeek cannot post (permission/channel off) -> keep LINE originals")
        }
        return ok
    }
    @Volatile private var lastCanRepost: Boolean? = null

    /** sbn.key -> time we last cancelled/snoozed it (to detect cancels that did not stick). */
    private val hiddenAt = object : LinkedHashMap<String, Long>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Long>?) = size > 300
    }
    private val sweepRunnables = HashMap<String, Runnable>()

    /** Why a LINE notification must NOT be hidden (calls, ongoing), or null if it may. */
    private fun keepReason(sbn: StatusBarNotification): String? {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return "ongoing"
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return "foreground-service"
        if (n.category in SKIP_CATEGORIES) return "category=${n.category}"
        if (n.fullScreenIntent != null) return "full-screen (call)"
        if (n.flags and Notification.FLAG_INSISTENT != 0) return "insistent"
        // Group summaries and OA / channel / 1:1 chats: hide. Official accounts (e.g. NCDR)
        // often lack CATEGORY_MESSAGE — still hide if they carry any title/text/MessagingStyle.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras
        val hasText = !ex.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
            !ex.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank() ||
            !ex.getCharSequence(Notification.EXTRA_BIG_TEXT).isNullOrBlank()
        val isMessage = n.category == Notification.CATEGORY_MESSAGE ||
            n.category == null || n.category == Notification.CATEGORY_SOCIAL ||
            ex.containsKey(Notification.EXTRA_MESSAGES) || ex.containsKey("line.chat.id") ||
            ex.containsKey("line.message.id") || ex.containsKey("line.sticker.url") ||
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) != null ||
            hasText
        return if (isMessage) null else "not a chat message (category=${n.category})"
    }

    private fun flagsOf(sbn: StatusBarNotification) =
        "flags=0x${Integer.toHexString(sbn.notification.flags)} summary=${sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0}"

    /**
     * Mirror (dedupe-aware) and then ALWAYS hide an eligible LINE notification, whatever path
     * handle() took (dedupe hit, empty text, sticker failure, exception...).
     * Stickers are enriched AFTER post (see handle); hide must never wait on the network.
     */
    private fun processLine(sbn: StatusBarNotification, why: String, silent: Boolean) {
        // Re-assert hide before heavy parse (asap may have raced with LINE's own update).
        if (hideWanted()) try { hideOriginal(sbn, "$why/pre") } catch (e: Throwable) {
            Diagnostics.fail(this, "HIDE pre", e)
        }
        try {
            handle(sbn, silent)
        } catch (e: Throwable) {
            Log.e(TAG, "handle failed", e)
            Diagnostics.fail(this, "handle ($why)", e)
        } finally {
            if (hideWanted()) hideOriginal(sbn, why)
            else Diagnostics.note(this, "HIDE off ($why) key=${sbn.key} ${flagsOf(sbn)}")
        }
    }

    private fun hideOriginal(sbn: StatusBarNotification, why: String) {
        keepReason(sbn)?.let { Diagnostics.note(this, "HIDE skip ($why) key=${sbn.key} ${flagsOf(sbn)}: $it"); return }
        // hiddenAt is touched from the main thread (asap hide) and the worker: access-ordered
        // LinkedHashMap.get() also modifies it, so every access is synchronized.
        val prev = synchronized(hiddenAt) { hiddenAt[sbn.key] }
        // Still active although we already removed this very post => cancel did not stick -> snooze.
        val stuck = prev != null && sbn.postTime <= prev
        if (stuck) {
            val still = try { getActiveNotifications(arrayOf(sbn.key))?.isNotEmpty() == true } catch (_: Exception) { true }
            if (!still) { Diagnostics.note(this, "HIDE already gone ($why) key=${sbn.key}"); return }
        }
        val method = try {
            if (stuck) { snoozeNotification(sbn.key, 24 * 3600_000L); "snooze 24h (cancel did not stick)" }
            else { cancelNotification(sbn.key); "cancel" }
        } catch (e: Exception) {
            try { cancelNotification(sbn.key); "cancel (snooze failed: ${e.javaClass.simpleName})" }
            catch (e2: Exception) { "FAILED: $e2" }
        }
        synchronized(hiddenAt) { hiddenAt[sbn.key] = System.currentTimeMillis() }
        Diagnostics.note(this, "HIDE $method ($why) key=${sbn.key} ${flagsOf(sbn)}")
    }

    /** Re-check the shade for LINE message notifications that are still visible. */
    private fun scheduleSweep(tag: String, delayMs: Long) {
        main.post {
            sweepRunnables.remove(tag)?.let { main.removeCallbacks(it) }   // coalesce bursts
            val r = Runnable { sweepRunnables.remove(tag); worker.execute { sweep(tag) } }
            sweepRunnables[tag] = r
            main.postDelayed(r, delayMs)
        }
    }

    private fun sweep(tag: String) {
        if (!hideWanted() || !connected) return
        val active = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) {
            Diagnostics.note(this, "SWEEP $tag failed: $e"); return
        }
        val line = active.filter { isLinePkg(it.packageName) }
        var hidden = 0; var kept = 0
        for (sbn in line) {
            if (keepReason(sbn) != null) { kept++; continue }
            hidden++
            // Silent: these are leftovers / posts missed while disconnected, not fresh alerts.
            processLine(sbn, "sweep:$tag", silent = true)
        }
        if (hidden > 0 || tag.startsWith("connect"))
            Diagnostics.note(this, "SWEEP $tag: LINE active=${line.size} leftover-hidden=$hidden kept=$kept")
    }

    /**
     * If the shade still has hideable LINE messages, or we have unread chats with no matching
     * preview notification, recover: mirror + hide.
     */
    private fun recoverOrphans(tag: String) {
        if (!Prefs.enabled(this) || !connected) return
        try {
            sweep(tag)
            if (!Prefs.enabled(this)) return
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
            val ourKeys = try {
                nm.activeNotifications.mapNotNull { it.notification.extras.getString(Notifier.EXTRA_CHAT_KEY) }.toSet()
            } catch (_: Exception) { emptySet() }
            val unreadChats = MessageStore.all(this)
                .filter { it.pkg == LINE_PKG || it.pkg == LINE_LITE_PKG }
                .groupBy { it.chatKey }
            var recovered = 0
            for ((chatKey, msgs) in unreadChats) {
                if (chatKey in ourKeys) continue
                // Only recover chats that still have unread (not mark-read).
                val unread = MessageStore.unreadForChat(this, chatKey, 50)
                if (unread.isEmpty()) continue
                val latest = unread.maxBy { it.time }
                val group = unread.any { it.sender.isNotBlank() && it.sender != it.chatTitle }
                try {
                    Notifier.postChat(this, latest, group, silent = true)
                    recovered++
                } catch (e: Throwable) {
                    Diagnostics.fail(this, "recover post chat=$chatKey", e)
                }
            }
            if (recovered > 0) Diagnostics.note(this, "RECOVER $tag: re-posted $recovered chat preview(s)")
        } catch (e: Throwable) {
            Diagnostics.fail(this, "recoverOrphans $tag", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // Our own stacked notification was tapped or swiped away -> that chat counts as read.
        if (sbn.packageName == packageName) {
            val key = sbn.notification.extras.getString(Notifier.EXTRA_CHAT_KEY) ?: return
            if (reason == REASON_CLICK || reason == REASON_CANCEL || reason == REASON_CANCEL_ALL) {
                MessageStore.markRead(this, key)
            }
            return
        }
        if (!Sources.isTracked(this, sbn.packageName)) return
        if (reason != REASON_APP_CANCEL) return
        worker.execute { Diagnostics.record(applicationContext, sbn, "REMOVED_BY_APP reason=$reason") }
        if (Sources.forPackage(sbn.packageName) != Sources.LINE) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // LINE cleared its notification (chat read in LINE / on PC): clear our stacked copy and
        // reset the accumulated lines. Delay slightly in case LINE is only cancel+reposting.
        val key = chatKeyOf(sbn, NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n), n.extras)
        pendingResets.remove(key)?.let { main.removeCallbacks(it) }
        val r = Runnable {
            pendingResets.remove(key)
            MessageStore.markRead(this, key)
            Notifier.cancelChat(this, key)
        }
        pendingResets[key] = r
        main.postDelayed(r, 2000)
    }

    private fun chatKeyOf(sbn: StatusBarNotification, ms: NotificationCompat.MessagingStyle?, ex: Bundle): String {
        val n = sbn.notification
        val raw = (if (sbn.packageName == LINE_PKG || sbn.packageName == LINE_LITE_PKG)
            n.extras.getString("line.chat.id")?.takeIf { it.isNotBlank() } else null)
            ?: n.shortcutId?.takeIf { it.isNotBlank() }
            ?: ms?.conversationTitle?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: sbn.tag
            ?: sbn.id.toString()
        return "${sbn.packageName}|$raw"
    }

    private fun handle(sbn: StatusBarNotification, silent: Boolean = false) {
        val n = sbn.notification
        // LINE's "stack" header: never reposted (hidden by processLine's finally if wanted).
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (n.category in SKIP_CATEGORIES) return
        // Incoming calls / meeting-started alerts use a full-screen intent or are insistent.
        if (n.fullScreenIntent != null) return
        if (n.flags and Notification.FLAG_INSISTENT != 0) return

        val ex = n.extras
        // Downloads, uploads, media playback and similar progress notifications are never messages
        // (matters now that users can pick any app as a side-panel source).
        if (ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) return
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        val ms = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val chatKey = chatKeyOf(sbn, ms, ex)

        // Same notification key re-posted with identical content (LINE updates / re-alerts) -> skip.
        val fingerprint = listOf(
            ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_TEXT),
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT), ex.getString("line.message.id"),
            ms?.messages?.joinToString("\u0001") { "${it.timestamp}|${it.person?.name}|${it.text}" }
        ).joinToString("\u0002") { it?.toString() ?: "" }.hashCode()
        // Non-LINE apps that re-post the SAME text for every new message (bubble: 「有新消息。」)
        // give each real new message a fresh `when`; plain refreshes keep the old one.
        val isLinePkg = sbn.packageName == LINE_PKG || sbn.packageName == LINE_LITE_PKG
        val prevWhen = if (isLinePkg || n.`when` <= 0) null else lastWhenByKey.put(sbn.key, n.`when`)
        if (lastContent[sbn.key] == fingerprint) {
            dedupeLog("skip unchanged update key=${sbn.key}")
            if (prevWhen != null && n.`when` - prevWhen >= 5_000L &&
                MessageStore.bumpRepeat(this, chatKey, n.`when`)) dedupeLog("repeat +1 (new when) chat=$chatKey")
            return
        }
        if (lastKeyPostTime[sbn.key] == sbn.postTime && lastContent.containsKey(sbn.key)) {
            dedupeLog("skip same key+postTime key=${sbn.key} postTime=${sbn.postTime}"); return
        }
        lastContent[sbn.key] = fingerprint
        lastKeyPostTime[sbn.key] = sbn.postTime
        val convTitle = ms?.conversationTitle?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val isGroup = (ms?.isGroupConversation == true) || !convTitle.isNullOrBlank()
        val chatTitle = (convTitle ?: title ?: Sources.displayName(this, sbn.packageName)).trim()

        MessageStore.setIntent(chatKey, n.contentIntent)
        try { MessageStore.setLineActions(chatKey, extractLineActions(n)) } catch (e: Throwable) {
            Diagnostics.fail(this, "capture LINE actions", e)
        }

        // Images carried by the notification itself (BigPictureStyle) — local, no network.
        val notifPicture: Bitmap? = extractNotificationPicture(ex)
        val notifLargeIcon: Bitmap? = n.getLargeIcon()?.let { iconToBitmap(it) }
        // LINE stickers: defer CDN download until AFTER we post + hide (never block the shade).
        val isLine = sbn.packageName == LINE_PKG || sbn.packageName == LINE_LITE_PKG
        val notifSticker = if (isLine) StickerLoader.discover(ex) else StickerLoader.Discovery(emptyList(), emptyList(), null, null, null)
        if (isLine && (notifSticker.candidates.isNotEmpty() || notifSticker.stickerKeys.isNotEmpty())) {
            Diagnostics.note(this, "sticker-discover notif keys=${notifSticker.stickerKeys} id=${notifSticker.stkId} pkg=${notifSticker.stkPkgId} ver=${notifSticker.stkVer} candidates=${notifSticker.candidates.size}")
        }
        // msgId -> candidate CDN URLs to try after post
        val pendingStickerByMsgId = LinkedHashMap<String, List<String>>()

        val newMessages = ArrayList<LineMessage>()

        // Some apps (e.g. bubble: 「有新消息。」) re-post the SAME notification for every new
        // message. Nothing new to show, but count it and move the card to the new time.
        val prevPost = if (isLine) null else lastPostByKey.put(sbn.key, sbn.postTime)
        fun repeatIfReposted() {
            if (isLine || prevPost == null) return
            // bubble re-posts with FLAG_ONLY_ALERT_ONCE and the old `when`, so neither can tell a
            // refresh from a new message; only re-posts seconds apart are treated as the same event.
            if (sbn.postTime - prevPost < 5_000L) return
            if (MessageStore.bumpRepeat(this, chatKey, sbn.postTime)) dedupeLog("repeat +1 chat=$chatKey")
        }

        val msgs = ms?.messages.orEmpty()
        val lineMsgId = if (isLine) ex.getString("line.message.id")?.takeIf { it.isNotBlank() } else null
        if (msgs.isNotEmpty()) {
            // Only messages newer than what we've already processed for this chat.
            val seenUntil = lastMsTime[chatKey] ?: MessageStore.lastTimeForChat(this, chatKey)
            for ((idx, m) in msgs.withIndex()) {
                val sender = m.person?.name?.toString() ?: ms?.user?.name?.toString()?.takeIf { it.isNotBlank() } ?: "我"
                var text = m.text?.toString()?.trim().orEmpty()
                val isLast = idx == msgs.lastIndex
                if (m.timestamp in 1..seenUntil) {
                    dedupeLog("skip older MessagingStyle msg chat=$chatKey ts=${m.timestamp} <= $seenUntil")
                    continue
                }
                // LINE's line.message.id describes the newest message of the post.
                val id = if (isLast && lineMsgId != null) "$chatKey|mid:$lineMsgId"
                         else idOf(chatKey, sender, text, m.timestamp, m.dataUri)
                if (MessageStore.contains(this, id) || isRecentDupe(id)) { dedupeLog("skip seen id=$id"); continue }
                if (text.isNotEmpty() && seenOnKey(sbn.key, "$sender|$text")) { dedupeLog("skip re-post on same key=${sbn.key}"); continue }

                var image: Bitmap? = null
                val mime = m.dataMimeType
                val uri = m.dataUri
                if (uri != null) {
                    val tryImage = mime == null || mime.startsWith("image/") || mime == "application/octet-stream"
                    if (tryImage) {
                        image = try { loadUri(uri) } catch (se: SecurityException) {
                            Diagnostics.note(this, "dataUri SecurityException id=$id: $se"); null
                        } catch (e: Throwable) {
                            Diagnostics.note(this, "dataUri open fail id=$id: $e"); null
                        }
                    }
                }
                // Discover sticker URLs from message extras + (for last) notification extras.
                // Queue enrich even when LINE already put「傳送了貼圖」as the text.
                val msgDisc = if (image == null && isLine) StickerLoader.discover(m.extras, if (isLast) ex else null) else null
                val candidates = (msgDisc?.candidates.orEmpty()).ifEmpty {
                    if (isLast) notifSticker.candidates else emptyList()
                }
                val stickerish = image == null && isLine && (
                    candidates.isNotEmpty() || StickerLoader.looksLikeStickerText(text) ||
                        (msgDisc?.stickerKeys?.isNotEmpty() == true)
                )
                if (image == null && isLast) image = notifPicture
                if (text.isEmpty() && image == null) {
                    text = if (stickerish) "傳送了貼圖" else "（傳送了一則訊息）"
                }
                if (text.isEmpty() && image != null) text = "（圖片）"
                if (stickerish) {
                    if (candidates.isEmpty()) {
                        Diagnostics.note(this, "sticker-candidate EMPTY id=$id" + Diagnostics.sensitive(this, " text=${text.take(20)}") + " keys=${msgDisc?.stickerKeys ?: notifSticker.stickerKeys}")
                        if (Diagnostics.full(this)) Diagnostics.note(this, "sticker-dump " + StickerLoader.dumpStickerExtras(m.extras, if (isLast) ex else null))
                    }
                    // Always queue when sticker-like; empty list still logged in enrich.
                    pendingStickerByMsgId[id] = candidates
                }

                val avatar = m.person?.let { personAvatar(it) } ?: notifLargeIcon
                newMessages += build(id, sbn.packageName, chatKey, chatTitle, sender, text,
                    if (m.timestamp > 0) m.timestamp else sbn.postTime, image, avatar)
            }
            msgs.maxOfOrNull { it.timestamp }?.let { t -> if (t > (lastMsTime[chatKey] ?: 0L)) lastMsTime[chatKey] = t }
        } else {
            var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            val picture = notifPicture
            val stickerish = isLine && picture == null && (
                notifSticker.candidates.isNotEmpty() || StickerLoader.looksLikeStickerText(text) ||
                    notifSticker.stickerKeys.isNotEmpty()
            )
            if (text.isEmpty() && picture == null && !stickerish) return
            if (text.isEmpty() && picture == null && stickerish) text = "傳送了貼圖"
            val sender = title ?: chatTitle
            val time = if (n.`when` > 0) n.`when` else sbn.postTime
            val id = if (lineMsgId != null) "$chatKey|mid:$lineMsgId" else idOf(chatKey, sender, text, time, null)
            if (MessageStore.contains(this, id) || isRecentDupe(id)) { dedupeLog("skip seen id=$id"); repeatIfReposted(); return }
            if (text.isNotEmpty() && seenOnKey(sbn.key, "$sender|$text")) { dedupeLog("skip re-post on same key=${sbn.key}"); repeatIfReposted(); return }
            // Same text re-posted in the same chat within a few seconds => treat as duplicate.
            val softId = idOf(chatKey, sender, text, 0, null)
            if (isRecentDupe(softId, windowMs = 10_000)) { dedupeLog("skip same text <10s chat=$chatKey"); repeatIfReposted(); return }
            if (stickerish) {
                pendingStickerByMsgId[id] = notifSticker.candidates
                if (notifSticker.candidates.isEmpty()) {
                    if (Diagnostics.full(this)) Diagnostics.note(this, "sticker-dump " + StickerLoader.dumpStickerExtras(ex))
                }
            }
            newMessages += build(id, sbn.packageName, chatKey, chatTitle, sender,
                text.ifEmpty { "（圖片）" }, time, picture, notifLargeIcon)
        }

        // Final, store-level dedupe (line.message.id / id / chat+sender+text within 10s).
        // Remember each message's own tap target (Threads: the liked post, Messenger: the chat…).
        newMessages.forEach { MessageStore.setMessageIntent(it.id, n.contentIntent) }
        // Same notification slot, same sender + text as the card it made last time, but a new
        // timestamp (bubble sometimes bumps `when`): count it on that card, don't add a twin.
        // Checked against the stored card (not memory), so it also works right after a restart.
        if (!isLine && newMessages.size == 1) {
            val m = newMessages[0]
            val last = MessageStore.latestInChat(this, chatKey)
            if (last != null && last.sender == m.sender && last.text.trim() == m.text.trim() &&
                m.imagePath == null && maxOf(m.time, sbn.postTime) - last.time >= 5_000L &&
                MessageStore.bumpRepeat(this, chatKey, maxOf(m.time, sbn.postTime))) {
                dedupeLog("repeat +1 (new when, same text) chat=$chatKey")
                return
            }
        }
        val added = newMessages.filter { m ->
            val why = MessageStore.add(this, m)
            if (why != null) dedupeLog("skip store duplicate ($why) chat=${m.chatKey}" + Diagnostics.sensitive(this, " text=${m.text.take(40)}"))
            else dedupeLog("added id=${m.id} chat=${m.chatKey}")
            why == null
        }
        if (!isLine) added.lastOrNull()?.let { lastTextByKey[sbn.key] = "${it.sender}|${it.text.trim()}" }
        if (added.isEmpty()) { repeatIfReposted(); return }

        // Reposting / stacking is LINE-only. Other apps already show messages fine.
        if (!isLine || !Prefs.enabled(this)) return
        main.post { pendingResets.remove(chatKey)?.let { main.removeCallbacks(it) } }
        notifLargeIcon?.let { MessageStore.saveChatAvatar(this, chatKey, it) }
        try {
            Notifier.postChat(this, added.maxBy { it.time }, isGroup, silent)
        } catch (e: Throwable) {
            Diagnostics.fail(this, "POST postChat chat=$chatKey", e)
        }
        // Sticker CDN download AFTER post/hide — then silently refresh shade + panel.
        if (pendingStickerByMsgId.isNotEmpty()) {
            enrichStickersAsync(chatKey, pendingStickerByMsgId)
        }
        // Hiding the original happens in processLine()'s finally block, for every path.
    }

    private fun enrichStickersAsync(chatKey: String, urlsById: Map<String, List<String>>) {
        // Fire-and-forget on stickerPool — returns immediately so [worker] stays free.
        val app = applicationContext
        stickerPool.execute {
            var any = false
            for ((id, candidates) in urlsById) {
                try {
                    if (candidates.isEmpty()) {
                        Diagnostics.note(app, "sticker-enrich SKIP no candidates id=$id")
                        continue
                    }
                    val hit = StickerLoader.loadBest(app, candidates)
                    if (hit == null) {
                        Diagnostics.note(app, "sticker-enrich ALL FAILED id=$id tried=${candidates.size} first=${candidates.firstOrNull()}")
                        continue
                    }
                    val (bmp, url) = hit
                    Diagnostics.note(app, "sticker-enrich OK id=$id via=$url ${bmp.width}x${bmp.height}")
                    val path = MessageStore.saveBitmap(app, "img", id.hashCode().toUInt().toString(), bmp, 720) ?: continue
                    MessageStore.updateImagePath(app, id, path)
                    any = true
                } catch (e: Throwable) {
                    Diagnostics.fail(app, "sticker-enrich id=$id", e)
                }
            }
            if (any) try { Notifier.refreshChatSilently(app, chatKey) } catch (e: Throwable) {
                Diagnostics.fail(app, "sticker-enrich refresh chat=$chatKey", e)
            }
        }
    }

    /** Copy reply (RemoteInput) + mark-as-read style actions from LINE for our conversation notif. */
    private fun extractLineActions(n: Notification): List<androidx.core.app.NotificationCompat.Action> {
        val out = ArrayList<androidx.core.app.NotificationCompat.Action>()
        val actions = n.actions ?: return out
        for (a in actions) {
            try {
                val title = a.title?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val pi = a.actionIntent ?: continue
                val b = androidx.core.app.NotificationCompat.Action.Builder(0, title, pi)
                a.remoteInputs?.forEach { ri ->
                    val rb = androidx.core.app.RemoteInput.Builder(ri.resultKey)
                        .setLabel(ri.label)
                    try { ri.allowedDataTypes?.forEach { rb.setAllowDataType(it, true) } } catch (_: Exception) {}
                    b.addRemoteInput(rb.build())
                }
                // Prefer reply (has RemoteInput) and mark-as-read / 已讀 style titles; skip obscure junk.
                val isReply = !a.remoteInputs.isNullOrEmpty()
                val isRead = title.contains("已讀") || title.contains("已读") ||
                    title.contains("Read", true) || title.contains("Mark", true) ||
                    title.contains("既読")
                if (isReply || isRead || out.size < 2) out += b.build()
            } catch (e: Throwable) {
                Diagnostics.note(this, "skip LINE action: $e")
            }
        }
        return out.take(3)
    }

    private fun isLinePkg(p: String) = p == LINE_PKG || p == LINE_LITE_PKG

    private fun build(
        id: String, pkg: String, chatKey: String, chatTitle: String, sender: String,
        text: String, time: Long, image: Bitmap?, avatar: Bitmap?,
    ): LineMessage {
        val imgPath = image?.let { MessageStore.saveBitmap(this, "img", id.hashCode().toUInt().toString(), it, 720) }
        val avPath = avatar?.let {
            // Name the file after the picture itself: notifications that share a title/sender
            // (Threads「1 個新的讚」, IG activity…) each keep their own avatar instead of all
            // pointing at one file that the newest notification keeps overwriting.
            MessageStore.saveBitmap(this, "av", MessageStore.bitmapKey(it), it, 160)
        }
        val urls = LinkExtractor.extract(text)
        return LineMessage(id, pkg, chatKey, chatTitle, sender, text, time, imgPath, avPath, urls)
    }

    private fun idOf(chatKey: String, sender: String, text: String, time: Long, uri: Uri?): String =
        "$chatKey|$sender|$time|${uri ?: ""}|$text".hashCode().toUInt().toString(16) + "-" + time

    private fun isRecentDupe(id: String, windowMs: Long = 10 * 60_000L): Boolean {
        val now = System.currentTimeMillis()
        val it = recent.entries.iterator()
        while (it.hasNext()) if (now - it.next().value > 10 * 60_000L) it.remove()
        val prev = recent[id]
        recent[id] = now
        return prev != null && now - prev < windowMs
    }

    @Suppress("DEPRECATION")
    private fun extractNotificationPicture(ex: Bundle): Bitmap? {
        try {
            (ex.get(Notification.EXTRA_PICTURE) as? Bitmap)?.let { return it }
            if (Build.VERSION.SDK_INT >= 31) {
                (ex.get(Notification.EXTRA_PICTURE_ICON) as? Icon)?.let { icon -> iconToBitmap(icon)?.let { return it } }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun loadUri(uri: Uri): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 1440) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
    } catch (e: Exception) {
        // Usually SecurityException: LINE did not grant us read access to its content URI.
        Log.i(TAG, "cannot read image uri $uri: ${e.javaClass.simpleName}")
        null
    }

    private fun personAvatar(p: Person): Bitmap? = try {
        p.icon?.loadDrawable(this)?.let { drawableToBitmap(it) }
    } catch (_: Exception) {
        null
    }

    private fun iconToBitmap(icon: Icon): Bitmap? = try {
        icon.loadDrawable(this)?.let { drawableToBitmap(it) }
    } catch (_: Exception) {
        null
    }

    private fun drawableToBitmap(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 128
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 128
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }
}

/** Helper used by the Activity to check whether notification access is granted. */
fun isListenerEnabled(ctx: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    val me = ComponentName(ctx, LineListenerService::class.java)
    return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
}

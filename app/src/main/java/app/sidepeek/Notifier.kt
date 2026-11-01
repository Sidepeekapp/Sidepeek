package app.sidepeek

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    // v1.1.0: new id so the stronger vibration pattern applies (Android freezes a channel's
    // vibration once created). The old channel's user settings are copied over in ensureChannels.
    const val CHANNEL_MSG = "line_preview_messages_v4"
    // Newest first: settings are copied from the first one that exists (v2/v3 only on test builds).
    private val OLD_CHANNELS_MSG = listOf("line_preview_messages_v3", "line_preview_messages_v2", "line_preview_messages")
    // LINE's own channel pattern (one long, two short; read from its channel, LINE 26.16); the
    // system default single short buzz is easy to miss in a pocket.
    private val MSG_VIBRATION = longArrayOf(0, 330, 160, 100, 80, 140)
    const val CHANNEL_PANEL = "panel_service_silent"
    private const val OLD_CHANNEL_PANEL = "edge_panel_service"

    const val ACTION_REPLY = "app.sidepeek.REPLY"
    const val ACTION_MARK_READ = "app.sidepeek.MARK_READ"
    const val ACTION_DISMISS = "app.sidepeek.DISMISS"
    private const val SUMMARY_TAG = "chat_summary"
    private const val SUMMARY_ID = 1
    private const val CHAT_GROUP = "line_preview_chats"

    // Android only lets the user change a channel's sound, so picking one in the app moves to a
    // new channel id (CHANNEL_MSG_s1, _s2…) that carries the user's other settings over.
    private const val KEY_MSG_CHANNEL_SEQ = "msg_channel_seq"
    private fun msgChannelId(seq: Int) = if (seq == 0) CHANNEL_MSG else "${CHANNEL_MSG}_s$seq"

    /** The 「LINE 訊息預覽」 channel now in use. */
    fun msgChannel(ctx: Context): String = msgChannelId(Prefs.sp(ctx).getInt(KEY_MSG_CHANNEL_SEQ, 0))

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        run {
            // Settings only apply on first creation; later calls just refresh name/description.
            val seq = Prefs.sp(ctx).getInt(KEY_MSG_CHANNEL_SEQ, 0)
            val id = msgChannelId(seq)
            val older = (seq - 1 downTo 0).map { msgChannelId(it) } + OLD_CHANNELS_MSG
            val old = if (nm.getNotificationChannel(id) == null)
                older.firstNotNullOfOrNull { nm.getNotificationChannel(it) } else null
            createMsgChannel(nm, id, old)
            if (old != null) older.forEach { try { nm.deleteNotificationChannel(it) } catch (_: Exception) {} }
        }
        // v2.5: own silent channel for the fallback foreground-service notification. Users can turn
        // this channel off (Android 13+ then hides the FGS notification). Old channel removed.
        try { nm.deleteNotificationChannel(OLD_CHANNEL_PANEL) } catch (_: Exception) {}
        if (nm.getNotificationChannel(CHANNEL_PANEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PANEL, "側邊欄常駐通知（備用模式，可關閉）", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "未開啟無障礙服務時，側邊欄需要的常駐通知。關閉此類別即可隱藏，側邊欄仍會運作。"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                }
            )
        }
    }

    /** [old]'s user settings carried over; [sound] replaces its sound when [changeSound]. */
    private fun createMsgChannel(
        nm: NotificationManager, id: String, old: NotificationChannel?,
        changeSound: Boolean = false, sound: android.net.Uri? = null,
    ) {
        nm.createNotificationChannel(
            NotificationChannel(id, "LINE 訊息預覽", old?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
                description = "每個 LINE 聊天室一則通知，未讀訊息逐行顯示（浮動通知）"
                enableVibration(old?.shouldVibrate() ?: true)
                vibrationPattern = MSG_VIBRATION
                setShowBadge(old?.canShowBadge() ?: true)
                lockscreenVisibility = old?.lockscreenVisibility ?: android.app.Notification.VISIBILITY_PRIVATE
                if (changeSound) setSound(sound, old?.audioAttributes ?: android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                else if (old != null) setSound(old.sound, old.audioAttributes)
            }
        )
    }

    /** The sound of 「LINE 訊息預覽」 now; null = silent. */
    fun messageSound(ctx: Context): android.net.Uri? {
        ensureChannels(ctx)
        return ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(msgChannel(ctx))?.sound
    }

    /**
     * 「通知鈴聲」 picked in the app ([sound] null = silent). Deleting the old channel takes our LINE
     * notifications off the shade; the orphan recovery puts unread chats back on the new one.
     */
    fun setMessageSound(ctx: Context, sound: android.net.Uri?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ensureChannels(ctx)
        val sp = Prefs.sp(ctx)
        val seq = sp.getInt(KEY_MSG_CHANNEL_SEQ, 0)
        val old = nm.getNotificationChannel(msgChannelId(seq))
        sp.edit().putInt(KEY_MSG_CHANNEL_SEQ, seq + 1).commit()
        createMsgChannel(nm, msgChannelId(seq + 1), old, changeSound = true, sound = sound)
        try { nm.deleteNotificationChannel(msgChannelId(seq)) } catch (_: Exception) {}
        Diagnostics.note(ctx, "SOUND changed -> ${msgChannelId(seq + 1)} silent=${sound == null}")
    }

    /**
     * Can our stacked LINE notification actually reach the user right now? LINE's original may only
     * be hidden when this is true; otherwise the message would vanish with no alert at all.
     * False when: app notifications are off (Android 13+ permission denied / switched off), the
     * 「LINE 訊息預覽」 channel is turned off, or its channel group is blocked.
     */
    fun canRepost(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return true
            // Not created yet: postChat() creates it with IMPORTANCE_HIGH before posting.
            val ch = nm.getNotificationChannel(msgChannel(ctx)) ?: return true
            if (ch.importance == NotificationManager.IMPORTANCE_NONE) return false
            if (Build.VERSION.SDK_INT >= 28) {
                val g = ch.group
                if (g != null && nm.getNotificationChannelGroup(g)?.isBlocked == true) return false
            }
            true
        } catch (_: Exception) { true }
    }

    internal fun titleOf(a: NotificationCompat.Action) = a.title?.toString().orEmpty()
    internal fun isReply(a: NotificationCompat.Action) =
        a.remoteInputs?.isNotEmpty() == true || titleOf(a).contains("回覆") || titleOf(a).contains("Reply", true)
    internal fun isMarkRead(a: NotificationCompat.Action): Boolean {
        val t = titleOf(a)
        return t.contains("已讀") || t.contains("已讀取") || t.contains("Mark as read", true) ||
            t.contains("既読") || (t.contains("讀") && !isReply(a))
    }

    private fun ownPi(ctx: Context, action: String, chatKey: String, mutable: Boolean): PendingIntent {
        val i = Intent(ctx, NotifActionReceiver::class.java).setAction(action).putExtra(EXTRA_CHAT_KEY, chatKey)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(ctx, (action + chatKey).hashCode(), i, flags)
    }

    /**
     * Cap at 3 actions. Priority: reply > 「開啟連結」(only when exactly 1 URL) > mark-as-read.
     * Reply / mark-read go through [NotifActionReceiver] so we can cancel our notification after
     * forwarding LINE's original PendingIntent. 「開啟連結」 stays a direct VIEW intent.
     */
    private fun addPrioritizedActions(ctx: Context, builder: NotificationCompat.Builder, latest: LineMessage) {
        val lineActs = MessageStore.lineActionsFor(latest.chatKey)
        val reply = lineActs.firstOrNull { isReply(it) }
        val mark = lineActs.firstOrNull { isMarkRead(it) }
        val others = lineActs.filter { it !== reply && it !== mark }

        val chosen = ArrayList<NotificationCompat.Action>(3)
        reply?.let { r ->
            val ris = r.remoteInputs
            chosen += if (ris.isNullOrEmpty()) r else {
                val pi = ownPi(ctx, ACTION_REPLY, latest.chatKey, mutable = true)
                val b = if (r.iconCompat != null)
                    NotificationCompat.Action.Builder(r.iconCompat, r.title, pi)
                else
                    NotificationCompat.Action.Builder(0, r.title, pi)
                ris.forEach { b.addRemoteInput(it) }
                b.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build()
            }
        }

        val urls = latest.urls
        if (urls.size == 1 && chosen.size < 3) {
            val url = urls[0]
            val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getActivity(ctx, ("link:" + latest.id).hashCode(), view, flags)
            chosen += NotificationCompat.Action.Builder(0, "開啟連結", pi).build()
        }
        mark?.let { m ->
            if (chosen.size < 3) {
                val pi = ownPi(ctx, ACTION_MARK_READ, latest.chatKey, mutable = false)
                val b = if (m.iconCompat != null)
                    NotificationCompat.Action.Builder(m.iconCompat, m.title, pi)
                else
                    NotificationCompat.Action.Builder(0, m.title, pi)
                chosen += b.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build()
            }
        }
        for (a in others) {
            if (chosen.size >= 3) break
            chosen += a
        }
        for (a in chosen) {
            try { builder.addAction(a) } catch (e: Throwable) {
                Diagnostics.note(ctx, "POST skip action ${a.title}: $e")
            }
        }
    }

    /** Message text as displayed: long links shortened ("instagram.com/reel/…"); 「開啟連結」 keeps the full URL. */
    private fun shown(m: LineMessage): String = LinkExtractor.shortenInText(m.text)

    fun notificationIdFor(chatKey: String): Int = ("chat:$chatKey").hashCode().let { if (it in 0..10) it + 1000 else it }

    fun fallbackIntent(ctx: Context, pkg: String): PendingIntent? {
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(ctx, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            ctx, pkg.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_CHAT_KEY = "app.sidepeek.CHAT_KEY"
    /** Expanded BigTextStyle text is capped by the system at ~9 lines (then "…"). */
    private const val BIG_TEXT_LINES = 9
    /** Approx. width of one notification text line, in CJK-character units (One UI, default font size). */
    private const val LINE_UNITS = 21f
    private const val AVATAR_PX = 128

    /** Rough number of display lines a text needs (CJK = 1 unit, Latin/digits/space ≈ 0.55). */
    private fun estLines(cs: CharSequence): Int = cs.split('\n').sumOf { para ->
        var u = 0f
        for (ch in para) u += if (ch.code >= 0x2E80) 1f else 0.55f
        maxOf(1, kotlin.math.ceil(u / LINE_UNITS).toInt())
    }

    /** Append the gap between two messages: half-height blank line (小) or full blank line (大). */
    private fun appendGap(sb: android.text.SpannableStringBuilder, gap: Int) {
        sb.append('\n')
        when (gap) {
            Prefs.GAP_SMALL -> sb.append("\n", android.text.style.RelativeSizeSpan(0.5f), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            Prefs.GAP_LARGE -> sb.append('\n')
        }
    }

    /** Re-render every chat notification we are currently showing (after a display setting changed). */
    fun refreshAllSilently(ctx: Context) {
        val keys = try {
            ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                .mapNotNull { it.notification.extras.getString(EXTRA_CHAT_KEY) }.distinct()
        } catch (_: Exception) { emptyList() }
        keys.forEach { try { refreshChatSilently(ctx, it) } catch (_: Exception) {} }
    }
    private val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.TAIWAN)

    /**
     * After a message was deleted/restored in the side panel: refresh our stacked notification for
     * that chat silently, or cancel it if no unread messages remain. Only touches a notification
     * that is currently showing.
     */
    fun refreshChatSilently(ctx: Context, chatKey: String) {
        val id = notificationIdFor(chatKey)
        val showing = try {
            ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == id && it.tag == null }
        } catch (_: Exception) { false }
        if (!showing) return
        val unread = unreadAfterWatermark(ctx, chatKey, null)
        if (unread.isEmpty()) { cancelChat(ctx, chatKey); return }
        val latest = unread.maxBy { it.time }
        val group = unread.any { it.sender.isNotBlank() && it.sender != it.chatTitle }
        postChat(ctx, latest, group, silent = true)
    }

    /** Unread messages after the dismiss watermark; falls back to [fallback] / latest if empty. */
    private fun unreadAfterWatermark(ctx: Context, chatKey: String, fallback: LineMessage?): List<LineMessage> {
        val all = MessageStore.unreadForChat(ctx, chatKey, 200).ifEmpty { fallback?.let { listOf(it) } ?: emptyList() }
        val wm = MessageStore.notifWatermark(ctx, chatKey)
        val filtered = all.filter { it.time > wm }
        if (filtered.isNotEmpty()) return filtered
        // Somehow empty after filter — still allow the latest so a new post isn't dropped.
        return all.takeLast(1).ifEmpty { fallback?.let { listOf(it) } ?: emptyList() }
    }

    fun postChat(ctx: Context, latest: LineMessage, isGroupChat: Boolean, silent: Boolean = false) {
        ensureChannels(ctx)
        val unread = unreadAfterWatermark(ctx, latest.chatKey, latest)
        val messaging = Prefs.notifStyle(ctx) == Prefs.STYLE_MESSAGING
        val cap = if (messaging) 25 else 50
        val shown = unread.takeLast(minOf(Prefs.notifMax(ctx), cap))
        val hidden = unread.size - shown.size

        val showSender = { m: LineMessage -> isGroupChat && m.sender.isNotBlank() && m.sender != m.chatTitle }

        /** Text for a message line; image-only rows use 「[圖片]」 when not shown as BigPicture. */
        fun textOf(m: LineMessage, bigPictureMode: Boolean): String {
            if (!m.imagePath.isNullOrBlank() && !messaging && !bigPictureMode) {
                return shown(m).ifBlank { "[圖片]" }
            }
            return shown(m)
        }

        fun body(m: LineMessage, bigPictureMode: Boolean = false): CharSequence {
            val t = textOf(m, bigPictureMode)
            if (!showSender(m)) return t
            val sb = android.text.SpannableStringBuilder()
            sb.append(m.sender, android.text.style.StyleSpan(android.graphics.Typeface.BOLD), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("：").append(t)
            return sb
        }

        /** One message: "21:05  text" (1:1 chat, time bold) or "21:05  **小明**：text" (group). Never truncated. */
        fun line(m: LineMessage, keepNewlines: Boolean, bigPictureMode: Boolean = false): CharSequence {
            val t = clock.format(java.util.Date(m.time))
            val sb = android.text.SpannableStringBuilder()
            if (showSender(m)) sb.append(t) else
                sb.append(t, android.text.style.StyleSpan(android.graphics.Typeface.BOLD), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ")
            val b = body(m, bigPictureMode)
            sb.append(if (keepNewlines) b else android.text.SpannableStringBuilder(b).also {
                for (i in it.indices) if (it[i] == '\n') it.replace(i, i + 1, " ")
            })
            return sb
        }
        val title = latest.chatTitle.ifBlank { latest.sender.ifBlank { Sources.displayName(latest.pkg) } }
        val builder = NotificationCompat.Builder(ctx, msgChannel(ctx))
            .setSmallIcon(R.drawable.ic_stat_msg)
            .setColor(Sources.forPackage(latest.pkg)?.color ?: 0xFF16A34A.toInt())
            .setContentTitle(title)
            // Collapsed fallback (OEMs that ignore MessagingStyle): show the latest 2 messages.
            .setContentText(collapsedSummary(unread, isGroupChat))
            .setWhen(latest.time)
            .setShowWhen(true)
            .setNumber(unread.size)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)         // every new message alerts / heads-up again (not on edits)
            .setSilent(silent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_GROUP)
            .setContentIntent(MessageStore.intentFor(latest.chatKey) ?: fallbackIntent(ctx, latest.pkg))
            .setDeleteIntent(ownPi(ctx, ACTION_DISMISS, latest.chatKey, mutable = false))
            .addExtras(android.os.Bundle().apply { putString(EXTRA_CHAT_KEY, latest.chatKey) })

        // Actions (max 3): reply > open-link (exactly 1 URL) > mark-read.
        try {
            addPrioritizedActions(ctx, builder, latest)
        } catch (e: Throwable) {
            Diagnostics.fail(ctx, "POST add actions", e)
        }

        // LINE 26.x group notifications expose the GROUP picture as largeIcon. Keep it in the
        // header (unless this chat turned out to put the sender there, see groupPicture);
        // individual rows use Person.icon when supplied, otherwise a sender initial badge.
        if (isGroupChat) builder.setLargeIcon(circle(groupPicture(ctx, latest.chatKey) ?: groupIcon(latest.chatTitle)))
        else (MessageStore.loadBitmap(MessageStore.chatAvatarPath(ctx, latest.chatKey))
            ?: MessageStore.loadBitmap(latest.avatarPath))?.let { builder.setLargeIcon(circle(it)) }

        // P1-6: BigPicture only when a single unread has an image; else text list with 「[圖片]」.
        val useBigPicture = !messaging && unread.size == 1 && !latest.imagePath.isNullOrBlank()
        val picture = if (useBigPicture) MessageStore.loadBitmap(latest.imagePath) else null
        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .setBigContentTitle(title)
                    .bigPicture(picture)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                    .setSummaryText(body(latest, bigPictureMode = true))
            )
        } else if (messaging) {
            try {
                applyMessagingStyle(ctx, builder, latest, title, shown, isGroupChat)
            } catch (e: Throwable) {
                Diagnostics.fail(ctx, "POST MessagingStyle chat=${latest.chatKey}", e)
                // Fall back to BigText so the user still gets a preview.
                try {
                    val gap = Prefs.msgGap(ctx)
                    val big = android.text.SpannableStringBuilder()
                    shown.forEachIndexed { i, m -> if (i > 0) appendGap(big, gap); big.append(line(m, keepNewlines = true)) }
                    builder.setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(big))
                } catch (e2: Throwable) {
                    Diagnostics.fail(ctx, "POST BigText-fallback chat=${latest.chatKey}", e2)
                }
            }
        } else if (Prefs.notifStyle(ctx) == Prefs.STYLE_INBOX) {
            // 精簡每行：one line per message (system ellipsizes long lines)
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            shown.forEach { style.addLine(line(it, keepNewlines = false)) }
            val more = unread.size - shown.size
            if (more > 0) style.setSummaryText("還有 $more 則較早的訊息")
            builder.setStyle(style)
        } else {
            // 完整文字 (default): every message wraps fully, separated by a gap (setting 「訊息之間的間距」).
            val gap = Prefs.msgGap(ctx)
            val gapCost = if (gap == Prefs.GAP_NONE) 0 else 1
            val lines = shown.map { line(it, keepNewlines = true) }
            var used = 0
            var fit = 0
            for (i in lines.indices.reversed()) {
                val cost = estLines(lines[i]) + (if (fit > 0) gapCost else 0)
                val noteCost = if (i > 0 || hidden > 0) 1 + gapCost else 0
                if (fit > 0 && used + cost + noteCost > BIG_TEXT_LINES) break
                used += cost; fit++
            }
            val older = unread.size - fit
            val big = android.text.SpannableStringBuilder()
            if (older > 0) { big.append("（還有 $older 則較早的訊息）"); appendGap(big, gap) }
            lines.takeLast(fit).forEachIndexed { i, l -> if (i > 0) appendGap(big, gap); big.append(l) }
            builder.setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(big))
        }

        // P1-1: lock-screen public version — app name + count only, no chat title.
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, msgChannel(ctx))
                    .setSmallIcon(R.drawable.ic_stat_msg)
                    .setColor(Sources.forPackage(latest.pkg)?.color ?: 0xFF16A34A.toInt())
                    .setContentTitle(Sources.displayName(latest.pkg))
                    .setContentText("${unread.size} 則新訊息")
                    .setNumber(unread.size)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .build()
            )

        val id = notificationIdFor(latest.chatKey)
        try {
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                Diagnostics.note(ctx, "POST blocked: app notifications disabled chat=${latest.chatKey}")
                return
            }
            postChatSummary(ctx, latest)
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
            Diagnostics.note(ctx, "POST ok id=$id chat=${latest.chatKey} style=${Prefs.notifStyle(ctx)} silent=$silent unread=${unread.size}")
        } catch (e: SecurityException) {
            Diagnostics.fail(ctx, "POST SecurityException (POST_NOTIFICATIONS?) chat=${latest.chatKey}", e)
        } catch (e: Throwable) {
            Diagnostics.fail(ctx, "POST notify chat=${latest.chatKey}", e)
            // Last-resort plain notification without a fancy style.
            try {
                val plain = NotificationCompat.Builder(ctx, msgChannel(ctx))
                    .setSmallIcon(R.drawable.ic_stat_msg)
                    .setContentTitle(title)
                    .setContentText(shown(latest))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(shown(latest)))
                    .setContentIntent(MessageStore.intentFor(latest.chatKey) ?: fallbackIntent(ctx, latest.pkg))
                    .setDeleteIntent(ownPi(ctx, ACTION_DISMISS, latest.chatKey, mutable = false))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .addExtras(android.os.Bundle().apply { putString(EXTRA_CHAT_KEY, latest.chatKey) })
                NotificationManagerCompat.from(ctx).notify(id, plain.build())
                Diagnostics.note(ctx, "POST plain-fallback ok id=$id chat=${latest.chatKey}")
            } catch (e2: Throwable) {
                Diagnostics.fail(ctx, "POST plain-fallback chat=${latest.chatKey}", e2)
            }
        }
    }

    /**
     * Same shape as LINE's own notifications: all chats share one group with a real summary, so
     * swiping the collapsed stack clears it while an expanded chat can be swiped alone. Without a
     * summary Android 16+ folds the chats into its own stack and one swipe clears them all.
     * No EXTRA_CHAT_KEY: the summary is not a chat preview. Silent; the child does the alerting.
     */
    private fun postChatSummary(ctx: Context, latest: LineMessage) {
        val summary = NotificationCompat.Builder(ctx, msgChannel(ctx))
            .setSmallIcon(R.drawable.ic_stat_msg)
            .setColor(Sources.forPackage(latest.pkg)?.color ?: 0xFF16A34A.toInt())
            .setContentTitle(Sources.displayName(latest.pkg))
            .setWhen(latest.time)
            .setShowWhen(true)
            .setGroup(CHAT_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(fallbackIntent(ctx, latest.pkg))
            .build()
        NotificationManagerCompat.from(ctx).notify(SUMMARY_TAG, SUMMARY_ID, summary)
    }

    /** Latest 1–2 messages for the collapsed one-line/two-line summary. */
    private fun collapsedSummary(unread: List<LineMessage>, isGroupChat: Boolean): CharSequence {
        val last = unread.takeLast(2)
        if (last.isEmpty()) return ""
        fun one(m: LineMessage): CharSequence {
            val t = if (!m.imagePath.isNullOrBlank()) shown(m).ifBlank { "[圖片]" } else shown(m)
            if (!(isGroupChat && m.sender.isNotBlank() && m.sender != m.chatTitle)) return t
            val sb = android.text.SpannableStringBuilder()
            sb.append(m.sender, android.text.style.StyleSpan(android.graphics.Typeface.BOLD), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("：").append(t)
            return sb
        }
        if (last.size == 1) return one(last[0])
        return android.text.SpannableStringBuilder().append(one(last[0])).append("\n").append(one(last[1]))
    }

    private data class ShortcutSnap(val title: String, val avatarMtime: Long)
    private val shortcutCache = java.util.concurrent.ConcurrentHashMap<String, ShortcutSnap>()

    /**
     * 對話樣式: NotificationCompat.MessagingStyle, like Instagram / WhatsApp. One UI shows the latest
     * two messages when collapsed and the whole list when expanded. The notification is linked to a
     * long-lived per-chat shortcut so Android 11+ treats it as a real conversation.
     */
    private fun applyMessagingStyle(
        ctx: Context, builder: NotificationCompat.Builder, latest: LineMessage, title: String,
        shown: List<LineMessage>, isGroupChat: Boolean,
    ) {
        val me = androidx.core.app.Person.Builder().setName("我").setKey("me").build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(isGroupChat)
        if (isGroupChat) style.setConversationTitle(title)
        val people = LinkedHashMap<String, androidx.core.app.Person>()
        var chatPerson: androidx.core.app.Person? = null
        for (m in shown) {
            val name = (if (isGroupChat) m.sender else m.sender.takeIf { it.isNotBlank() && it != "我" } ?: title).ifBlank { title }
            val p = people.getOrPut(name) {
                androidx.core.app.Person.Builder().setName(name).setKey("${m.chatKey}|$name").apply {
                    // Person icon only for group chats; 1:1 relies on shortcut/largeIcon. When
                    // LINE omits a member icon, use the same deterministic initial avatar as the
                    // side panel so people remain distinct and consistent across Android skins.
                    if (isGroupChat) {
                        val icon = MessageStore.messageAvatar(ctx, m, suppressChatPicture = isLine(m.pkg))
                            ?: AvatarBadge.bitmap(name, AVATAR_PX)
                        setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(circle(icon)))
                    }
                }.build()
            }
            if (!isGroupChat) chatPerson = p
            val msg = NotificationCompat.MessagingStyle.Message(shown(m), m.time, p)
            imageUri(ctx, m.imagePath)?.let { uri ->
                // SystemUI draws only the image of a data message, so its caption needs its own row.
                val caption = shown(m)
                if (caption.isNotBlank() && caption != "（圖片）") style.addMessage(caption, m.time, p)
                msg.setData(imageMime(m.imagePath!!), uri)
            }
            style.addMessage(msg)
        }
        builder.setStyle(style)
        try {
            val chatIcon = if (isGroupChat) groupPicture(ctx, latest.chatKey) ?: groupIcon(title)
                else MessageStore.loadBitmap(MessageStore.chatAvatarPath(ctx, latest.chatKey))
                    ?: MessageStore.loadBitmap(latest.avatarPath)
            val id = "chat_" + latest.chatKey.hashCode().toUInt().toString(16)
            val avatarFile = MessageStore.chatAvatarPath(ctx, latest.chatKey)?.let { java.io.File(it) }
                ?: latest.avatarPath?.let { java.io.File(it) }
            val mtime = avatarFile?.takeIf { it.exists() }?.lastModified() ?: 0L
            val snap = ShortcutSnap(title, mtime)
            val cached = shortcutCache[id]
            // P1-4: only pushDynamicShortcut when title or avatar mtime changes; always setShortcutId/LocusId.
            if (cached != snap) {
                val sc = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, id)
                    .setShortLabel(title.take(24).ifBlank { "LINE" })
                    .setLongLived(true)
                    .setIntent(Intent(ctx, OpenChatActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_CHAT_KEY, latest.chatKey).putExtra(OpenChatActivity.EXTRA_PKG, latest.pkg))
                    .setIcon(chatIcon?.let { androidx.core.graphics.drawable.IconCompat.createWithBitmap(circle(it)) }
                        ?: androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .apply {
                        val ps = chatPerson?.let { arrayOf(it) } ?: people.values.toTypedArray()
                        if (ps.isNotEmpty()) setPersons(ps)
                    }
                    .build()
                androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(ctx, sc)
                shortcutCache[id] = snap
            }
            builder.setShortcutId(id)
            try {
                builder.setLocusId(androidx.core.content.LocusIdCompat(id))
            } catch (e: Exception) {
                Diagnostics.note(ctx, "NOTIF LocusId failed: $e")
            }
        } catch (e: Exception) {
            Diagnostics.note(ctx, "NOTIF shortcut failed: $e")
        }
    }

    /** Prefer BitmapFactory bounds outMimeType; fall back to image/png. */
    private fun imageMime(path: String): String = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        opts.outMimeType?.takeIf { it.startsWith("image/") } ?: "image/png"
    } catch (_: Exception) { "image/png" }

    /** content:// uri (FileProvider) for a stored image, readable by SystemUI. */
    private fun imageUri(ctx: Context, path: String?): android.net.Uri? {
        if (path.isNullOrBlank()) return null
        val f = java.io.File(path)
        if (!f.exists()) return null
        return try {
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f).also {
                try { ctx.grantUriPermission("com.android.systemui", it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
        } catch (_: Exception) { null }
    }

    /** Always output a square [AVATAR_PX] circular bitmap (largeIcon / shortcut / Person). */
    /**
     * A LINE group's own picture (LINE's large icon, saved per chat). Not used once this chat has
     * shown that its large icon follows the sender instead (see GroupPicture).
     */
    private fun groupPicture(ctx: Context, chatKey: String): android.graphics.Bitmap? =
        if (MessageStore.groupPicture(ctx, chatKey) == GroupPicture.Verdict.SENDER) null
        else MessageStore.loadBitmap(MessageStore.chatAvatarPath(ctx, chatKey))

    /**
     * Fallback when there is no group picture to show (ordinary groups, whose large icon is the
     * sender): the group's initial in its own palette colour, so several groups are easy to tell apart.
     */
    private fun groupIcon(name: String): android.graphics.Bitmap {
        return AvatarBadge.bitmap(name.ifBlank { "群" }, AVATAR_PX)
    }

    private fun isLine(pkg: String): Boolean =
        pkg == LineListenerService.LINE_PKG || pkg == LineListenerService.LINE_LITE_PKG

    private fun circle(src: android.graphics.Bitmap, outPx: Int = AVATAR_PX): android.graphics.Bitmap {
        val s = minOf(src.width, src.height)
        val out = android.graphics.Bitmap.createBitmap(outPx, outPx, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        c.drawCircle(outPx / 2f, outPx / 2f, outPx / 2f, p)
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val l = (src.width - s) / 2
        val t = (src.height - s) / 2
        c.drawBitmap(src, android.graphics.Rect(l, t, l + s, t + s), android.graphics.Rect(0, 0, outPx, outPx), p)
        return out
    }

    fun cancelChat(ctx: Context, chatKey: String) {
        val id = notificationIdFor(chatKey)
        NotificationManagerCompat.from(ctx).cancel(id)
        // Only once no chat is left: cancelling a summary also removes the chats under it.
        val othersLeft = try {
            ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                .any { it.id != id && it.notification.extras.getString(EXTRA_CHAT_KEY) != null }
        } catch (_: Exception) { true }
        if (!othersLeft) NotificationManagerCompat.from(ctx).cancel(SUMMARY_TAG, SUMMARY_ID)
    }

    /** Chat keys of the tidied LINE notifications now showing. */
    private fun activeLineChatKeys(ctx: Context): List<String> = try {
        ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications
            .mapNotNull { it.notification.extras.getString(EXTRA_CHAT_KEY) }.distinct()
            .filter { it.startsWith(LineListenerService.LINE_PKG + "|") || it.startsWith(LineListenerService.LINE_LITE_PKG + "|") }
    } catch (_: Exception) { emptyList() }

    /** Chat key -> chat name for the tidied LINE notifications now showing (進階清理 compares against these). */
    fun activeLineChatTitles(ctx: Context): Map<String, String> =
        activeLineChatKeys(ctx).mapNotNull { k -> MessageStore.latestInChat(ctx, k)?.chatTitle?.let { k to it } }.toMap()

    /** The user is looking at LINE: count every LINE chat as read and remove our copies. Returns how many. */
    fun clearLineChats(ctx: Context): Int {
        val keys = activeLineChatKeys(ctx)
        keys.forEach { MessageStore.markRead(ctx, it); cancelChat(ctx, it) }
        return keys.size
    }

    /** The user opened this one LINE chat: count it as read and remove our copy. */
    fun clearLineChat(ctx: Context, chatKey: String) {
        MessageStore.markRead(ctx, chatKey); cancelChat(ctx, chatKey)
    }

    fun postTest(ctx: Context) {
        val chatKey = "test|demo"
        // The demo deliberately repeats the same five texts. Remove its previous run first so the
        // production duplicate filter (10 seconds, or 30 minutes for long text) cannot hide rows.
        MessageStore.purgeFiles(ctx, MessageStore.removeChat(ctx, chatKey))
        MessageStore.markRead(ctx, chatKey)   // start a fresh stack
        MessageStore.setNotifWatermark(ctx, chatKey, 0L)
        val now = System.currentTimeMillis() + 5
        // A small group chat that shows off what the app does: several senders, a link button,
        // a user name that only looks like a URL (no button), and a long message shown in full.
        // The four names get four different badge colours (AvatarBadge.colorFor).
        val title = "測試｜週六聚餐"
        val lines = listOf(
            "家豪" to "這週六聚餐還 OK 嗎？",
            "怡君" to "OK！餐廳在這 https://example.com/dinner",
            "思妤" to "我先訂位，6 個人、晚上 6 點半，套餐一人 680 含飲料。我先刷卡，大家再轉給我，帳號晚點貼上來，記得週五前喔",
            "宗翰" to "甜點可以參考 IG 上 cake.daily 的推薦",
            "怡君" to "收到 👌",
        )
        var last: LineMessage? = null
        lines.forEachIndexed { i, (who, t) ->
            val m = LineMessage(
                id = "test-$now-$i", pkg = LineListenerService.LINE_PKG, chatKey = chatKey,
                chatTitle = title, sender = who, text = t, time = now + 1 + i,
                urls = LinkExtractor.extract(t),
            )
            MessageStore.add(ctx, m); last = m
        }
        last?.let { postChat(ctx, it, isGroupChat = true) }
    }
}

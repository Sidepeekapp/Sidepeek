package app.sidepeek

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling debug log in filesDir that the user can export and share.
 *
 * Normally only short events are written (what was posted / hidden / skipped), never message
 * contents. Full dumps of every notification (all extras, recursively, including message text)
 * are written only while the user has switched on 「收集完整診斷記錄」, for [FULL_HOURS] hours.
 */
object Diagnostics {
    private const val FILE = "diag.txt"
    private const val MAX_BYTES = 600_000L
    const val FULL_HOURS = 24
    private const val KEY_FULL_UNTIL = "diag_full_until"
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.TAIWAN)

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    /** End of the full-dump window (0 = off). */
    fun fullUntil(ctx: Context): Long = Prefs.sp(ctx).getLong(KEY_FULL_UNTIL, 0L)

    /** True while full notification dumps (with message text) are being collected. */
    fun full(ctx: Context): Boolean = System.currentTimeMillis() < fullUntil(ctx)

    fun startFull(ctx: Context) {
        Prefs.sp(ctx).edit().putLong(KEY_FULL_UNTIL, System.currentTimeMillis() + FULL_HOURS * 3600_000L).apply()
        note(ctx, "DIAG full collection ON for ${FULL_HOURS}h")
    }

    fun stopFull(ctx: Context) {
        Prefs.sp(ctx).edit().putLong(KEY_FULL_UNTIL, 0L).apply()
        note(ctx, "DIAG full collection OFF")
    }

    /** [detail] (may contain message text) only while full collection is on, else "". */
    fun sensitive(ctx: Context, detail: String): String = if (full(ctx)) detail else ""

    @Synchronized
    fun record(ctx: Context, sbn: StatusBarNotification, event: String = "POSTED") {
        if (!full(ctx)) {
            // Event only: which app / notification, no title, text or extras.
            val n = sbn.notification
            note(ctx, "$event pkg=${sbn.packageName} id=${sbn.id} category=${n.category} " +
                "flags=0x${Integer.toHexString(n.flags)} summary=${n.flags and Notification.FLAG_GROUP_SUMMARY != 0}")
            return
        }
        try {
            val sb = StringBuilder()
            val n = sbn.notification
            sb.append("===== ").append(event).append(' ').append(fmt.format(Date())).append(" =====\n")
            sb.append("pkg=").append(sbn.packageName).append(" id=").append(sbn.id)
                .append(" tag=").append(sbn.tag).append(" key=").append(sbn.key).append('\n')
            sb.append("postTime=").append(fmt.format(Date(sbn.postTime))).append(" when=").append(n.`when`)
                .append(" flags=0x").append(Integer.toHexString(n.flags))
                .append(" category=").append(n.category)
                .append(" channel=").append(n.channelId)
                .append(" group=").append(n.group)
                .append(" sortKey=").append(n.sortKey)
                .append(" shortcutId=").append(n.shortcutId)
                .append(" isGroupSummary=").append(n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
                .append('\n')
            sb.append("tickerText=").append(n.tickerText).append('\n')
            sb.append("contentIntent=").append(n.contentIntent).append('\n')
            sb.append("largeIcon=").append(describe(n.getLargeIcon())).append('\n')
            n.actions?.forEachIndexed { i, a ->
                sb.append("action[").append(i).append("] title=").append(a.title)
                    .append(" remoteInputs=").append(a.remoteInputs?.joinToString { it.resultKey }).append('\n')
            }
            sb.append("extras:\n")
            dumpBundle(n.extras, sb, "  ", 0)
            sb.append('\n')

            val f = file(ctx)
            if (f.exists() && f.length() > MAX_BYTES) {
                // keep the newest half
                val text = f.readText()
                f.writeText(text.substring(text.length / 2))
            }
            f.appendText(sb.toString())
        } catch (e: Throwable) {
            try { file(ctx).appendText("dump failed: $e\n") } catch (_: Throwable) {}
        }
    }

    @Synchronized
    fun note(ctx: Context, line: String) {
        try { file(ctx).appendText("[${fmt.format(Date())}] $line\n") } catch (_: Throwable) {}
    }

    /** Log a failure with a truncated stack trace (POST / HIDE / LISTENER). */
    fun fail(ctx: Context, tag: String, e: Throwable) {
        val stack = e.stackTraceToString().lineSequence().take(25).joinToString("\n")
        note(ctx, "FAIL $tag: $e\n$stack")
    }

    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete() }

    /** Build an export file in cacheDir/export (shared through FileProvider). */
    @Synchronized
    fun export(ctx: Context): File {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val out = File(dir, "line-notification-diagnostics.txt")
        val header = buildString {
            append("Sidepeek 診斷記錄\n")
            append("匯出時間: ").append(fmt.format(Date())).append('\n')
            append("Sidepeek 版本: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("側邊欄模式: ").append(when {
                PanelAccessibilityService.instance != null -> "無障礙服務 (TYPE_ACCESSIBILITY_OVERLAY, 無常駐通知)"
                EdgePanelService.running -> "備用 (TYPE_APPLICATION_OVERLAY + 前景服務通知)"
                else -> "未運作"
            }).append(" / 無障礙設定已開啟=").append(PanelHost.a11yEnabledInSettings(ctx))
              .append(" / 側邊欄運作=").append(PanelHost.running()).append('\n')
            append("裝置: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            for (p in (Sources.KNOWN.flatMap { it.packages } + Sources.selected(ctx)).toSortedSet()) {
                try {
                    val pi = ctx.packageManager.getPackageInfo(p, 0)
                    append(p).append(" 版本: ").append(pi.versionName).append('\n')
                } catch (_: Exception) {}
            }
            append("通知存取: ").append(isListenerEnabled(ctx)).append('\n')
            append("完整記錄: ").append(if (full(ctx)) "收集中（含訊息內容）" else "關閉（只有事件，不含訊息內容）").append('\n')
            append("※ 收集完整記錄期間的內容包含訊息文字，分享前請留意隱私。\n\n")
        }
        val body = if (file(ctx).exists()) file(ctx).readText() else "(尚無記錄，請先收到幾則 LINE/Messenger/IG/Threads/Teams 訊息/貼圖/照片後再匯出)\n"
        out.writeText(header + body)
        return out
    }

    private fun describe(v: Any?): String = when (v) {
        null -> "null"
        is CharSequence -> "\"$v\" (${v.javaClass.simpleName})"
        is Bitmap -> "Bitmap ${v.width}x${v.height} ${v.config}"
        is Icon -> "Icon " + describeIcon(v)
        is Uri -> "Uri $v"
        is IntArray -> "int[] ${v.contentToString()}"
        is LongArray -> "long[] ${v.contentToString()}"
        is BooleanArray -> "boolean[] ${v.contentToString()}"
        is ByteArray -> "byte[${v.size}]"
        else -> "${v.javaClass.name}: ${v.toString().take(500)}"
    }

    private fun describeIcon(i: Icon): String {
        if (Build.VERSION.SDK_INT < 28) return i.toString()
        return try {
            when (i.type) {
                Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> "uri=${i.uri}"
                Icon.TYPE_RESOURCE -> "res=${i.resPackage}/0x${Integer.toHexString(i.resId)}"
                else -> "type=${i.type}"
            }
        } catch (_: Throwable) { i.toString() }
    }

    private fun iconType(i: Icon): Int = if (Build.VERSION.SDK_INT >= 28) i.type else -1

    @Suppress("DEPRECATION")
    private fun dumpBundle(b: Bundle?, sb: StringBuilder, indent: String, depth: Int) {
        if (b == null) { sb.append(indent).append("null\n"); return }
        if (depth > 6) { sb.append(indent).append("...(too deep)\n"); return }
        val keys = try { b.keySet().sorted() } catch (e: Throwable) { sb.append(indent).append("keySet failed: $e\n"); return }
        for (k in keys) {
            val v = try { b.get(k) } catch (e: Throwable) { "<unreadable: $e>" }
            dumpValue(k, v, sb, indent, depth)
        }
    }

    private fun dumpValue(k: String, v: Any?, sb: StringBuilder, indent: String, depth: Int) {
        when (v) {
            is Bundle -> { sb.append(indent).append(k).append(" = Bundle {\n"); dumpBundle(v, sb, "$indent  ", depth + 1); sb.append(indent).append("}\n") }
            is Array<*> -> {
                sb.append(indent).append(k).append(" = ").append(v.javaClass.componentType?.simpleName).append("[").append(v.size).append("] [\n")
                v.forEachIndexed { i, item -> dumpValue("[$i]", item, sb, "$indent  ", depth + 1) }
                sb.append(indent).append("]\n")
            }
            is List<*> -> {
                sb.append(indent).append(k).append(" = List(").append(v.size).append(") [\n")
                v.forEachIndexed { i, item -> dumpValue("[$i]", item, sb, "$indent  ", depth + 1) }
                sb.append(indent).append("]\n")
            }
            is Parcelable -> {
                if (v is Bitmap || v is Icon || v is Uri) sb.append(indent).append(k).append(" = ").append(describe(v)).append('\n')
                else if (v is Notification) {
                    sb.append(indent).append(k).append(" = Notification {\n"); dumpBundle(v.extras, sb, "$indent  ", depth + 1); sb.append(indent).append("}\n")
                } else if (Build.VERSION.SDK_INT >= 28 && v is android.app.Person) {
                    val p: android.app.Person = v
                    sb.append(indent).append(k).append(" = Person(name=").append(p.name).append(", key=").append(p.key)
                        .append(", uri=").append(p.uri).append(", icon=").append(describe(p.icon)).append(", bot=").append(p.isBot).append(")\n")
                } else sb.append(indent).append(k).append(" = ").append(describe(v)).append('\n')
            }
            else -> sb.append(indent).append(k).append(" = ").append(describe(v)).append('\n')
        }
    }
}

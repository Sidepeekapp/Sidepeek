package app.sidepeek

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Home-screen widget with the same list as the side panel; tapping a row opens it the same way. */
class MessagesWidget : AppWidgetProvider() {
    companion object {
        private const val REFRESH_DELAY_MS = 300L
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var pendingCtx: Context? = null
        private val pending = Runnable { pendingCtx?.let(::notifyChanged); pendingCtx = null }

        /** Coalesces bursts (LINE posts, sweeps, batch deletes) into one list reload. */
        fun refresh(ctx: Context) {
            pendingCtx = ctx.applicationContext
            main.removeCallbacks(pending)
            main.postDelayed(pending, REFRESH_DELAY_MS)
        }

        private fun notifyChanged(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, MessagesWidget::class.java))
            if (ids.isEmpty()) return
            manager.partiallyUpdateAppWidget(ids, RemoteViews(ctx.packageName, R.layout.widget_messages).apply {
                setTextViewText(R.id.widgetCount, countText(ctx))
            })
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }

        private fun countText(ctx: Context) =
            PanelRows.visibleMessages(ctx).size.takeIf { it > 0 }?.toString().orEmpty()
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(ctx, id))
    }

    private fun build(ctx: Context, id: Int): RemoteViews {
        // A distinct data Uri keeps each placed widget bound to its own adapter.
        val adapter = Intent(ctx, MessagesWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(Uri.parse("sidepeek://widget/$id"))
        val open = Intent(ctx, OpenChatActivity::class.java)
        val panel = Intent(ctx, OpenPanelActivity::class.java)
        return RemoteViews(ctx.packageName, R.layout.widget_messages).apply {
            setTextViewText(R.id.widgetCount, countText(ctx))
            @Suppress("DEPRECATION")
            setRemoteAdapter(R.id.widgetList, adapter)
            setEmptyView(R.id.widgetList, R.id.widgetEmpty)
            // Mutable: each row fills in its own message.
            setPendingIntentTemplate(R.id.widgetList, PendingIntent.getActivity(
                ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            setOnClickPendingIntent(R.id.widgetHeader, PendingIntent.getActivity(
                ctx, id, panel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }
}

class MessagesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var rows: List<LineMessage> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() { rows = emptyList() }
        override fun onDataSetChanged() { rows = PanelRows.visibleMessages(ctx) }
        override fun getCount() = rows.size
        override fun getItemId(position: Int) = rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: 0L
        override fun hasStableIds() = true
        override fun getViewTypeCount() = 1
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val row = RemoteViews(ctx.packageName, R.layout.widget_message_item)
            val m = rows.getOrNull(position) ?: return row
            val ts = Prefs.panelTextScale(ctx)
            row.setTextViewText(R.id.widgetChat, m.chatTitle)
            row.setTextViewTextSize(R.id.widgetChat, TypedValue.COMPLEX_UNIT_SP, 14f * ts)
            row.setTextViewText(R.id.widgetTime, PanelRows.formatTime(m.time))
            row.setTextViewTextSize(R.id.widgetTime, TypedValue.COMPLEX_UNIT_SP, 12f * ts)
            bindBadge(row, m, ts)
            val showSender = m.sender.isNotBlank() && m.sender != m.chatTitle
            row.setViewVisibility(R.id.widgetSender, if (showSender) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.widgetSender, m.sender)
            row.setTextViewTextSize(R.id.widgetSender, TypedValue.COMPLEX_UNIT_SP, 12.5f * ts)
            row.setTextViewText(R.id.widgetText, LinkExtractor.shortenInText(m.text))
            row.setTextViewTextSize(R.id.widgetText, TypedValue.COMPLEX_UNIT_SP, 14.5f * ts)
            row.setInt(R.id.widgetText, "setMaxLines", Prefs.panelMaxLines(ctx))
            row.setViewVisibility(R.id.widgetRepeat, if (m.repeat > 1) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.widgetRepeat, ctx.getString(R.string.repeat_count, m.repeat))
            row.setImageViewBitmap(R.id.widgetAvatar, avatarOf(m))
            bindImage(row, m)
            row.setOnClickFillInIntent(R.id.widgetRow, target(m))
            row.setOnClickFillInIntent(R.id.widgetAvatar, target(m).putExtra(OpenChatActivity.EXTRA_PROFILE, true))
            return row
        }

        private fun target(m: LineMessage) = Intent()
            .putExtra(Notifier.EXTRA_CHAT_KEY, m.chatKey)
            .putExtra(OpenChatActivity.EXTRA_PKG, m.pkg)
            .putExtra(OpenChatActivity.EXTRA_MSG_ID, m.id)

        private fun bindBadge(row: RemoteViews, m: LineMessage, ts: Float) {
            val src = Sources.forPackage(ctx, m.pkg)
            row.setViewVisibility(R.id.widgetBadge, if (src != null) View.VISIBLE else View.GONE)
            if (src == null) return
            row.setTextViewText(R.id.widgetBadge, src.badge)
            row.setTextViewTextSize(R.id.widgetBadge, TypedValue.COMPLEX_UNIT_SP, 10.5f * ts)
            if (Build.VERSION.SDK_INT >= 31)
                row.setColorStateList(R.id.widgetBadge, "setBackgroundTintList", ColorStateList.valueOf(src.color))
            else row.setInt(R.id.widgetBadge, "setBackgroundColor", src.color)
        }

        private fun bindImage(row: RemoteViews, m: LineMessage) {
            val image = MessageStore.loadBitmap(m.imagePath)?.let(::fit)
            row.setViewVisibility(R.id.widgetImage, if (image != null) View.VISIBLE else View.GONE)
            if (image != null) row.setImageViewBitmap(R.id.widgetImage, image)
            val sticker = image == null && m.pkg == LineListenerService.LINE_PKG && StickerLoader.looksLikeStickerText(m.text)
            row.setViewVisibility(R.id.widgetSticker, if (sticker) View.VISIBLE else View.GONE)
        }

        private fun avatarOf(m: LineMessage): Bitmap {
            val icon = if (PanelRows.isLineGroup(m) || Sources.known(m.pkg) != null) null
                else try { ctx.packageManager.getApplicationIcon(m.pkg).toBitmap(AVATAR_PX, AVATAR_PX) } catch (_: Exception) { null }
            val picture = PanelRows.picture(ctx, m) ?: icon
                ?: AvatarBadge.bitmap(m.sender.ifBlank { m.chatTitle }, AVATAR_PX)
            return Notifier.circle(picture, AVATAR_PX)
        }

        /** Each row crosses a binder call, so full-size pictures have to shrink first. */
        private fun fit(bmp: Bitmap): Bitmap {
            val longest = max(bmp.width, bmp.height)
            if (longest <= IMAGE_PX) return bmp
            val scale = IMAGE_PX.toFloat() / longest
            return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1),
                (bmp.height * scale).roundToInt().coerceAtLeast(1), true)
        }

        private companion object {
            const val AVATAR_PX = 114
            const val IMAGE_PX = 480
        }
    }
}

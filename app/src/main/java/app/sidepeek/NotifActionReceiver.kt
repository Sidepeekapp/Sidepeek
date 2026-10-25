package app.sidepeek

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/**
 * Own receiver for reply / mark-read / dismiss on our stacked notifications.
 * Forwards LINE's original PendingIntent (after filling RemoteInput for reply),
 * then updates MessageStore + cancels our notification. Dismiss only sets a watermark.
 */
class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val chatKey = intent.getStringExtra(Notifier.EXTRA_CHAT_KEY) ?: return
        val acts = MessageStore.lineActionsFor(chatKey)
        when (intent.action) {
            Notifier.ACTION_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val line = acts.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
                val linePi = line?.actionIntent
                val ris = line?.remoteInputs
                val ok = results != null && linePi != null && ris != null && runCatching {
                    val fill = Intent()
                    RemoteInput.addResultsToIntent(ris, fill, results)
                    linePi.send(ctx, 0, fill)
                }.onFailure { Diagnostics.fail(ctx, "REPLY forward chat=$chatKey", it) }.isSuccess
                if (ok) {
                    MessageStore.markRead(ctx, chatKey)
                    Notifier.cancelChat(ctx, chatKey)
                } else {
                    // At least re-post so the system stops the "sending…" spinner.
                    Notifier.refreshChatSilently(ctx, chatKey)
                }
            }
            Notifier.ACTION_MARK_READ -> {
                acts.firstOrNull { Notifier.isMarkRead(it) }?.actionIntent?.let {
                    runCatching { it.send() }
                        .onFailure { e -> Diagnostics.fail(ctx, "MARK_READ forward chat=$chatKey", e) }
                }
                MessageStore.markRead(ctx, chatKey)
                Notifier.cancelChat(ctx, chatKey)
            }
            Notifier.ACTION_DISMISS -> {
                // Watermark only — do NOT markRead (sidebar unread must remain).
                val t = MessageStore.lastTimeForChat(ctx, chatKey)
                if (t > 0L) MessageStore.setNotifWatermark(ctx, chatKey, t)
                Diagnostics.note(ctx, "DISMISS watermark chat=$chatKey t=$t")
            }
        }
    }
}

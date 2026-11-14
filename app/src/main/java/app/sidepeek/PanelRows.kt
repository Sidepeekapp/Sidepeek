package app.sidepeek

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the side panel and the home-screen widget both show for a message. */
object PanelRows {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.TAIWAN)
    private val dateFmt = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN)

    @Synchronized
    fun formatTime(t: Long): String = if (DateUtils.isToday(t)) timeFmt.format(Date(t)) else dateFmt.format(Date(t))

    fun visibleMessages(ctx: Context) =
        MessageStore.all(ctx).filter { m -> Sources.isSelected(ctx, m.pkg) }.sortedByDescending { it.time }

    fun isLineGroup(m: LineMessage) =
        (m.pkg == LineListenerService.LINE_PKG || m.pkg == LineListenerService.LINE_LITE_PKG) &&
            m.sender.isNotBlank() && m.sender != m.chatTitle

    /**
     * LINE groups: show the picture LINE attached to this message as is (the sender's in ordinary
     * groups, the community's in communities), so the row never waits for GroupPicture.
     * Messages saved before linePicPath existed: a community's picture (CHAT) wins, otherwise
     * the member's own icon; until a second person speaks every message is from one sender,
     * so the chat's latest large icon is the one LINE gave them.
     */
    fun picture(ctx: Context, m: LineMessage): Bitmap? =
        if (!isLineGroup(m)) MessageStore.messageAvatar(ctx, m, suppressChatPicture = false)
        else MessageStore.loadBitmap(m.linePicPath) ?: run {
            val verdict = MessageStore.groupPicture(ctx, m.chatKey)
            val chatPic = if (verdict != GroupPicture.Verdict.SENDER)
                MessageStore.loadBitmap(MessageStore.chatAvatarPath(ctx, m.chatKey)) else null
            (if (verdict == GroupPicture.Verdict.CHAT) chatPic else null)
                ?: MessageStore.messageAvatar(ctx, m, suppressChatPicture = false)
                ?: chatPic
        }
}

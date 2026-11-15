package app.sidepeek

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * Test builds only (src/debug, never in release / the Play bundle): four made-up chats for the
 * Play Store screenshots, posted through the real Notifier and MessageStore so the notification
 * shade, side panel and widget show genuine app screens. Started from two launcher shortcuts
 * (src/debug/res/xml/shortcuts.xml). Names match the feature graphic: Tom, Ann, Mia, 「專案」.
 */
object ScreenshotDemo {
    const val ACTION_POST = "app.sidepeek.demo.POST"
    const val ACTION_CLEAR = "app.sidepeek.demo.CLEAR"

    private const val GROUP = "demo|project"
    private const val TOM = "demo|tom"
    private const val ANN = "demo|ann"
    private const val MIA = "demo|mia"
    private val KEYS = listOf(GROUP, TOM, ANN, MIA)

    /** [secondsAgo] sender text */
    private class Line(val secondsAgo: Int, val sender: String, val text: Int)
    private class Chat(val key: String, val title: String, val group: Boolean, val lines: List<Line>)

    /** Oldest chat first, so the newest notification ends up on top. */
    private fun chats(ctx: Context) = listOf(
        Chat(MIA, "Mia", false, listOf(Line(1260, "Mia", R.string.demo_mia1))),
        Chat(GROUP, ctx.getString(R.string.demo_group), true, listOf(
            Line(720, "Tom", R.string.demo_g1),
            Line(600, "Ann", R.string.demo_g2),
            Line(480, "Mia", R.string.demo_g3),
            Line(420, "Tom", R.string.demo_g4),
        )),
        Chat(ANN, "Ann", false, listOf(Line(200, "Ann", R.string.demo_ann1), Line(190, "Ann", R.string.demo_ann2))),
        Chat(TOM, "Tom", false, listOf(Line(40, "Tom", R.string.demo_tom1))),
    )

    /**
     * The pictures are generated from the SVGs in store/avatars (node store/avatars/render.js), the same
     * files the feature graphic (store/feature-graphic/feature.html) shows, so both show the same
     * people. Change the SVGs, not these PNGs.
     */
    private val PERSON_PIC = mapOf(
        "Tom" to R.drawable.demo_avatar_tom, "Ann" to R.drawable.demo_avatar_ann, "Mia" to R.drawable.demo_avatar_mia,
    )
    private val GROUP_PIC = R.drawable.demo_avatar_group
    private const val AVATAR_PX = 192

    private fun picture(ctx: Context, res: Int) = BitmapFactory.decodeResource(ctx.resources, res)

    fun post(ctx: Context) {
        clear(ctx)
        val now = System.currentTimeMillis()
        // Stand-ins for the profile pictures LINE would attach. Every place that draws a person
        // takes these files rather than its own fallback badge, so they match everywhere:
        //   avatarPath  -> group rows in the notification (MessagingStyle Person icon),
        //                  1:1 rows in the side panel and widget (PanelRows.picture)
        //   linePicPath -> group rows in the side panel and widget (PanelRows.picture)
        //   chat avatar -> the 1:1 notification's large icon and the group's header picture
        val avatar = PERSON_PIC.mapValues { (name, res) ->
            MessageStore.saveBitmap(ctx, "av", "demo_" + name.lowercase(), picture(ctx, res), AVATAR_PX)
        }
        for (c in chats(ctx)) {
            MessageStore.saveChatAvatar(ctx, c.key, picture(ctx, if (c.group) GROUP_PIC else PERSON_PIC.getValue(c.title)))
            var last: LineMessage? = null
            c.lines.forEachIndexed { i, l ->
                val text = ctx.getString(l.text)
                val pic = avatar[l.sender]
                val m = LineMessage(
                    id = "demo-$now-${c.key}-$i", pkg = LineListenerService.LINE_PKG, chatKey = c.key,
                    chatTitle = c.title, sender = l.sender, text = text, time = now - l.secondsAgo * 1000L,
                    urls = LinkExtractor.extract(text), avatarPath = pic, linePicPath = pic,
                )
                MessageStore.add(ctx, m); last = m
            }
            // Silent: four sounds and heads-ups in a row would get in the way of the screenshots.
            last?.let { Notifier.postChat(ctx, it, isGroupChat = c.group, silent = true) }
        }
    }

    fun clear(ctx: Context) {
        for (k in KEYS) {
            MessageStore.purgeFiles(ctx, MessageStore.removeChat(ctx, k))
            Notifier.cancelChat(ctx, k)
        }
        // The messages are back-dated (「20 minutes ago」), so a read mark or dismiss watermark left by
        // an earlier run would hide them. These are MessageStore's own preference files.
        for (file in listOf("read_marks", "notif_watermarks")) {
            val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
            p.edit().apply { KEYS.forEach { remove(it) } }.apply()
        }
    }
}

/** Target of the two demo shortcuts; does its job and closes without showing anything. */
class ScreenshotDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ScreenshotDemo.ACTION_CLEAR) {
            ScreenshotDemo.clear(this)
            Toast.makeText(this, R.string.toast_demo_cleared, Toast.LENGTH_SHORT).show()
        } else {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, R.string.toast_test_need_post, Toast.LENGTH_LONG).show()
            }
            ScreenshotDemo.post(this)
            Toast.makeText(this, R.string.toast_demo_posted, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

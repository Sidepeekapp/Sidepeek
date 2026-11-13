package app.sidepeek

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Target of the per-chat conversation shortcut: opens that chat in LINE (or LINE itself). */
class OpenChatActivity : Activity() {
    companion object {
        const val EXTRA_PKG = "app.sidepeek.PKG"
        const val EXTRA_MSG_ID = "app.sidepeek.MSG_ID"
        const val EXTRA_PROFILE = "app.sidepeek.PROFILE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(Notifier.EXTRA_CHAT_KEY)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: LineListenerService.LINE_PKG
        val msgId = intent.getStringExtra(EXTRA_MSG_ID)
        if (!(intent.getBooleanExtra(EXTRA_PROFILE, false) && openProfile(msgId))) {
            val pi = msgId?.let { MessageStore.intentForMessage(it) } ?: key?.let { MessageStore.intentFor(it) }
                ?: key?.let { Notifier.shownIntent(this, it) } ?: Notifier.fallbackIntent(this, pkg)
            try { pi?.let(::send) } catch (_: Exception) {
                try { Notifier.fallbackIntent(this, pkg)?.let(::send) } catch (_: Exception) {}
            }
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    private fun send(pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(this, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
    }

    /** Widget avatar: the sender's profile when we can tell who it is (Threads / IG). */
    private fun openProfile(msgId: String?): Boolean {
        val all = MessageStore.all(this)
        val m = all.firstOrNull { it.id == msgId } ?: return false
        val profile = ProfileLinks.profileIntent(this, m, all) ?: return false
        return try { startActivity(profile); true } catch (_: Exception) {
            try { startActivity(Intent(profile).setPackage(null)); true } catch (_: Exception) { false }
        }
    }
}

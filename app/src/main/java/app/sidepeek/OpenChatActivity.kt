package app.sidepeek

import android.app.Activity
import android.os.Bundle

/** Target of the per-chat conversation shortcut: opens that chat in LINE (or LINE itself). */
class OpenChatActivity : Activity() {
    companion object { const val EXTRA_PKG = "app.sidepeek.PKG" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(Notifier.EXTRA_CHAT_KEY)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: LineListenerService.LINE_PKG
        val pi = key?.let { MessageStore.intentFor(it) } ?: Notifier.fallbackIntent(this, pkg)
        try { pi?.send() } catch (_: Exception) {
            try { Notifier.fallbackIntent(this, pkg)?.send() } catch (_: Exception) {}
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

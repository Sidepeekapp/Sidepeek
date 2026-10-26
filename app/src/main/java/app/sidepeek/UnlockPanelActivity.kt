package app.sidepeek

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Peek-bar swipe on the lock screen: ask the user to unlock first, then open the panel through the
 * normal [OpenPanelActivity] path. The panel itself never appears over the keyguard, so message
 * contents are not readable on a locked phone.
 */
class UnlockPanelActivity : Activity() {
    private var finished = false
    private val giveUp = Runnable { done("timeout") }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) { openPanel(); return }
        // Has to be shown above the keyguard for the unlock prompt (bouncer) to appear.
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        Diagnostics.note(this, "PANEL lock-screen swipe -> requestDismissKeyguard")
        window.decorView.postDelayed(giveUp, 30_000L)
        try {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    // Let the keyguard report "unlocked" before the panel checks it.
                    window.decorView.postDelayed({ openPanel() }, 250)
                }
                override fun onDismissCancelled() { done("cancelled") }
                override fun onDismissError() { done("error") }
            })
        } catch (e: Exception) {
            done("failed: $e")
        }
    }

    private fun openPanel() {
        if (finished) return
        try {
            startActivity(Intent(this, OpenPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Diagnostics.note(this, "PANEL open after unlock failed: $e")
        }
        done("unlocked")
    }

    @Suppress("DEPRECATION")
    private fun done(why: String) {
        if (finished) return
        finished = true
        window.decorView.removeCallbacks(giveUp)
        Diagnostics.note(this, "PANEL unlock request done: $why")
        finish()
        overridePendingTransition(0, 0)
    }
}

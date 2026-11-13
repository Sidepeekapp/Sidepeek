package app.sidepeek

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Invisible trampoline used by the Quick Settings tile. */
class OpenPanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PanelHost.open(this) && !Prefs.a11yConsent(this)) {
            // The accessibility disclosure has to be agreed to inside the app first.
            Toast.makeText(this, getString(R.string.toast_setup_panel_first), Toast.LENGTH_LONG).show()
            try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        } else if (!PanelHost.a11yActive && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                getString(R.string.toast_try_a11y_first),
                Toast.LENGTH_LONG
            ).show()
            try {
                // Samsung / Android 13+: try Accessibility first so the denial unlocks App Info ⋮.
                // Only jump to App Info if the user already tried a11y and still needs Restricted Settings.
                val intent = if (Prefs.a11yTryOpened(this) && !Prefs.a11yAppInfoOpened(this)) {
                    Prefs.set(this, Prefs.KEY_A11Y_APPINFO_OPENED_V1, true)
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                } else {
                    Prefs.set(this, Prefs.KEY_A11Y_TRY_OPENED_V1, true)
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                }
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
        // Note: launched from an app shortcut (e.g. Samsung's edge panel), the home screen is
        // already behind this activity at launch, so the app that was open is not shown again.
        // Neither moveTaskToBack(), finishing 1 s later, nor an opaque theme made a difference.
        // The Quick Settings tile keeps the app that was open; the settings hint points users there.
        Diagnostics.note(this, "PANEL trampoline done (${intent?.action ?: "tile"})")
        finish()
        overridePendingTransition(0, 0)
    }
}


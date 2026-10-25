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
        if (!PanelHost.open(this)) {
            Toast.makeText(
                this,
                "請先試開「Sidepeek 側邊欄」；若被拒絕，關閉後再到 App 資訊 ⋮ 允許受限設定，再回來打開",
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
        finish()
        overridePendingTransition(0, 0)
    }
}

package app.sidepeek

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile "開啟 LINE 側邊欄": opens the panel without touching the screen edge. */
class PanelTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = "開啟訊息側邊欄"
            state = if (PanelHost.running()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        // Go through a tiny invisible Activity: it collapses the shade and, being in the
        // foreground, is always allowed to start the foreground service.
        val i = Intent(this, OpenPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            startActivityAndCollapse(i)
        }
    }
}

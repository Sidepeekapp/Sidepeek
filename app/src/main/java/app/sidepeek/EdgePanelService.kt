package app.sidepeek

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Fallback host for the side panel when the accessibility service is NOT enabled:
 * a foreground service drawing TYPE_APPLICATION_OVERLAY windows. Android then shows our
 * (silent, minimum-importance) FGS notification plus the system "displaying over other apps" one.
 */
class EdgePanelService : Service() {

    companion object {
        private const val FG_ID = 1
        const val ACTION_OPEN = "app.sidepeek.OPEN_PANEL"
        @Volatile var running = false

        fun start(ctx: Context, open: Boolean = false) {
            if (!Settings.canDrawOverlays(ctx)) return
            try {
                val i = Intent(ctx, EdgePanelService::class.java)
                if (open) i.action = ACTION_OPEN
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                Diagnostics.note(ctx, "MODE fallback FGS start failed: $e")
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, EdgePanelService::class.java))
        }
    }

    private var controller: PanelController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        MessageStore.ensureLoaded(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // The accessibility host took over (or the overlay permission is gone): get out of the way,
        // which also removes both persistent notifications.
        if (PanelAccessibilityService.instance != null || !Settings.canDrawOverlays(this)) {
            if (intent?.action == ACTION_OPEN) PanelAccessibilityService.instance?.openPanel()
            stopSelf(); return START_NOT_STICKY
        }
        if (!running) Diagnostics.note(this, "MODE overlay+fgs started (accessibility service not enabled)")
        running = true
        val c = controller ?: PanelController(this, accessibilityMode = false).also { controller = it }
        c.start()
        if (intent?.action == ACTION_OPEN) c.open()
        return START_STICKY
    }

    override fun onDestroy() {
        if (running) Diagnostics.note(this, "MODE overlay+fgs stopped")
        running = false
        controller?.stop(); controller = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPanel = PendingIntent.getService(
            this, 1, Intent(this, EdgePanelService::class.java).setAction(ACTION_OPEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, Notifier.CHANNEL_PANEL)
            .setSmallIcon(R.drawable.ic_stat_msg)
            .setContentTitle("訊息側邊欄運作中")
            .setContentText("開啟無障礙服務即可隱藏此通知；也可在設定中關閉此通知類別")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openApp)
            .addAction(0, "開啟側邊欄", openPanel)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, FG_ID, n, type)
    }
}

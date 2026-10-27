package app.sidepeek

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Preferred host for the side panel. An enabled accessibility service is kept alive by the system,
 * so no foreground service (and no FGS notification) is needed, and TYPE_ACCESSIBILITY_OVERLAY
 * windows do not trigger the system "『Sidepeek』正重疊顯示在其他應用程式上方" notification.
 *
 * Only window-level information is used: which app's activity is in front (to show the handle on
 * the home screen only) and whether a keyboard window exists. Screen text is never read.
 */
class PanelAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: PanelAccessibilityService? = null
            private set
    }

    private var controller: PanelController? = null
    private val activityCache = HashMap<String, Boolean>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    val panelRunning get() = controller?.started == true
    /**
     * Keep home-only detection fresh; Samsung sometimes skips WINDOW_STATE_CHANGED for YouTube/PiP.
     * Runs only while it is useful: 「只在桌面顯示 Peek 條」 on and the screen on. It stops itself
     * otherwise and is re-armed by the next accessibility event (screen on / app switch).
     */
    private var polling = false
    private val fgPoll = object : Runnable {
        override fun run() {
            val c = controller
            if (c == null || !c.started || !pollWanted()) { polling = false; return }
            activeAppPackage()?.let { c.onForegroundApp(it) }
            c.recheckVisibility()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private fun pollWanted(): Boolean =
        Prefs.homeOnly(this) && getSystemService(android.os.PowerManager::class.java)?.isInteractive != false

    private fun ensurePolling() {
        if (polling || controller?.started != true || !pollWanted()) return
        polling = true
        mainHandler.removeCallbacks(fgPoll)
        mainHandler.postDelayed(fgPoll, 1000L)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Diagnostics.note(this, "MODE accessibility connected -> stopping overlay+fgs host")
        Notifier.ensureChannels(this)
        EdgePanelService.stop(this)               // removes FGS + SYSTEM_ALERT_WINDOW windows
        if (Prefs.edgePanel(this)) enablePanel()
    }

    fun enablePanel() {
        val c = controller ?: PanelController(this, accessibilityMode = true).also { controller = it }
        if (!c.started) {
            c.start()
            try { rootInActiveWindow?.packageName?.toString()?.let { c.onForegroundApp(it) } } catch (_: Exception) {}
            try { activeAppPackage()?.let { c.onForegroundApp(it) } } catch (_: Exception) {}
            checkImeWindow()
        }
        polling = false
        ensurePolling()
    }

    fun disablePanel() {
        mainHandler.removeCallbacks(fgPoll)
        polling = false
        controller?.stop()
        controller = null
    }

    fun openPanel() {
        enablePanel()
        controller?.open()
    }

    fun previewPanel(holdMs: Long) {
        enablePanel()
        controller?.preview(holdMs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val c = controller ?: return
        val e = event ?: return
        ensurePolling()
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString()
                val cls = e.className?.toString()
                if (pkg != null && cls != null && isActivity(pkg, cls)) c.onForegroundApp(pkg)
                checkImeWindow()
                c.recheckVisibility()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                checkImeWindow()
                // Always re-read the active app window — Samsung often omits ACTIVE/FOCUSED bits.
                activeAppPackage()?.let { c.onForegroundApp(it) }
                c.recheckVisibility()
            }
        }
    }

    /** Package of the active/focused application window (window root only; no content is read). */
    @Suppress("DEPRECATION")
    private fun activeAppPackage(): String? = try {
        val wins = windows
        val w = wins.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            ?: wins.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            ?: wins.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        w?.root?.let { r -> r.packageName?.toString().also { r.recycle() } }
    } catch (_: Exception) { null }

    private fun checkImeWindow() {
        val c = controller ?: return
        val ime = try { windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } } catch (_: Exception) { return }
        c.onImeWindow(ime)
    }

    /** True if [cls] is an Activity of [pkg] (filters dialogs, popups, toasts, keyboards). */
    private fun isActivity(pkg: String, cls: String): Boolean = activityCache.getOrPut("$pkg/$cls") {
        try { packageManager.getActivityInfo(ComponentName(pkg, cls), 0); true } catch (_: Exception) { false }
    }

    override fun onInterrupt() {}

    /** Back key / back gesture while the panel is open: close it instead of going "back" elsewhere. */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val c = controller
        if (c != null && event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            // Only consume when the panel window is actually open (not just the edge handle).
            if (c.isPanelOpen) {
                if (event.action == android.view.KeyEvent.ACTION_UP) {
                    Diagnostics.note(this, "PANEL BACK via accessibility key filter")
                    c.closePanel()
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown("unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown("destroyed")
        super.onDestroy()
    }

    private fun shutdown(why: String) {
        if (instance !== this) return
        instance = null
        disablePanel()
        Diagnostics.note(this, "MODE accessibility $why")
        // Fall back to overlay+FGS so the panel keeps working (may be refused from background).
        if (Prefs.edgePanel(this)) EdgePanelService.start(this)
    }
}

/** Routes panel start/stop/open to whichever host is available. */
object PanelHost {
    fun a11yEnabledInSettings(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, PanelAccessibilityService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    val a11yActive get() = PanelAccessibilityService.instance != null
    fun running() = PanelAccessibilityService.instance?.panelRunning == true || EdgePanelService.running

    fun start(ctx: Context) {
        val a = PanelAccessibilityService.instance
        if (a != null) { a.enablePanel(); EdgePanelService.stop(ctx); return }
        // Enabled but not yet connected (e.g. right after boot): don't flash the FGS notification.
        if (a11yEnabledInSettings(ctx)) return
        EdgePanelService.start(ctx)
    }

    fun stop(ctx: Context) {
        PanelAccessibilityService.instance?.disablePanel()
        EdgePanelService.stop(ctx)
    }

    /** Live look-only preview of the panel while adjusting its appearance (accessibility host only). */
    fun preview(ctx: Context, holdMs: Long): Boolean {
        val a = PanelAccessibilityService.instance ?: return false
        a.previewPanel(holdMs); return true
    }

    /** @return false if no host could open it (no accessibility service and no overlay permission). */
    fun open(ctx: Context): Boolean {
        PanelAccessibilityService.instance?.let { it.openPanel(); return true }
        if (!Settings.canDrawOverlays(ctx)) return false
        EdgePanelService.start(ctx, open = true)
        return true
    }
}

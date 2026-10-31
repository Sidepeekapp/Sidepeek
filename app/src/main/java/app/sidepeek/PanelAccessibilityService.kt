package app.sidepeek

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Preferred host for the side panel. An enabled accessibility service is kept alive by the system,
 * so no foreground service (and no FGS notification) is needed, and TYPE_ACCESSIBILITY_OVERLAY
 * windows do not trigger the system "『Sidepeek』正重疊顯示在其他應用程式上方" notification.
 *
 * Only window-level information is used: which app's activity is in front (to show the handle on
 * the home screen only) and whether a keyboard window exists. Screen text is not read, except the
 * top bar of LINE screens when the user opts in to 進階清理 (see smartClear).
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
        val e = event ?: return
        // No controller = side panel switched off; LINE notification cleanup must still run.
        val c = controller
        ensurePolling()
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString()
                val cls = e.className?.toString()
                if (pkg != null && cls != null && isActivity(pkg, cls)) {
                    c?.onForegroundApp(pkg)
                    onLineScreen(pkg, cls)
                }
                checkImeWindow()
                c?.recheckVisibility()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (c == null) return
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

    /** The LINE screen (activity class) last in front, or null when another app's screen is. */
    private var lineScreen: String? = null

    /**
     * Opening a chat inside LINE gives us no signal: we already removed LINE's own notification,
     * so LINE has nothing to cancel. Instead, whenever a LINE screen comes to the front (LINE opened,
     * or chat list -> chat), clear our tidied LINE notifications. Messages stay in the side panel.
     */
    private fun onLineScreen(pkg: String, cls: String) {
        val line = pkg == LineListenerService.LINE_PKG || pkg == LineListenerService.LINE_LITE_PKG
        if (!line) { lineScreen = null; return }
        if (cls == lineScreen) return
        lineScreen = cls
        val screen = cls.substringAfterLast('.')
        if (Prefs.clearOnLineOpen(this) && Prefs.smartClear(this)) {
            // Only the chat screen: the chat list's top rows can show other chats' names.
            if (screen == LINE_CHAT_SCREEN) smartClear(cls, screen, 0)
            else Diagnostics.note(this, "LINE screen $screen: smart clear skipped (not a chat)")
            return
        }
        // Screen class name only (which kind of LINE screen); no screen text is read.
        val n = if (Prefs.clearOnLineOpen(this)) Notifier.clearLineChats(this) else 0
        Diagnostics.note(this, "LINE screen $screen: cleared $n tidied notification(s)")
    }

    /** LINE's chat screen (activity simple name, LINE 26.16; seen in the diagnostics log). */
    private val LINE_CHAT_SCREEN = "ChatHistoryActivity"

    /** Retry delays: the chat's top bar may not be drawn yet right after the screen switches. */
    private val smartDelays = longArrayOf(400, 1000, 2000)

    /**
     * 進階清理 (opted in with its own consent): read only the top bar of this LINE screen and clear
     * the one tidied chat it names. Compared in memory against chat names Sidepeek already has; the
     * texts are not kept. No match, or more than one, clears nothing.
     */
    private fun smartClear(cls: String, screen: String, attempt: Int) {
        mainHandler.postDelayed({
            if (lineScreen != cls) return@postDelayed           // already left this screen
            val chats = Notifier.activeLineChatTitles(this)
            if (chats.isEmpty()) {
                if (attempt == 0) Diagnostics.note(this, "LINE screen $screen: smart clear, nothing to clear")
                return@postDelayed
            }
            val texts = topBarTexts()
            val key = ChatTitleMatch.match(texts, chats)
            when {
                key != null -> {
                    Notifier.clearLineChat(this, key)
                    Diagnostics.note(this, "LINE screen $screen: smart clear matched (try ${attempt + 1})" +
                        Diagnostics.sensitive(this, " name=${chats[key]}"))
                }
                attempt + 1 < smartDelays.size -> smartClear(cls, screen, attempt + 1)
                else -> Diagnostics.note(this, "LINE screen $screen: smart clear no match " +
                    "(texts=${texts.size} chats=${chats.size})" +
                    Diagnostics.sensitive(this, " top=$texts names=${chats.values}"))
            }
        }, smartDelays[attempt])
    }

    /** Texts and content descriptions in the top bar (upper ~15%) of the active LINE window only. */
    @Suppress("DEPRECATION")
    private fun topBarTexts(): List<String> {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return emptyList()
        val pkg = root.packageName?.toString()
        if (pkg != LineListenerService.LINE_PKG && pkg != LineListenerService.LINE_LITE_PKG) { root.recycle(); return emptyList() }
        val limit = (resources.displayMetrics.heightPixels * 0.15f).toInt()
        val out = mutableListOf<String>()
        val r = android.graphics.Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (++seen <= 300) {
                n.getBoundsInScreen(r)
                if (r.top < limit) {
                    if (r.bottom <= limit + limit / 3) {
                        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
                        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
                    }
                    for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
                }
            }
            n.recycle()
        }
        return out
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

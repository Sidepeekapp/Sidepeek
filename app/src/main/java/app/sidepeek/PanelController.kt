package app.sidepeek

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.widget.HorizontalScrollView
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * The edge handle + side panel. Hosted either by [PanelAccessibilityService] (windows of type
 * TYPE_ACCESSIBILITY_OVERLAY: no foreground-service notification and no "displaying over other
 * apps" notification) or, as a fallback, by the foreground [EdgePanelService] (TYPE_APPLICATION_OVERLAY).
 *
 * In accessibility mode the WindowManager must be the accessibility service's own one, which is
 * what [ContextWrapper] gives us since the base context is the service itself.
 */
class PanelController(host: Context, val accessibilityMode: Boolean) : ContextWrapper(host) {

    private val wm: WindowManager = host.getSystemService(WindowManager::class.java)
    private val overlayType =
        if (accessibilityMode) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private var handleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var panelRoot: SwipeFrame? = null
    private var panelCard: LinearLayout? = null
    private var listContainer: LinearLayout? = null
    /** Cards built so far: the newest [LIST_PAGE] on open, more as the list is scrolled down. */
    private val LIST_PAGE = 50
    private var listShown = LIST_PAGE
    private var listFooter: View? = null
    private var suppressListRefresh = 0
    private val dismissingViews = mutableSetOf<View>()
    /**
     * Store changes arrive in bursts (a group chat posting several messages, sticker downloads
     * finishing): rebuild the open list at most once per [REFRESH_COALESCE_MS].
     */
    private val storeListener: () -> Unit = {
        if (panelRoot != null) {
            mainHandler.removeCallbacks(coalescedRefresh)
            mainHandler.postDelayed(coalescedRefresh, REFRESH_COALESCE_MS)
        }
    }
    private val coalescedRefresh = Runnable { if (suppressListRefresh == 0) refreshList() }
    private val REFRESH_COALESCE_MS = 120L
    var started = false
        private set

    fun start() {
        if (started) return
        started = true
        Prefs.migrateSamsungHandleDefaultsV1(this)
        Prefs.migrateHandleInsetGestureV1(this)
        MessageStore.ensureLoaded(this)
        log("start (mode=${if (accessibilityMode) "accessibility" else "overlay+fgs"})")
        if (handleView == null) {
            addHandle()
            startVisibilityTracking()
        }
        MessageStore.addListener(storeListener)
    }

    fun stop() {
        if (!started) return
        started = false
        log("stop (mode=${if (accessibilityMode) "accessibility" else "overlay+fgs"})")
        MessageStore.removeListener(storeListener)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        stopVisibilityTracking()
        closePanel(animate = false)
        handleFadeAnim?.cancel(); handleFadeAnim = null
        handleView?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        handleView = null; handleParams = null; handleBar = null
        handleWantVisible = false
    }

    fun open() { if (!started) start(); openPanel() }
    val isPanelOpen get() = panelRoot != null

    /**
     * Live preview while the user adjusts 「側邊欄外觀」: shows the panel without taking touches
     * (the settings sliders underneath keep working) and re-renders on every change.
     * [holdMs] < 0 keeps it up (finger still on a slider); otherwise it closes after [holdMs].
     */
    private var previewMode = false
    private val previewClose = Runnable { if (previewMode) { closePanel(animate = true); previewMode = false } }
    fun preview(holdMs: Long) {
        if (!started) start()
        mainHandler.removeCallbacks(previewClose)
        if (panelRoot != null && !previewMode) return        // user already opened it for real
        if (panelRoot == null) { previewMode = true; openPanel(animate = false, preview = true) }
        if (holdMs >= 0) mainHandler.postDelayed(previewClose, holdMs)
    }

    // ---------------------------------------------------------------- helpers
    /** Last time a lock-screen swipe asked to unlock (stops a re-ask loop if still locked after it). */
    private var unlockAskedAt = 0L
    private val UNLOCK_RETRY_MS = 3000L
    private var openedAt = 0L
    private val OPEN_GUARD_MS = 300L

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds; b.width() to b.height()
        } else {
            val dm = resources.displayMetrics; dm.widthPixels to dm.heightPixels
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    // ---------------------------------------------------------------- home-screen-only visibility
    private var launcherPkgs: Set<String> = emptySet()
    private var foregroundPkg: String? = null
    private var lastEventQuery = 0L
    private var polling = false
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            if (homeOnlyActive()) pollForeground()
            if (Prefs.hideOnIme(this@PanelController)) imeByReflection = reflectImeVisible()
            updateIme()
            applyVisibility()
            mainHandler.postDelayed(this, 700)
        }
    }

    /** Never treat these as "left the home screen": SystemUI (shade, QS, recents bar).
     *  Our own package IS tracked so the handle stays visible inside Sidepeek for adjust/drag. */
    private val IGNORED_FG by lazy {
        setOf("com.android.systemui", "com.samsung.android.app.cocktailbarservice")
    }

    private fun log(msg: String) = Diagnostics.note(this, "PANEL $msg")
    private fun safeAdd(v: View, lp: WindowManager.LayoutParams, what: String): Boolean = try {
        if (v.isAttachedToWindow) { try { wm.removeViewImmediate(v) } catch (_: Exception) {} }
        wm.addView(v, lp); true
    } catch (e: Exception) { log("addView($what) failed: $e"); false }
    private fun safeUpdate(v: View, lp: WindowManager.LayoutParams, what: String) {
        try { wm.updateViewLayout(v, lp) } catch (e: Exception) {
            log("updateViewLayout($what) failed: $e -> re-adding")
            if (!safeAdd(v, lp, what) && v === handleView) { handleView = null; handleParams = null; mainHandler.post { addHandle() } }
        }
    }

    // ---------------------------------------------------------------- keyboard (IME) detection
    @Volatile private var imeVisible = false
    private var imeByInsets: Boolean? = null      // (1) WindowInsets on our overlay windows (API 30+)
    private var imeByResize: Boolean? = null      // (2) adjustResize helper window height shrink
    private var imeByReflection: Boolean? = null  // (3) hidden InputMethodManager API, if allowed
    private var imeByA11y: Boolean? = null        // (4) accessibility getWindows() TYPE_INPUT_METHOD
    private var reflectionWorks: Boolean? = null
    private var imeHelper: View? = null
    private val fullHeights = HashMap<Int, Int>()

    private fun updateIme() {
        // Accessibility mode: the keyboard window list from getWindows() is authoritative. The handle
        // window's insets can report a stale keyboard (e.g. panel opened while the keyboard was
        // closing) and then never update, which hid the Peek bar until something else changed.
        val now = if (accessibilityMode && imeByA11y != null) imeByA11y == true
                  else imeByInsets == true || imeByResize == true || imeByReflection == true || imeByA11y == true
        if (now != imeVisible) {
            imeVisible = now
            Diagnostics.note(this, "IME ${if (now) "shown" else "hidden"} (a11y=$imeByA11y insets=$imeByInsets resize=$imeByResize reflect=$imeByReflection)")
            applyVisibility()
        }
    }

    private fun imeFromInsets(insets: android.view.WindowInsets): Boolean? =
        if (Build.VERSION.SDK_INT >= 30)
            insets.isVisible(android.view.WindowInsets.Type.ime()) ||
                insets.getInsets(android.view.WindowInsets.Type.ime()).bottom > 0
        else null

    private fun attachInsetsListener(v: View) {
        if (Build.VERSION.SDK_INT >= 30) attachInsetsListener30(v)
    }

    @androidx.annotation.RequiresApi(30)
    private fun attachInsetsListener30(v: View) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            imeFromInsets(insets)?.let { if (it != imeByInsets) { imeByInsets = it; mainHandler.post { updateIme() } } }
            view.onApplyWindowInsets(insets)
        }
        v.setWindowInsetsAnimationCallback(object : android.view.WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(insets: android.view.WindowInsets, running: MutableList<android.view.WindowInsetsAnimation>) = insets
            override fun onEnd(animation: android.view.WindowInsetsAnimation) {
                v.rootWindowInsets?.let { imeFromInsets(it) }?.let { imeByInsets = it; mainHandler.post { updateIme() } }
            }
        })
    }

    /** Invisible 1px-wide, full-height window that the system resizes (adjustResize) when the keyboard opens. */
    private fun addImeHelper() {
        // Accessibility mode reads the keyboard window from getWindows(); the helper would need a
        // TYPE_APPLICATION_OVERLAY window (=> "displaying over other apps" notification).
        if (imeHelper != null || accessibilityMode) return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        v.viewTreeObserver.addOnGlobalLayoutListener {
            val (sw, sh) = screenSize()
            if (v.height > 0 && sh > 0) {
                // Compare against the tallest height seen in this orientation (system bars excluded).
                val full = maxOf(fullHeights[sw] ?: 0, v.height).also { fullHeights[sw] = it }
                val shrunk = v.height < full * 0.80f
                if (shrunk != imeByResize) { imeByResize = shrunk; updateIme() }
            }
        }
        attachInsetsListener(v)
        if (safeAdd(v, lp, "ime-helper")) imeHelper = v
    }

    private fun removeImeHelper() {
        imeHelper?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        imeHelper = null; imeByResize = null; imeByInsets = null; imeByReflection = null
        updateIme()
    }

    private fun reflectImeVisible(): Boolean? {
        if (reflectionWorks == false) return null
        return try {
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            val m = imm.javaClass.getMethod("getInputMethodWindowVisibleHeight")
            val h = m.invoke(imm) as Int
            if (reflectionWorks == null) { reflectionWorks = true; Diagnostics.note(this, "IME reflection available") }
            h > 0
        } catch (e: Throwable) {
            if (reflectionWorks == null) Diagnostics.note(this, "IME reflection unavailable: ${e.javaClass.simpleName}")
            reflectionWorks = false; null
        }
    }

    /** Accessibility mode: the foreground app changed (from TYPE_WINDOW_STATE_CHANGED). */
    fun onForegroundApp(pkg: String) {
        if (pkg in IGNORED_FG || pkg == foregroundPkg) return
        if (accessibilityMode) onForegroundLeft(pkg)
        foregroundPkg = pkg
        applyVisibility()
    }

    /** Accessibility mode: keyboard window present in getWindows(). */
    fun onImeWindow(visible: Boolean) {
        if (visible != imeByA11y) { imeByA11y = visible; updateIme() }
    }

    /** Accessibility mode: lock state may have changed (window events on keyguard). */
    fun recheckVisibility() = applyVisibility()

    private fun homeOnlyActive() = Prefs.homeOnly(this) && (accessibilityMode || hasUsageAccess(this))

    private fun keyguardLocked() =
        getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun resolveLaunchers(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val set = HashSet<String>()
        try {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(i, 0).forEach { set += it.activityInfo.packageName }
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { set += it }
        } catch (_: Exception) {}
        set += "com.sec.android.app.launcher"   // Samsung One UI Home
        set -= "com.android.settings"            // FallbackHome during boot, not a real launcher
        set -= "android"                          // "resolver" pseudo-activity
        return set
    }

    private fun startVisibilityTracking() {
        launcherPkgs = resolveLaunchers()
        if (Prefs.hideOnIme(this)) addImeHelper()
        if (screenReceiver == null) {
            screenReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.action) {
                        // Accessibility overlays can appear above the lock screen: never leave the
                        // panel (message contents) or the handle there.
                        Intent.ACTION_SCREEN_OFF -> { stopPolling(); closePanel(animate = false); applyVisibility() }
                        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                            launcherPkgs = resolveLaunchers(); restartVisibilityTracking()
                        }
                    }
                }
            }
            val f = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        restartVisibilityTracking()
    }

    private fun restartVisibilityTracking() {
        stopPolling()
        if (Prefs.hideOnIme(this)) addImeHelper() else removeImeHelper()
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
        if (accessibilityMode) {
            // Foreground app + keyboard come from accessibility events; no polling needed.
            applyVisibility()
        } else if (interactive && (homeOnlyActive() || (Prefs.hideOnIme(this) && reflectionWorks != false))) {
            // Look back a while to learn what is in the foreground right now.
            lastEventQuery = System.currentTimeMillis() - 30 * 60_000L
            foregroundPkg = null
            polling = true
            mainHandler.post(pollRunnable)
        } else applyVisibility()
    }

    private fun stopPolling() {
        polling = false
        mainHandler.removeCallbacks(pollRunnable)
    }

    private fun stopVisibilityTracking() {
        stopPolling()
        removeImeHelper()
        screenReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenReceiver = null
    }

    @Suppress("DEPRECATION")
    private fun pollForeground() {
        try {
            val usm = getSystemService(android.app.usage.UsageStatsManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(lastEventQuery, now)
            val e = android.app.usage.UsageEvents.Event()
            var newest: String? = null
            var newestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                // ACTIVITY_RESUMED == MOVE_TO_FOREGROUND == 1
                if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND && e.timeStamp >= newestTime &&
                    e.packageName !in IGNORED_FG) {
                    newest = e.packageName; newestTime = e.timeStamp
                }
            }
            if (newest != null) foregroundPkg = newest
            lastEventQuery = now - 1000   // small overlap so nothing is missed
        } catch (_: Exception) {}
    }

    /** Show the handle when allowed: panel closed, screen on, and (if enabled) on home; lock screen optional via showOnLock. */
    private fun applyVisibility() {
        // Locked while the panel is open (e.g. Smart Lock / lock without screen-off): close it.
        if (panelRoot != null && keyguardLocked()) {
            log("keyguard locked while panel open -> close")
            closePanel(animate = false)
            return   // closePanel() calls applyVisibility() again with the panel gone
        }
        if (handleView == null || handleParams == null) return
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
        // home-only: unknown foreground must HIDE the handle (was: null+!polling => show, which
        // left the green bar over YouTube when a11y never reported a package yet).
        val onHome = when {
            foregroundPkg == packageName -> true   // always show in Sidepeek so user can drag/adjust
            // Lock screen: treat as an allowed surface when showOnLock (home-only would otherwise hide)
            keyguardLocked() && Prefs.showOnLock(this) -> true
            !homeOnlyActive() -> true
            foregroundPkg == null -> false
            else -> foregroundPkg in launcherPkgs
        }
        val lockedOk = Prefs.showOnLock(this) || !keyguardLocked()
        val show = panelRoot == null && interactive && lockedOk &&
            !(Prefs.hideOnIme(this) && imeVisible) && onHome
        if (show == handleWantVisible && handleFadeAnim == null) {
            // Already at the desired end state — still sync flags if needed.
            syncHandleTouchFlags(show)
            return
        }
        handleWantVisible = show
        animateHandleVisibility(show)
    }

    private fun syncHandleTouchFlags(show: Boolean) {
        val v = handleView ?: return
        val lp = handleParams ?: return
        val wantFlags = if (show) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (lp.flags != wantFlags) {
            lp.flags = wantFlags
            safeUpdate(v, lp, "handle-flags")
        }
    }

    /**
     * Samsung Edge Panel-like progressive fade for the edge handle.
     * Fade-out: immediately NOT_TOUCHABLE, then alpha → 0, then INVISIBLE.
     * Fade-in: VISIBLE + touchable, alpha → user opacity.
     * Rapid IME toggles cancel the previous animator.
     */
    private fun animateHandleVisibility(show: Boolean) {
        val v = handleView ?: return
        val bar = handleBar ?: return
        val lp = handleParams ?: return
        val userAlpha = Prefs.handleOpacity(this) / 100f
        val target = if (show) userAlpha else 0f

        handleFadeAnim?.cancel()
        handleFadeAnim = null

        if (show) {
            v.visibility = View.VISIBLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            safeUpdate(v, lp, "handle-show")
        } else {
            // Do not intercept touches while fading out (Samsung-like).
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            safeUpdate(v, lp, "handle-hide")
            if (v.visibility == View.GONE) {
                bar.alpha = 0f
                v.visibility = View.INVISIBLE
                return
            }
        }

        val start = bar.alpha
        if (kotlin.math.abs(start - target) < 0.02f) {
            bar.alpha = target
            if (!show) v.visibility = View.INVISIBLE
            return
        }

        val anim = ValueAnimator.ofFloat(start, target)
        anim.duration = HANDLE_FADE_MS
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { va ->
            if (handleView !== v) return@addUpdateListener
            bar.alpha = va.animatedValue as Float
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (handleFadeAnim === anim) handleFadeAnim = null
                if (!handleWantVisible && handleView === v) {
                    bar.alpha = 0f
                    v.visibility = View.INVISIBLE
                } else if (handleWantVisible && handleView === v) {
                    bar.alpha = Prefs.handleOpacity(this@PanelController) / 100f
                }
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                if (handleFadeAnim === anim) handleFadeAnim = null
            }
        })
        handleFadeAnim = anim
        anim.start()
    }

    /** Keep FLAG_SHOW_WHEN_LOCKED in sync with Prefs.showOnLock for the edge handle. */
    @Suppress("DEPRECATION")
    private fun syncShowWhenLockedFlag() {
        val v = handleView ?: return
        val lp = handleParams ?: return
        val want = Prefs.showOnLock(this)
        val has = (lp.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0
        if (want == has) return
        lp.flags = if (want) lp.flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                   else lp.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED.inv()
        safeUpdate(v, lp, "handle-show-when-locked")
    }

    // ---------------------------------------------------------------- handle
    private var handleBar: View? = null
    /** Desired handle visibility (IME / home-only / keyguard). Separate from current animating alpha. */
    private var handleWantVisible = false
    private var handleFadeAnim: ValueAnimator? = null
    private val HANDLE_FADE_MS = 280L
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            in Prefs.HANDLE_KEYS -> mainHandler.post { applyHandleStyle() }
            in Prefs.PANEL_KEYS -> mainHandler.post {
                if (panelRoot != null) { val p = previewMode; closePanel(false); previewMode = p; openPanel(animate = false, preview = p) }
            }
            Prefs.KEY_HOME_ONLY, Prefs.KEY_HIDE_ON_IME, Prefs.KEY_SHOW_ON_LOCK -> mainHandler.post { restartVisibilityTracking(); syncShowWhenLockedFlag(); applyVisibility() }
            Prefs.KEY_MSG_GAP -> mainHandler.post { refreshList() }
        }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val isLeft get() = Prefs.handleSide(this) == Prefs.SIDE_LEFT

    /** Touch padding between the screen edge and the bar (only as much as the inset leaves room for). */
    private fun touchPad() = dp(18f)
    /**
     * Touch padding on the inner (screen-content) side. Smaller than the old 18dp so the handle does
     * not cover buttons near the edge (LINE's sticker panel once the keyboard has closed), but 10dp
     * (a 16dp strip by default) proved too hard to hit.
     */
    private fun innerTouchPad() = dp(20f)

    /**
     * Ask the system not to treat touches on the handle as a back gesture (API 29+).
     * Android caps exclusion at 200dp per edge, so the handle height is limited to 200dp.
     * Some versions ignore exclusion for overlay windows; the default inset (8dp) keeps the
     * handle clearer of the gesture zone.
     */
    private fun updateExclusion(v: View) {
        if (Build.VERSION.SDK_INT >= 29) {
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, v.width, v.height))
        }
    }

    private fun applyHandleStyle() {
        val container = handleView ?: return
        val bar = handleBar ?: return
        val lp = handleParams ?: return
        val barW = dp(Prefs.handleWidth(this).toFloat())
        val h = dp(Prefs.handleHeight(this).toFloat())
        val inset = dp(Prefs.handleInset(this).toFloat())
        val outer = min(touchPad(), inset)

        lp.width = outer + barW + innerTouchPad()
        lp.height = h
        lp.gravity = Gravity.TOP or (if (isLeft) Gravity.START else Gravity.END)
        // Window starts `outer` before the bar so the bar itself sits exactly `inset` from the edge.
        lp.x = inset - outer
        val barOffset = outer
        (bar.layoutParams as FrameLayout.LayoutParams).apply {
            width = barW; height = h
            gravity = (if (isLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            leftMargin = if (isLeft) barOffset else 0
            rightMargin = if (isLeft) 0 else barOffset
        }
        // Samsung Edge Panel vibe: frosted / semi-transparent capsule + subtle stroke (not solid green).
        val radius = (barW / 2f).coerceAtLeast(dp(2f).toFloat())
        bar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0x66F2F4F7.toInt())          // frosted light gray ~40%
            setStroke(dp(1f).coerceAtLeast(1), 0x55FFFFFF.toInt())
        }
        // Only snap opacity when fully visible and not mid-fade (fade owns alpha otherwise).
        if (handleWantVisible && handleFadeAnim == null) {
            bar.alpha = Prefs.handleOpacity(this) / 100f
        } else if (!handleWantVisible && handleFadeAnim == null) {
            bar.alpha = 0f
        }
        bar.requestLayout()
        val (_, sh) = screenSize()
        // Re-sync Y from prefs (reset / migration uses -1 → center ~75% thumb zone)
        // Default: handle center ~75% screen height (one-handed thumb zone on the right)
        val defY = (sh * 75 / 100 - h / 2).coerceAtLeast(0)
        val savedY = Prefs.handleY(this)
        lp.y = (if (savedY < 0) defY else savedY).coerceIn(0, (sh - h).coerceAtLeast(0))
        safeUpdate(container, lp, "handle-style")
        container.post { updateExclusion(container) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle() {
        val container = FrameLayout(this)
        val bar = View(this)
        container.addView(bar, FrameLayout.LayoutParams(1, 1))
        handleBar = bar

        val (_, sh) = screenSize()
        val h0 = dp(Prefs.handleHeight(this).toFloat())
        // Default: handle center ~75% screen height (one-handed thumb zone on the right)
        val defY = (sh * 75 / 100 - h0 / 2).coerceAtLeast(0)
        val savedY = Prefs.handleY(this).let { if (it < 0) defY else it }
            .coerceIn(0, (sh - h0).coerceAtLeast(0))
        @Suppress("DEPRECATION")
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (Prefs.showOnLock(this)) WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED else 0)
        val lp = WindowManager.LayoutParams(
            1, 1,
            overlayType,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 0; y = savedY }
        handleParams = lp
        handleView = container

        container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> updateExclusion(v) }
        attachInsetsListener(container)

        // Swipe inward = open (the only way; a tap does nothing, so stray edge taps never open it).
        // Long-press then drag = reposition.
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        // Faster than system default on many devices; still respects a shorter system timeout.
        val longPressMs = min(400L, ViewConfiguration.getLongPressTimeout().toLong())
        // Finger jitter while holding must not cancel long-press (was too strict at scaledTouchSlop).
        val jitterSlop = (slop * 3).coerceAtLeast(dp(24f))
        var downX = 0f; var downY = 0f; var startY = 0
        var dragMode = false; var consumed = false; var moved = false
        var downAt = 0L
        var openDist = dp(24f)
        fun restoreBarVisual(duration: Long = 120L) {
            handleBar?.animate()?.scaleX(1f)?.alpha(Prefs.handleOpacity(this) / 100f)?.setDuration(duration)?.start()
        }
        val enterDrag = Runnable {
            if (!moved && !consumed) {
                dragMode = true
                container.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                handleBar?.animate()?.scaleX(1.8f)?.alpha(1f)?.setDuration(120)?.start()
                Toast.makeText(this, "可以拖了，上下移動調整位置", Toast.LENGTH_SHORT).show()
            }
        }
        fun endDrag() {
            if (dragMode) {
                Prefs.setInt(this, Prefs.KEY_HANDLE_Y, lp.y)
                restoreBarVisual()
            }
            dragMode = false
        }
        container.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startY = lp.y
                    dragMode = false; consumed = false; moved = false
                    downAt = android.os.SystemClock.uptimeMillis()
                    openDist = dp(Prefs.swipeOpenDp(Prefs.swipeSensitivity(this)).toFloat())
                    // Immediate hit feedback so the user knows they touched the strip.
                    handleBar?.animate()?.scaleX(1.15f)?.alpha(1f)?.setDuration(80)?.start()
                    mainHandler.postDelayed(enterDrag, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragMode) {
                        val (_, sh2) = screenSize()
                        lp.y = (startY + dy.toInt()).coerceIn(0, (sh2 - lp.height).coerceAtLeast(0))
                        safeUpdate(container, lp, "handle-drag")
                    } else {
                        val inward = if (isLeft) dx else -dx
                        // Intentional inward swipe opens the panel and cancels long-press.
                        if (!consumed && inward > openDist && abs(dx) > abs(dy)) {
                            moved = true
                            mainHandler.removeCallbacks(enterDrag)
                            restoreBarVisual(80)
                            consumed = true; openPanel()
                        } else if (abs(dx) > jitterSlop || abs(dy) > jitterSlop) {
                            // Clear movement beyond jitter — cancel long-press only.
                            moved = true
                            mainHandler.removeCallbacks(enterDrag)
                            restoreBarVisual(80)
                        }
                        // else: small jitter under threshold — keep waiting for long-press
                    }
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(enterDrag)
                    val heldMs = android.os.SystemClock.uptimeMillis() - downAt
                    if (dragMode) {
                        // Long-press is for reposition only: never open on release
                        // (even if finger did not move after the toast).
                        endDrag()
                    } else if (!consumed && !moved) {
                        restoreBarVisual(80)   // plain tap: no longer opens the panel
                    } else {
                        restoreBarVisual(80)
                        // Held almost long enough then drifted / released without dragMode.
                        if (!consumed && heldMs > 200L) {
                            Toast.makeText(this, "按住不動約半秒再拖", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(enterDrag)
                    if (dragMode) endDrag() else restoreBarVisual(80)
                    dragMode = false
                }
            }
            true
        }
        if (!safeAdd(container, lp, "handle")) { handleView = null; handleParams = null; return }
        applyHandleStyle()
        syncShowWhenLockedFlag()
        applyVisibility()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    // ---------------------------------------------------------------- panel
    fun openPanel(animate: Boolean = true, preview: Boolean = false) {
        if (panelRoot != null) return
        if (!preview) { previewMode = false; mainHandler.removeCallbacks(previewClose) }
        // Never show message contents on the lock screen. With 「鎖定畫面也顯示 Peek 條」 on, a swipe
        // there asks to unlock first and opens the panel afterwards (UnlockPanelActivity).
        if (keyguardLocked()) {
            val now = android.os.SystemClock.uptimeMillis()
            if (!preview && Prefs.showOnLock(this) && now - unlockAskedAt > UNLOCK_RETRY_MS) {
                unlockAskedAt = now
                log("open while locked -> ask to unlock first")
                try {
                    startActivity(Intent(this, UnlockPanelActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: Exception) { log("unlock request failed: $e") }
            }
            log("open refused: device locked")
            Toast.makeText(this, "請先解鎖", Toast.LENGTH_SHORT).show()
            return
        }
        val (sw, sh) = screenSize()
        val textScale = Prefs.panelTextScale(this)
        log("open requested (animate=$animate)")
        openedAt = android.os.SystemClock.uptimeMillis()
        openedWall = System.currentTimeMillis()
        val root = SwipeFrame(this).apply {
            // Dim the rest of the screen so the "tap outside to close" area is obvious.
            setBackgroundColor(if (Prefs.panelScrim(this@PanelController)) 0x66000000 else 0x00000000)
            closeToLeft = isLeft
            guardUntil = openedAt + OPEN_GUARD_MS
            onSwipeClose = { closePanel(animate = true, swiped = true) }
            findItem = { x, y -> itemAt(x, y) }
            onItemDismiss = { v -> dismissCardOneUi(v) }
            // Swipes that start on the header / close zones close the whole panel (never delete a card).
            forceCloseAt = { x, y -> isForceCloseZone(x, y) }
            onBack = { log("BACK via KEYCODE_BACK on panel window"); closePanel() }
            setOnClickListener {
                // Scrim / empty area tap. The opening tap must never close the panel it just opened.
                if (android.os.SystemClock.uptimeMillis() - openedAt >= OPEN_GUARD_MS) closePanel()
            }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF2171717.toInt(), Prefs.panelRadius(this@PanelController).toFloat())
            setPadding(dp(14f), dp(16f), dp(14f), dp(12f))
            isClickable = true // swallow clicks so they don't close the panel
            elevation = dp(8f).toFloat()
        }
        val left = isLeft
        val margin = dp(8f)
        val panelW = (sw * Prefs.panelWidth(this) / 100f).toInt().coerceAtMost(sw - margin)
        val panelH = (sh * Prefs.panelHeight(this) / 100f).toInt()
        val vGravity = when (Prefs.panelPosition(this)) {
            Prefs.POS_TOP -> Gravity.TOP
            Prefs.POS_BOTTOM -> Gravity.BOTTOM
            else -> Gravity.CENTER_VERTICAL
        }
        root.addView(card, FrameLayout.LayoutParams(panelW, panelH,
            (if (left) Gravity.START else Gravity.END) or vGravity).apply {
            val edge = if (Prefs.panelWidth(this@PanelController) >= 100) 0 else margin
            if (left) leftMargin = edge else rightMargin = edge
            // keep clear of status / navigation bar when anchored top/bottom
            if (vGravity == Gravity.TOP) topMargin = dp(28f)
            if (vGravity == Gravity.BOTTOM) bottomMargin = dp(28f)
        })
        card.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> updateExclusion(v) }

        // Header: 「最近訊息」 + count on the left, a quiet 「清除」 on the right. No close button: the
        // panel closes by swiping outward (also on the header), tapping outside or Back.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), 0, dp(2f), dp(10f))
            tag = "close-zone"
            isClickable = true
            var lastTap = 0L
            setOnClickListener {
                // Double-tap the header closes (single tap is swallowed so the scrim click doesn't fire).
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastTap < 350) closePanel() else lastTap = now
            }
        }
        // Usable width inside the card, in dp. Narrow panels (or big text) stack the message meta.
        val contentDp = (panelW - dp(28f)) / resources.displayMetrics.density
        narrowItems = contentDp < 250f * textScale
        val title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 19f * textScale
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        titleView = title
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val clear = TextView(this).apply {
            text = "清除"; setTextColor(0xFFB4B4BA.toInt()); textSize = 13.5f * textScale
            maxLines = 1
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0x00000000)
                setStroke(dp(1f).coerceAtLeast(1), 0x33FFFFFF)
            }
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            contentDescription = "清除全部訊息（可以復原）"
            setOnClickListener { clearAllOneUi() }
        }
        clearButton = clear
        header.addView(clear, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8f) })
        card.addView(header)
        headerBar = header

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> maybeLoadMore(scroll) }
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        listContainer = list
        listShown = LIST_PAGE
        panelCard = card
        root.target = card

        // "已刪除・復原" / "已清除 N 則・復原" undo bar (shown a few seconds after a delete / clear)
        val undo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF3A3A3E.toInt(), 14f)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            visibility = View.GONE
            addView(TextView(this@PanelController).apply {
                text = "已刪除"; setTextColor(0xFFE8E8E8.toInt()); textSize = 14f * textScale
                undoLabel = this
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@PanelController).apply {
                text = "・復原"; setTextColor(0xFF4ADE80.toInt()); textSize = 14f * textScale
                typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { undoDelete() }
        }
        card.addView(undo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })
        undoBar = undo

        // One-time tip so the user knows where to close (cards take most of the panel).
        if (!Prefs.sp(this).getBoolean(Prefs.KEY_PANEL_HINT_SHOWN, false)) {
            val tip = TextView(this).apply {
                text = "點外面、往外滑或按返回鍵關閉・卡片往左滑刪除"
                setTextColor(0xFFB0B0B4.toInt()); textSize = 12f * textScale
                gravity = Gravity.CENTER; setPadding(dp(8f), dp(8f), dp(8f), dp(4f))
                tag = "close-zone"
            }
            card.addView(tip)
            Prefs.sp(this).edit().putBoolean(Prefs.KEY_PANEL_HINT_SHOWN, true).apply()
            mainHandler.postDelayed({
                if (tip.parent != null) (tip.parent as? ViewGroup)?.removeView(tip)
            }, 6000)
        }

        // Focusable so KEYCODE_BACK / the back gesture reaches the panel window while it's open.
        // FLAG_NOT_FOCUSABLE is intentionally omitted; the handle itself stays non-focusable.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            // No FLAG_SHOW_WHEN_LOCKED: the panel (message contents) is never shown over the keyguard.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Preview: look only. Touches go through to the settings page underneath.
                (if (preview) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        refreshList()
        if (!safeAdd(root, lp, "panel")) {
            panelRoot = null; panelCard = null; listContainer = null
            applyVisibility()           // put the handle back
            return
        }
        panelRoot = root
        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.post { if (panelRoot === root) { val ok = root.requestFocus(); log("BACK setup: focus=$ok hasWindowFocus=${root.hasWindowFocus()}") } }
        root.onWindowFocus = { has -> log("BACK window focus=$has") }
        registerBack(root)

        if (animate) {
            // Start hidden only for the slide; the animation itself starts after the first layout,
            // so it never runs on a 0x0 view and is always driven to its end state.
            root.alpha = 0f
            card.visibility = View.INVISIBLE
            card.post {
                if (panelRoot !== root) return@post
                val w = (if (card.width > 0) card.width else panelW).toFloat() + dp(16f)
                card.translationX = if (left) -w else w
                card.visibility = View.VISIBLE
                root.animate().alpha(1f).setDuration(180).withEndAction { root.alpha = 1f }.start()
                card.animate().translationX(0f).setInterpolator(DecelerateInterpolator(2f)).setDuration(260)
                    .withEndAction { card.translationX = 0f }.start()
            }
        }
        applyVisibility()
        log("opened")
        // Watchdog: within 1s the panel must be attached and fully visible, otherwise recover.
        mainHandler.postDelayed({ panelWatchdog(root, card) }, 1000)
    }

    // ---------------------------------------------------------------- swipe-to-delete / close zones
    private var undoBar: View? = null
    private var undoLabel: TextView? = null
    private var headerBar: View? = null
    private var titleView: TextView? = null
    private var clearButton: View? = null
    /** Set when the panel opens: stack the header / message meta when the panel is narrow. */
    private var narrowItems = false

    /** Screen points where a horizontal swipe must close the panel, never delete a card. */
    // ---------------------------------------------------------------- back handling
    private var backCallback: Any? = null

    /** API 33+: predictive-back dispatcher of the panel window (also catches gesture back when focused). */
    private fun registerBack(root: View) {
        if (Build.VERSION.SDK_INT < 33) return
        registerBack33(root)
    }

    @androidx.annotation.RequiresApi(33)
    private fun registerBack33(root: View) {
        root.post {
            try {
                val d = root.findOnBackInvokedDispatcher() ?: run { log("BACK no OnBackInvokedDispatcher"); return@post }
                val cb = android.window.OnBackInvokedCallback { log("BACK via OnBackInvokedCallback"); closePanel() }
                d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb)
                backCallback = d to cb
                log("BACK OnBackInvokedCallback registered")
            } catch (e: Exception) { log("BACK register failed: $e") }
        }
    }

    private fun unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) unregisterBack33()
        backCallback = null
    }

    @androidx.annotation.RequiresApi(33)
    private fun unregisterBack33() {
        @Suppress("UNCHECKED_CAST")
        val p = backCallback as? Pair<android.window.OnBackInvokedDispatcher, android.window.OnBackInvokedCallback> ?: return
        try { p.first.unregisterOnBackInvokedCallback(p.second) } catch (_: Exception) {}
    }

    /** Accessibility fallback: the user left to another app while the panel was open (e.g. back/home). */
    fun onForegroundLeft(pkg: String) {
        if (panelRoot != null && System.currentTimeMillis() - openedWall > 600) {
            log("BACK/nav fallback: foreground changed to $pkg while open -> close")
            closePanel(animate = false)
        }
    }
    private var openedWall = 0L

    private fun isForceCloseZone(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        for (v in listOfNotNull(headerBar)) {
            if (v.visibility != View.VISIBLE) continue
            v.getLocationOnScreen(loc)
            val pad = dp(12f)
            if (x >= loc[0] - pad && x < loc[0] + v.width + pad &&
                y >= loc[1] - pad && y < loc[1] + v.height + pad) return true
        }
        return false
    }

    private var lastDeleted: LineMessage? = null
    /** Messages removed by 「清除」 that can still be restored from the undo bar. */
    private var clearedAll: List<LineMessage>? = null
    private val UNDO_SINGLE_MS = 4000L
    private val UNDO_CLEAR_MS = 5000L
    private val CLEAR_STAGGER_MAX = 15
    private val hideUndo = Runnable { finishUndoWindow() }

    /** The message card under a screen point, if any (used by SwipeFrame). */
    private fun itemAt(x: Float, y: Float): View? {
        val list = listContainer ?: return null
        val loc = IntArray(2)
        for (i in 0 until list.childCount) {
            val c = list.getChildAt(i)
            if (c.tag !is LineMessage || c.visibility != View.VISIBLE || c in dismissingViews) continue
            c.getLocationOnScreen(loc)
            if (x >= loc[0] && x < loc[0] + c.width && y >= loc[1] && y < loc[1] + c.height) return c
        }
        return null
    }

    /**
     * One UI-style card dismiss: card is already flying off (or we finish the fly-off), then the
     * vertical gap collapses so cards below slide up, then the store is updated (refresh suppressed).
     */
    private fun dismissCardOneUi(v: View) {
        val m = v.tag as? LineMessage ?: return
        if (v in dismissingViews) return
        dismissingViews.add(v)
        v.isClickable = false
        // One UI: fly off to the LEFT, then collapse vertical space.
        val endX = -(v.width.coerceAtLeast(1) + dp(48f)).toFloat()
        val flyMs = 220L
        v.animate().cancel()
        v.animate().translationX(endX).alpha(0f)
            .setDuration(flyMs).setInterpolator(DecelerateInterpolator())
            .withEndAction { collapseCardThenDelete(v, m) }.start()
    }

    private fun collapseCardThenDelete(v: View, m: LineMessage) {
        val lp = (v.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, v.height)
        val startH = if (v.height > 0) v.height else lp.height.coerceAtLeast(1)
        val startMargin = lp.bottomMargin
        lp.height = startH
        v.layoutParams = lp
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.duration = 200
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { va ->
            val f = va.animatedValue as Float
            lp.height = (startH * f).toInt()
            lp.bottomMargin = (startMargin * f).toInt()
            v.layoutParams = lp
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                (v.parent as? ViewGroup)?.removeView(v)
                dismissingViews.remove(v)
                deleteMessage(m, animated = true)
                ensureListEmptyState()
            }
        })
        anim.start()
    }

    private fun ensureListEmptyState() {
        val list = listContainer ?: return
        val has = (0 until list.childCount).any { list.getChildAt(it).tag is LineMessage }
        if (!has) refreshList()
    }

    /** 「最近訊息  12」: count in smaller gray text; 「清除」 only when there is something to clear. */
    private fun updateHeader(count: Int) {
        val t = titleView ?: return
        val sb = android.text.SpannableStringBuilder("最近訊息")
        if (count > 0) {
            val start = sb.length
            sb.append("  ").append(count.toString())
            sb.setSpan(android.text.style.RelativeSizeSpan(0.72f), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.ForegroundColorSpan(0xFF8E8E93.toInt()), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.StyleSpan(Typeface.NORMAL), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        t.text = sb
        // While a clear can still be undone, keep the button hidden too (the undo bar is the action).
        clearButton?.visibility = if (count > 0 && clearedAll == null) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 「清除」: cards fly off in a staggered One UI-like cascade, then everything is removed from the
     * store but can be restored from the undo bar for [UNDO_CLEAR_MS]. Only when that window ends
     * (or the panel closes) are images / avatars / the diagnostics log deleted and our LINE
     * notifications updated, so an accidental tap costs nothing.
     */
    private fun clearAllOneUi() {
        val list = listContainer ?: return
        if (clearedAll != null) return
        finishUndoWindow()                      // a previous single delete becomes final
        val cards = (0 until list.childCount).map { list.getChildAt(it) }.filter { it.tag is LineMessage }
        suppressListRefresh++
        cards.forEach { dismissingViews.add(it); it.isClickable = false }
        cards.forEachIndexed { i, v ->
            mainHandler.postDelayed({
                if (panelRoot == null) return@postDelayed
                val endX = -(v.width.coerceAtLeast(1) + dp(48f)).toFloat()
                v.animate().cancel()
                v.animate().translationX(endX).alpha(0f)
                    .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            }, min(i, CLEAR_STAGGER_MAX) * 45L)
        }
        // Cards past the first screenful leave together, so clearing 200 loaded cards stays quick.
        val doneAt = if (cards.isEmpty()) 0L else min(cards.size - 1, CLEAR_STAGGER_MAX) * 45L + 260L
        mainHandler.postDelayed({
            val removed = MessageStore.removeAll(this@PanelController)
            dismissingViews.clear()
            suppressListRefresh = (suppressListRefresh - 1).coerceAtLeast(0)
            if (removed.isEmpty()) { refreshList(); return@postDelayed }
            if (panelRoot == null) {
                // Panel closed during the animation: no undo bar to show, make it final now.
                finalizeClear(removed); return@postDelayed
            }
            clearedAll = removed
            val shown = removed.count { Sources.isSelected(this@PanelController, it.pkg) }
            Diagnostics.note(this@PanelController, "PANEL cleared ${removed.size} message(s) (undo window open)")
            refreshList()
            showUndo("已清除 $shown 則", UNDO_CLEAR_MS)
        }, doneAt)
    }

    private fun deleteMessage(m: LineMessage, animated: Boolean = false) {
        finishUndoWindow()                      // a previous delete becomes final
        if (animated) suppressListRefresh++
        val removed = MessageStore.remove(this, m.id) ?: run {
            if (animated) suppressListRefresh = (suppressListRefresh - 1).coerceAtLeast(0)
            return
        }
        if (animated) suppressListRefresh = (suppressListRefresh - 1).coerceAtLeast(0)
        lastDeleted = removed
        Diagnostics.note(this, "PANEL deleted message id=${m.id} chat=${m.chatKey}")
        updateChatNotification(removed)
        showUndo("已刪除", UNDO_SINGLE_MS)
    }

    private fun showUndo(label: String, ms: Long) {
        undoLabel?.text = label
        undoBar?.let { it.visibility = View.VISIBLE; it.alpha = 0f; it.animate().alpha(1f).setDuration(150).start() }
        mainHandler.removeCallbacks(hideUndo)
        mainHandler.postDelayed(hideUndo, ms)
    }

    private fun undoDelete() {
        mainHandler.removeCallbacks(hideUndo)
        undoBar?.visibility = View.GONE
        clearedAll?.let { all ->
            clearedAll = null
            MessageStore.restoreAll(this, all)
            Diagnostics.note(this, "PANEL undo clear (${all.size} message(s))")
            refreshList()
            return
        }
        val m = lastDeleted ?: return
        lastDeleted = null
        MessageStore.restore(this, m)
        Diagnostics.note(this, "PANEL undo delete id=${m.id}")
        updateChatNotification(m)
    }

    /** The undo window ended (timeout, another delete, panel closed): make deletions final. */
    private fun finishUndoWindow() {
        mainHandler.removeCallbacks(hideUndo)
        lastDeleted?.let { MessageStore.purgeFiles(this, listOf(it)) }
        lastDeleted = null
        clearedAll?.let { all -> clearedAll = null; finalizeClear(all) }
        undoBar?.visibility = View.GONE
        if (listContainer != null) updateHeader(MessageStore.all(this).count { Sources.isSelected(this, it.pkg) })
    }

    /** After 「清除」: delete the cleared messages' files and the diagnostics log (it holds message text). */
    private fun finalizeClear(all: List<LineMessage>) {
        MessageStore.purgeFiles(this, all)
        Diagnostics.clear(this)
        Diagnostics.note(this, "PANEL clear final: ${all.size} message(s), images/avatars and diagnostics log deleted")
        try { Notifier.refreshAllSilently(this) } catch (_: Exception) {}
    }

    /** Keep our stacked LINE notification in sync with the deleted / restored message. */
    private fun updateChatNotification(m: LineMessage) {
        if (Sources.forPackage(m.pkg) != Sources.LINE || !Prefs.enabled(this)) return
        try { Notifier.refreshChatSilently(this, m.chatKey) } catch (e: Exception) { log("notif refresh failed: $e") }
    }

    private fun panelWatchdog(root: SwipeFrame, card: View) {
        if (panelRoot !== root) return
        if (!root.isAttachedToWindow || root.width == 0 || card.width == 0) {
            log("watchdog: panel not attached/laid out (attached=${root.isAttachedToWindow} w=${root.width}) -> restore handle")
            panelRoot = null; panelCard = null; listContainer = null
            try { wm.removeViewImmediate(root) } catch (_: Exception) {}
            applyVisibility()
            return
        }
        if (!root.dragging && (root.alpha < 0.99f || card.translationX != 0f || card.visibility != View.VISIBLE)) {
            log("watchdog: panel stuck (alpha=${root.alpha} tx=${card.translationX}) -> forcing visible")
            root.animate().cancel(); card.animate().cancel()
            root.alpha = 1f; card.translationX = 0f; card.visibility = View.VISIBLE
        }
    }

    fun closePanel(animate: Boolean = true, swiped: Boolean = false) {
        val root = panelRoot ?: return
        panelRoot = null
        val card = panelCard
        panelCard = null; listContainer = null
        finishUndoWindow(); undoBar = null; undoLabel = null; headerBar = null; titleView = null; clearButton = null
        dismissingViews.clear(); suppressListRefresh = 0
        unregisterBack()
        log("close (animate=$animate swiped=$swiped)")
        applyVisibility()
        var removed = false
        val remove = { if (!removed) { removed = true; try { wm.removeView(root) } catch (_: Exception) {} } }
        if (!animate || card == null) { remove(); return }
        val off = card.width.toFloat() + dp(16f)
        card.animate().translationX(if (isLeft) -off else off).setDuration(if (swiped) 140 else 200).start()
        root.animate().alpha(0f).setDuration(if (swiped) 140 else 200).withEndAction { remove() }.start()
        mainHandler.postDelayed({ remove() }, 600)   // in case the animation is cancelled
    }

    private fun pill(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(0xFFE8E8E8.toInt()); textSize = 13f
        maxLines = 1
        background = rounded(0xFF333336.toInt(), 16f)
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        setOnClickListener { onClick() }
    }

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.TAIWAN)
    private val dateFmt = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN)
    private fun formatTime(t: Long) = if (DateUtils.isToday(t)) timeFmt.format(Date(t)) else dateFmt.format(Date(t))

    private fun visibleMessages() =
        MessageStore.all(this).filter { m -> Sources.isSelected(this, m.pkg) }.sortedByDescending { it.time }

    private fun refreshList() {
        val list = listContainer ?: return
        list.removeAllViews()
        listFooter = null
        val visible = visibleMessages()
        val msgs = visible.take(listShown)
        updateHeader(visible.size)
        if (msgs.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "目前沒有訊息。\n收到 LINE、Messenger、IG 等訊息後會顯示在這裡。"
                setTextColor(0xFF9A9A9E.toInt()); textSize = 14f; gravity = Gravity.CENTER
                setPadding(0, dp(48f), 0, 0)
            })
            return
        }
        for (m in msgs) list.addView(itemView(m), itemParams())
        addListFooter(visible.size - msgs.size)
    }

    private fun itemParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(when (Prefs.msgGap(this@PanelController)) { Prefs.GAP_NONE -> 3f; Prefs.GAP_LARGE -> 14f; else -> 8f })
    }

    /** 「再載入 N 則」 under the last card when older messages are not built yet (also auto-loads on scroll). */
    private fun addListFooter(remaining: Int) {
        val list = listContainer ?: return
        if (remaining <= 0) return
        val footer = TextView(this).apply {
            text = "再載入 ${min(remaining, LIST_PAGE)} 則（還有 $remaining 則）"
            setTextColor(0xFF16A34A.toInt()); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, dp(16f))
            setOnClickListener { loadMoreMessages() }
        }
        list.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        listFooter = footer
    }

    /** Appends the next [LIST_PAGE] older messages without rebuilding the cards already shown. */
    private fun loadMoreMessages() {
        val list = listContainer ?: return
        if (listFooter == null || suppressListRefresh > 0 || clearedAll != null) return
        val visible = visibleMessages()
        val from = listShown.coerceAtMost(visible.size)
        listShown += LIST_PAGE
        val more = visible.subList(from, listShown.coerceAtMost(visible.size))
        listFooter?.let { list.removeView(it) }
        listFooter = null
        for (m in more) list.addView(itemView(m), itemParams())
        addListFooter(visible.size - from - more.size)
    }

    /** Auto-load when the list is scrolled to within about one screen of the footer. */
    private fun maybeLoadMore(scroll: ScrollView) {
        val footer = listFooter ?: return
        if (scroll.scrollY + scroll.height >= footer.top - scroll.height) loadMoreMessages()
    }

    private fun itemView(m: LineMessage): View {
        val ts = Prefs.panelTextScale(this)
        // Chat-app look: avatar on the left, chat name + time on top, the message in a bubble.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = rounded(0x00000000, 16f)
            val hp = if (narrowItems) 2f else 6f
            setPadding(dp(hp), dp(8f), dp(hp), dp(8f))
            setOnClickListener { openMessage(m) }
        }
        row.tag = m
        val av = if (narrowItems) 32f else 38f
        val isLineGroup = (m.pkg == LineListenerService.LINE_PKG || m.pkg == LineListenerService.LINE_LITE_PKG) &&
            m.sender.isNotBlank() && m.sender != m.chatTitle
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setOnClickListener { openProfile(m) }
            contentDescription = "開啟個人頁面"
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
            // LINE groups: show the picture LINE attached to this message as is (the sender's in ordinary
            // groups, the community's in communities), so the row never waits for GroupPicture.
            // Messages saved before linePicPath existed: a community's picture (CHAT) wins, otherwise
            // the member's own icon; until a second person speaks every message is from one sender,
            // so the chat's latest large icon is the one LINE gave them.
            val bmp = if (!isLineGroup) MessageStore.messageAvatar(this@PanelController, m, suppressChatPicture = false)
            else MessageStore.loadBitmap(m.linePicPath) ?: run {
                val verdict = MessageStore.groupPicture(this@PanelController, m.chatKey)
                val chatPic = if (verdict != GroupPicture.Verdict.SENDER)
                    MessageStore.loadBitmap(MessageStore.chatAvatarPath(this@PanelController, m.chatKey)) else null
                (if (verdict == GroupPicture.Verdict.CHAT) chatPic else null)
                    ?: MessageStore.messageAvatar(this@PanelController, m, suppressChatPicture = false)
                    ?: chatPic
            }
            val appIcon = if (bmp == null && !isLineGroup && Sources.known(m.pkg) == null)
                try { packageManager.getApplicationIcon(m.pkg) } catch (_: Exception) { null } else null
            when {
                bmp != null -> setImageBitmap(bmp)
                appIcon != null -> setImageDrawable(appIcon)   // user-picked app without an avatar
                // No picture: the same initial badge (fixed palette) the notification uses.
                else -> setImageBitmap(AvatarBadge.bitmap(m.sender.ifBlank { m.chatTitle }, dp(av * ts)))
            }
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(av * ts), dp(av * ts)).apply {
            rightMargin = dp(if (narrowItems) 8f else 10f); topMargin = dp(2f)
        })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleView = TextView(this).apply {
            text = m.chatTitle; setTextColor(Color.WHITE); textSize = 14f * ts; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        // Narrow panel: chat name gets the whole first line; app badge + time go on the line below.
        val meta = if (narrowItems) LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, 0)
        } else top
        if (narrowItems) col.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        else top.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        Sources.forPackage(this, m.pkg)?.let { src ->
            meta.addView(TextView(this).apply {
                text = src.badge; setTextColor(Color.WHITE); textSize = 10.5f * ts
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(src.color, 8f)
                setPadding(dp(6f), dp(1f), dp(6f), dp(1f))
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (!narrowItems) leftMargin = dp(6f)
            })
        }
        meta.addView(TextView(this).apply {
            text = formatTime(m.time); setTextColor(0xFF8E8E93.toInt()); textSize = 12f * ts
            setPadding(dp(8f), 0, 0, 0); maxLines = 1
        })
        col.addView(if (narrowItems) meta else top)
        if (m.sender.isNotBlank() && m.sender != m.chatTitle) {
            col.addView(TextView(this).apply {
                text = m.sender; setTextColor(0xFF4ADE80.toInt()); textSize = 12.5f * ts
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2f), 0, 0)
            })
        }
        // Speech bubble: sharp top-left corner points at the avatar, like a received chat message.
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF2E3136.toInt())
                val r = dp(18f).toFloat(); val t = dp(4f).toFloat()
                cornerRadii = floatArrayOf(t, t, r, r, r, r, r, r)
            }
            setPadding(dp(12f), dp(9f), dp(12f), dp(10f))
        }
        bubble.addView(TextView(this).apply {
            // Long links shown short; the link chips below open / copy the full URL.
            text = LinkExtractor.shortenInText(m.text); setTextColor(0xFFF2F2F7.toInt()); textSize = 14.5f * ts
            maxLines = Prefs.panelMaxLines(this@PanelController); ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2f).toFloat(), 1f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Same notification re-posted N times (bubble 「有新消息。」): a counter under the text,
        // so it never competes with the name/badge/time row for width.
        if (m.repeat > 1) {
            bubble.addView(TextView(this).apply {
                text = "共 ${m.repeat} 則"; textSize = 11.5f * ts; typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF052E16.toInt()); maxLines = 1
                background = rounded(0xFF4ADE80.toInt(), 999f)
                setPadding(dp(8f), dp(1f), dp(8f), dp(2f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
        }
        val bmp = MessageStore.loadBitmap(m.imagePath)
        if (bmp != null) {
            bubble.addView(ImageView(this).apply {
                setImageBitmap(bmp); adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                maxHeight = dp(180f)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) =
                        outline.setRoundRect(0, 0, view.width, view.height, dp(12f).toFloat())
                }
                clipToOutline = true
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
        } else if (m.pkg == LineListenerService.LINE_PKG && StickerLoader.looksLikeStickerText(m.text)) {
            // P0-3: LINE-only glyph placeholder when sticker text but enrich failed / no imagePath.
            bubble.addView(TextView(this).apply {
                text = "🎟  貼圖"
                setTextColor(0xFFE8E8ED.toInt())
                textSize = 16f * ts
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = rounded(0xFF3A3A3F.toInt(), 14f)
                setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
        }
        if (m.urls.isNotEmpty()) {
            bubble.addView(linkChipRow(m.urls, ts), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
        }
        col.addView(bubble, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4f) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun linkChipRow(urls: List<String>, ts: Float): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, url) in urls.withIndex()) {
            val chip = TextView(this).apply {
                text = "🔗 " + LinkExtractor.hostLabel(url)
                setTextColor(0xFF0A84FF.toInt())
                textSize = 12.5f * ts
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(0xFF1C3A5F.toInt(), 999f)
                setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
                setOnClickListener {
                    openUrl(url)
                }
                setOnLongClickListener {
                    copyUrl(url)
                    true
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) leftMargin = dp(6f) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun openUrl(url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "無法開啟連結", Toast.LENGTH_SHORT).show()
            Diagnostics.note(this, "openUrl fail $url: $e")
        }
        closePanel()
    }

    private fun copyUrl(url: String) {
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("url", url))
            Toast.makeText(this, "已複製連結", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "複製失敗", Toast.LENGTH_SHORT).show()
        }
    }

    /** Avatar tap: the sender's profile when we can tell who it is (Threads / IG), else the message. */
    private fun openProfile(m: LineMessage) {
        val intent = ProfileLinks.profileIntent(this, m, MessageStore.all(this)) ?: return openMessage(m)
        closePanel()
        try { startActivity(intent) } catch (_: Exception) {
            // App not installed / cannot handle it -> open the same page in the browser.
            try { startActivity(Intent(intent).setPackage(null)) } catch (_: Exception) { openMessage(m) }
        }
    }

    private fun openMessage(m: LineMessage) {
        val pi = MessageStore.intentForMessage(m.id) ?: MessageStore.intentFor(m.chatKey) ?: Notifier.fallbackIntent(this, m.pkg)
        closePanel()
        if (pi == null) return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(this, 0, null, null, null, null, opts.toBundle())
            } else {
                pi.send()
            }
        } catch (_: PendingIntent.CanceledException) {
            // LINE's intent expired (e.g. after LINE updated) -> just open LINE.
            try { Notifier.fallbackIntent(this, m.pkg)?.send() } catch (_: Exception) {}
        }
    }
}


/**
 * Full-screen panel container.
 *  - Swipe LEFT on a message card → delete (One UI fly-out + collapse).
 *  - Swipe RIGHT (outward for a right-edge panel; mirrored when panel is on the left) on the
 *    panel / header / empty area → close the whole panel with follow-finger + spring-back.
 *  - Vertical drags still scroll the list. Back key closes.
 */
class SwipeFrame(ctx: Context) : FrameLayout(ctx) {
    var onSwipeClose: (() -> Unit)? = null
    var closeToLeft = false
    var onBack: (() -> Unit)? = null
    var onWindowFocus: ((Boolean) -> Unit)? = null

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        onWindowFocus?.invoke(hasWindowFocus)
    }
    var target: View? = null
    var guardUntil = 0L
    /** Returns the message card under a raw screen point (left-swipe starts delete). */
    var findItem: ((Float, Float) -> View?)? = null
    var onItemDismiss: ((View) -> Unit)? = null
    /** When true at ACTION_DOWN, the drag closes the panel even if a message card is under the finger. */
    var forceCloseAt: ((Float, Float) -> Boolean)? = null
    private var downItem: View? = null
    private var itemMode = false
    private var forceClose = false
    var dragging = false
        private set
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val flingMin = 700 * ctx.resources.displayMetrics.density   // px/s
    private var downX = 0f
    private var downY = 0f
    private var vt: VelocityTracker? = null

    /** Positive when the finger moves in the panel-close direction (right for right-edge panel). */
    private fun outward(dx: Float) = if (closeToLeft) -dx else dx
    /** Positive when the finger moves left (card-delete direction). */
    private fun leftward(dx: Float) = -dx

    private fun track(ev: MotionEvent) {
        val e = MotionEvent.obtain(ev); e.setLocation(ev.rawX, ev.rawY)
        (vt ?: VelocityTracker.obtain().also { vt = it }).addMovement(e); e.recycle()
    }

    /** 0 = none, 1 = delete card (left), 2 = close panel (outward / right on default side). */
    private fun classify(ev: MotionEvent): Int {
        if (android.os.SystemClock.uptimeMillis() < guardUntil) return 0
        val dx = ev.rawX - downX; val dy = ev.rawY - downY
        if (abs(dx) <= slop || abs(dx) <= 1.5f * abs(dy)) return 0
        if (!forceClose && downItem != null && leftward(dx) > 0) return 1
        if (outward(dx) > 0) return 2
        return 0
    }

    private fun begin(ev: MotionEvent) {
        downX = ev.rawX; downY = ev.rawY; dragging = false; itemMode = false
        forceClose = forceCloseAt?.invoke(ev.rawX, ev.rawY) == true
        downItem = if (forceClose) null else findItem?.invoke(ev.rawX, ev.rawY)
        vt?.recycle(); vt = null; track(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging) {
                    when (classify(ev)) {
                        1 -> { dragging = true; itemMode = true; parent?.requestDisallowInterceptTouchEvent(true); return true }
                        2 -> { dragging = true; itemMode = false; parent?.requestDisallowInterceptTouchEvent(true); return true }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) { vt?.recycle(); vt = null }
        }
        return dragging
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val card = target
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { begin(ev); super.onTouchEvent(ev); return true }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging) {
                    when (classify(ev)) {
                        1 -> {
                            dragging = true; itemMode = true
                            val c = MotionEvent.obtain(ev); c.action = MotionEvent.ACTION_CANCEL; super.onTouchEvent(c); c.recycle()
                        }
                        2 -> {
                            dragging = true; itemMode = false
                            val c = MotionEvent.obtain(ev); c.action = MotionEvent.ACTION_CANCEL; super.onTouchEvent(c); c.recycle()
                        }
                    }
                }
                val item = downItem
                if (dragging && itemMode && item != null) {
                    val o = leftward(ev.rawX - downX).coerceAtLeast(0f)
                    item.translationX = -o
                    item.alpha = (1f - o / item.width.coerceAtLeast(1) * 0.9f).coerceIn(0.1f, 1f)
                    return true
                }
                if (dragging && card != null) {
                    val o = outward(ev.rawX - downX).coerceAtLeast(0f)
                    card.translationX = if (closeToLeft) -o else o
                    return true
                }
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                track(ev)
                val item = downItem
                if (itemMode && item != null) {
                    val vLeft = vt?.let { it.computeCurrentVelocity(1000); leftward(it.xVelocity) } ?: 0f
                    val o = leftward(ev.rawX - downX)
                    dragging = false; itemMode = false; vt?.recycle(); vt = null
                    val iw = item.width.coerceAtLeast(1)
                    if (o > iw * 0.35f || (vLeft > flingMin && o > slop)) {
                        onItemDismiss?.invoke(item)
                    } else {
                        item.animate().translationX(0f).alpha(1f).setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                    }
                    return true
                }
                val vOut = vt?.let { it.computeCurrentVelocity(1000); outward(it.xVelocity) } ?: 0f
                val o = outward(ev.rawX - downX)
                val w = (card?.width ?: width).coerceAtLeast(1)
                dragging = false; vt?.recycle(); vt = null
                if (o > w * 0.30f || (vOut > flingMin && o > slop)) onSwipeClose?.invoke()
                else card?.animate()?.translationX(0f)?.setDuration(180)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator(1.2f))?.start()
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) {
                downItem?.takeIf { itemMode }?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(180)?.start()
                dragging = false; itemMode = false; vt?.recycle(); vt = null
                card?.animate()?.translationX(0f)?.setDuration(180)?.start()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

package app.sidepeek

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "settings"
    const val KEY_ENABLED = "enabled"
    const val KEY_HIDE_ORIGINAL = "hide_original"
    const val KEY_EDGE_PANEL = "edge_panel"
    const val KEY_HANDLE_Y = "handle_y"
    const val KEY_HANDLE_SIDE = "handle_side"
    const val KEY_HANDLE_INSET = "handle_inset_dp"
    const val KEY_HANDLE_WIDTH = "handle_width_dp"
    const val KEY_HANDLE_HEIGHT = "handle_height_dp"
    const val KEY_HANDLE_OPACITY = "handle_opacity_pct"
    /** How far an inward swipe on the handle must travel to open the panel: level 1 (low) .. 5 (high). */
    const val KEY_SWIPE_SENS = "handle_swipe_sensitivity"
    val HANDLE_KEYS = setOf(KEY_HANDLE_SIDE, KEY_HANDLE_INSET, KEY_HANDLE_WIDTH, KEY_HANDLE_HEIGHT, KEY_HANDLE_OPACITY, KEY_HANDLE_Y, KEY_SWIPE_SENS)

    const val SIDE_RIGHT = "right"
    const val SIDE_LEFT = "left"

    const val INSET_MAX = 40
    const val WIDTH_MIN = 4;  const val WIDTH_MAX = 24
    const val HEIGHT_MIN = 40; const val HEIGHT_MAX = 200   // Android caps gesture exclusion at 200dp/edge
    const val OPACITY_MIN = 0; const val OPACITY_MAX = 100   // 0 = invisible (not in screenshots) but still swipeable

    // Samsung Edge Panel–like defaults (right edge, close, frosted readable)
    const val DEF_HANDLE_SIDE = SIDE_RIGHT
    const val DEF_HANDLE_INSET = 0
    const val DEF_HANDLE_WIDTH = 6
    const val DEF_HANDLE_HEIGHT = 110
    const val DEF_HANDLE_OPACITY = 55
    const val SENS_MIN = 1; const val SENS_MAX = 5; const val DEF_SWIPE_SENS = 3
    /** Inward swipe distance (dp) per sensitivity level; level 3 = 24dp, the old fixed value. */
    private val SWIPE_DP = intArrayOf(48, 36, 24, 16, 10)
    fun swipeSensitivity(ctx: Context) = sp(ctx).getInt(KEY_SWIPE_SENS, DEF_SWIPE_SENS).coerceIn(SENS_MIN, SENS_MAX)
    fun swipeOpenDp(level: Int) = SWIPE_DP[level.coerceIn(SENS_MIN, SENS_MAX) - 1]

    fun handleSide(ctx: Context) = sp(ctx).getString(KEY_HANDLE_SIDE, DEF_HANDLE_SIDE) ?: DEF_HANDLE_SIDE
    fun handleInset(ctx: Context) = sp(ctx).getInt(KEY_HANDLE_INSET, DEF_HANDLE_INSET).coerceIn(0, INSET_MAX)
    fun handleWidth(ctx: Context) = sp(ctx).getInt(KEY_HANDLE_WIDTH, DEF_HANDLE_WIDTH).coerceIn(WIDTH_MIN, WIDTH_MAX)
    fun handleHeight(ctx: Context) = sp(ctx).getInt(KEY_HANDLE_HEIGHT, DEF_HANDLE_HEIGHT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)
    fun handleOpacity(ctx: Context) = sp(ctx).getInt(KEY_HANDLE_OPACITY, DEF_HANDLE_OPACITY).coerceIn(OPACITY_MIN, OPACITY_MAX)
    // ---- home-screen-only
    const val KEY_HOME_ONLY = "home_only"
    fun homeOnly(ctx: Context) = sp(ctx).getBoolean(KEY_HOME_ONLY, false)
    /** One-time: overwrite old handle_* + home_only with Samsung-like defaults. */
    const val KEY_SAMSUNG_HANDLE_DEFAULTS_V2 = "samsung_handle_defaults_v2"
    const val KEY_HANDLE_SIDE_RIGHT_V3 = "handle_side_right_v3"
    const val KEY_HANDLE_Y_SAMSUNG_V1 = "handle_y_samsung_v1"
    /** One-time: old default inset 2 → 8 (clear of Samsung back-gesture edge). */
    const val KEY_HANDLE_INSET_GESTURE_V1 = "handle_inset_gesture_v1"

    fun migrateSamsungHandleDefaultsV1(ctx: Context) {
        val p = sp(ctx)
        if (!p.getBoolean(KEY_SAMSUNG_HANDLE_DEFAULTS_V2, false)) {
            p.edit()
                .putString(KEY_HANDLE_SIDE, DEF_HANDLE_SIDE)
                .putInt(KEY_HANDLE_INSET, DEF_HANDLE_INSET)
                .putInt(KEY_HANDLE_WIDTH, DEF_HANDLE_WIDTH)
                .putInt(KEY_HANDLE_HEIGHT, DEF_HANDLE_HEIGHT)
                .putInt(KEY_HANDLE_OPACITY, DEF_HANDLE_OPACITY)
                .putBoolean(KEY_HOME_ONLY, false)
                .putBoolean(KEY_SAMSUNG_HANDLE_DEFAULTS_V2, true)
                .commit()
        }
        // v3: always force default side to RIGHT once (fixes installs that stayed on left).
        if (!p.getBoolean(KEY_HANDLE_SIDE_RIGHT_V3, false)) {
            p.edit()
                .putString(KEY_HANDLE_SIDE, SIDE_RIGHT)
                .putBoolean(KEY_HANDLE_SIDE_RIGHT_V3, true)
                .commit()
        }
        // v3.5.5: once forced KEY_HANDLE_Y=-1 so mid default applied (was ~48% Samsung mid).
        // Runtime default when handleY < 0 is now center-at-75% (one-handed thumb zone).
        // Do not force-reset again — saved KEY_HANDLE_Y >= 0 (user drag) must be kept.
        if (!p.getBoolean(KEY_HANDLE_Y_SAMSUNG_V1, false)) {
            p.edit()
                .putInt(KEY_HANDLE_Y, -1)
                .putBoolean(KEY_HANDLE_Y_SAMSUNG_V1, true)
                .commit()
        }
    }

    /** One-time: default inset 2 → 8 so the bar sits outside Samsung gesture edge more often.
     *  Only updates missing key or still-at-old-default (2); leave other user values alone. */
    fun migrateHandleInsetGestureV1(ctx: Context) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_HANDLE_INSET_GESTURE_V1, false)) return
        val ed = p.edit().putBoolean(KEY_HANDLE_INSET_GESTURE_V1, true)
        if (!p.contains(KEY_HANDLE_INSET) || p.getInt(KEY_HANDLE_INSET, DEF_HANDLE_INSET) == 2) {
            ed.putInt(KEY_HANDLE_INSET, DEF_HANDLE_INSET)
        }
        ed.apply()
    }

    fun writeHandleDefaults(ctx: Context) {
        sp(ctx).edit()
            .putString(KEY_HANDLE_SIDE, DEF_HANDLE_SIDE)
            .putInt(KEY_HANDLE_INSET, DEF_HANDLE_INSET)
            .putInt(KEY_HANDLE_WIDTH, DEF_HANDLE_WIDTH)
            .putInt(KEY_HANDLE_HEIGHT, DEF_HANDLE_HEIGHT)
            .putInt(KEY_HANDLE_OPACITY, DEF_HANDLE_OPACITY)
            .putInt(KEY_SWIPE_SENS, DEF_SWIPE_SENS)
            .putInt(KEY_HANDLE_Y, -1)
            .apply()
    }
    const val KEY_HIDE_ON_IME = "hide_on_ime"
    fun hideOnIme(ctx: Context) = sp(ctx).getBoolean(KEY_HIDE_ON_IME, true)
    /** Clear our tidied LINE notifications when a LINE screen comes to the front (needs the a11y service). */
    const val KEY_CLEAR_ON_LINE_OPEN = "clear_on_line_open"
    fun clearOnLineOpen(ctx: Context) = sp(ctx).getBoolean(KEY_CLEAR_ON_LINE_OPEN, true)
    /**
     * 進階清理 (experimental, off by default): instead of clearing every LINE chat, read the top bar
     * of the LINE screen and clear only the chat it names. Needs its own consent (SmartClearDisclosure).
     */
    const val KEY_SMART_CLEAR = "smart_clear_v1"
    const val KEY_SMART_CLEAR_CONSENT_V1 = "smart_clear_consent_v1"
    fun smartClear(ctx: Context) = sp(ctx).getBoolean(KEY_SMART_CLEAR, false) &&
        sp(ctx).getBoolean(KEY_SMART_CLEAR_CONSENT_V1, false)

    /**
     * Show the edge handle on the lock screen. The panel itself never opens while locked: swiping
     * the handle there asks to unlock first (OpenPanelActivity), so message contents are never
     * shown on the lock screen. Off by default.
     */
    const val KEY_SHOW_ON_LOCK = "show_on_lock"
    fun showOnLock(ctx: Context) = sp(ctx).getBoolean(KEY_SHOW_ON_LOCK, false)

    // ---- panel appearance
    const val KEY_PANEL_WIDTH = "panel_width_pct"
    const val KEY_PANEL_HEIGHT = "panel_height_pct"
    const val KEY_PANEL_POS = "panel_position"
    const val KEY_PANEL_RADIUS = "panel_radius_dp"
    const val KEY_PANEL_TEXT = "panel_text_size"
    const val KEY_PANEL_MAXLINES = "panel_max_lines"
    const val ML_MIN = 2; const val ML_MAX = 10
    fun panelMaxLines(ctx: Context) = sp(ctx).getInt(KEY_PANEL_MAXLINES, 6).coerceIn(ML_MIN, ML_MAX)

    // ---- LINE notification style
    const val KEY_NOTIF_STYLE = "notif_style"
    const val STYLE_BIGTEXT = "bigtext"; const val STYLE_INBOX = "inbox"; const val STYLE_MESSAGING = "messaging"
    /** Spacing between messages in the expanded notification / side panel: 0 無, 1 小 (default), 2 大. */
    const val KEY_MSG_GAP = "msg_gap"
    const val GAP_NONE = 0; const val GAP_SMALL = 1; const val GAP_LARGE = 2
    fun msgGap(ctx: Context) = sp(ctx).getInt(KEY_MSG_GAP, GAP_SMALL).coerceIn(GAP_NONE, GAP_LARGE)
    /** v2.8: default is the conversation (MessagingStyle) layout, which shows the latest 2 messages collapsed. */
    fun notifStyle(ctx: Context) = sp(ctx).getString(KEY_NOTIF_STYLE, STYLE_MESSAGING) ?: STYLE_MESSAGING

    /** Max messages per chat notification: 5 / 10 / 25 (default 10). */
    const val KEY_NOTIF_MAX = "notif_max"
    const val KEY_NOTIF_MAX_MIGRATE_V1 = "notif_max_migrate_v1"
    const val NOTIF_MAX_DEFAULT = 10
    /** Soft cap for non-Messaging styles; MessagingStyle is further capped at 25 in Notifier. */
    const val NOTIF_MAX_CAP = 25
    @Deprecated("use NOTIF_MAX_DEFAULT; kept so old call sites compile if any")
    const val NOTIF_MAX_ALL = 0
    fun notifMaxRaw(ctx: Context): Int {
        migrateNotifMaxV1(ctx)
        return sp(ctx).getInt(KEY_NOTIF_MAX, NOTIF_MAX_DEFAULT)
    }
    fun notifMax(ctx: Context): Int {
        val v = notifMaxRaw(ctx)
        return when {
            v <= 0 -> 25          // legacy 「全部」
            v in setOf(5, 10, 25) -> v
            v < 5 -> 5
            v < 25 -> 10
            else -> 25
        }
    }

    /** Drop 「全部」/2 options; clamp old values once. */
    fun migrateNotifMaxV1(ctx: Context) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_NOTIF_MAX_MIGRATE_V1, false)) return
        val ed = p.edit().putBoolean(KEY_NOTIF_MAX_MIGRATE_V1, true)
        if (p.contains(KEY_NOTIF_MAX)) {
            val raw = p.getInt(KEY_NOTIF_MAX, NOTIF_MAX_DEFAULT)
            val nv = when {
                raw <= 0 -> 25
                raw == 2 -> 5
                raw in setOf(5, 10, 25) -> raw
                raw > 25 -> 25
                else -> NOTIF_MAX_DEFAULT
            }
            ed.putInt(KEY_NOTIF_MAX, nv)
        }
        // missing key → new default 10 applies via getInt
        ed.apply()
    }

    /** Dim the screen behind the open side panel (tapping it closes the panel either way). */
    const val KEY_PANEL_SCRIM = "panel_scrim"
    fun panelScrim(ctx: Context) = sp(ctx).getBoolean(KEY_PANEL_SCRIM, true)
    /** One-time "how to close" hint at the bottom of the panel. */
    const val KEY_PANEL_HINT_SHOWN = "panel_hint_shown_v34"   // v3.4: no bottom close bar tip
    /** User confirmed they muted LINE's message channel / never-sleep whitelist (checklist). */
    const val KEY_LINE_MUTE_DONE = "line_mute_done"
    const val KEY_NEVER_SLEEP_DONE = "never_sleep_done"
    /** User opened App Info to allow Restricted Settings (after trying a11y once). */
    const val KEY_A11Y_APPINFO_OPENED_V1 = "a11y_appinfo_opened_v1"
    /** User opened Accessibility to try enabling the sidebar service (Samsung denial flow). */
    const val KEY_A11Y_TRY_OPENED_V1 = "a11y_try_opened_v1"
    /**
     * Wizard step 1 opened notification-access settings. Stock Android (ASUS, Pixel…) blocks
     * notification access for sideloaded apps too, not just accessibility like Samsung.
     */
    const val KEY_LISTENER_TRY_OPENED_V1 = "listener_try_opened_v1"
    fun listenerTryOpened(ctx: Context) = sp(ctx).getBoolean(KEY_LISTENER_TRY_OPENED_V1, false)
    /** User agreed to the accessibility prominent disclosure (A11yDisclosure). */
    const val KEY_A11Y_CONSENT_V1 = "a11y_consent_v1"
    fun a11yConsent(ctx: Context) = sp(ctx).getBoolean(KEY_A11Y_CONSENT_V1, false)
    /** First-run multi-step wizard finished (or skipped via complete). */
    const val KEY_WIZARD_DONE_V1 = "wizard_done_v1"
    fun lineMuteDone(ctx: Context) = sp(ctx).getBoolean(KEY_LINE_MUTE_DONE, false)
    fun neverSleepDone(ctx: Context) = sp(ctx).getBoolean(KEY_NEVER_SLEEP_DONE, false)
    fun a11yAppInfoOpened(ctx: Context) = sp(ctx).getBoolean(KEY_A11Y_APPINFO_OPENED_V1, false)
    fun a11yTryOpened(ctx: Context) = sp(ctx).getBoolean(KEY_A11Y_TRY_OPENED_V1, false)
    fun wizardDone(ctx: Context) = sp(ctx).getBoolean(KEY_WIZARD_DONE_V1, false)

    /** The user postponed the side-panel steps in the wizard; a to-do stays on the status card. */
    const val KEY_A11Y_LATER = "a11y_setup_later"
    fun a11yLater(ctx: Context) = sp(ctx).getBoolean(KEY_A11Y_LATER, false)

    /** LINE snoozes left by versions before 1.1.1 were released (done once). */
    const val KEY_SNOOZE_RELEASED = "snooze_released_v1"
    fun snoozeReleased(ctx: Context) = sp(ctx).getBoolean(KEY_SNOOZE_RELEASED, false)

    /** Last observed LINE notification channel (from RankingMap). -1 = not seen yet. */
    const val KEY_LINE_CH_ID = "line_ch_id"
    const val KEY_LINE_CH_IMP = "line_ch_imp"
    fun lineChannelId(ctx: Context) = sp(ctx).getString(KEY_LINE_CH_ID, null)
    fun lineChannelImportance(ctx: Context) = sp(ctx).getInt(KEY_LINE_CH_IMP, -1)
    fun lineChannelKnown(ctx: Context) = lineChannelImportance(ctx) >= 0
    /** True when LINE's channel can still heads-up (DEFAULT or higher). */
    fun lineChannelCanHeadsUp(ctx: Context) =
        lineChannelImportance(ctx) >= android.app.NotificationManager.IMPORTANCE_DEFAULT

    fun setLineChannel(ctx: Context, channelId: String?, importance: Int) {
        sp(ctx).edit()
            .putString(KEY_LINE_CH_ID, channelId)
            .putInt(KEY_LINE_CH_IMP, importance)
            .apply()
    }
    val PANEL_KEYS = setOf(KEY_PANEL_WIDTH, KEY_PANEL_HEIGHT, KEY_PANEL_POS, KEY_PANEL_RADIUS, KEY_PANEL_TEXT, KEY_PANEL_MAXLINES, KEY_PANEL_SCRIM)
    const val POS_TOP = "top"; const val POS_CENTER = "center"; const val POS_BOTTOM = "bottom"
    const val PW_MIN = 50; const val PW_MAX = 100
    const val PH_MIN = 40; const val PH_MAX = 100
    const val PR_MAX = 40
    const val DEF_PANEL_WIDTH = 50
    const val KEY_PANEL_WIDTH_NARROW_V1 = "panel_width_narrow_v1"
    const val KEY_PANEL_WIDTH_WIDEN_V2 = "panel_width_widen_v2"
    const val KEY_PANEL_WIDTH_HALF_V3 = "panel_width_half_v3"
    fun panelWidth(ctx: Context) = sp(ctx).getInt(KEY_PANEL_WIDTH, DEF_PANEL_WIDTH).coerceIn(PW_MIN, PW_MAX)

    /** One-time: old default 80 → 55; leave alone if user picked another width. */
    fun migratePanelWidthNarrowV1(ctx: Context) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_PANEL_WIDTH_NARROW_V1, false)) return
        val ed = p.edit().putBoolean(KEY_PANEL_WIDTH_NARROW_V1, true)
        // Hardcode 55 so widen-v2 can then lift survivors of the narrow default to 68.
        if (!p.contains(KEY_PANEL_WIDTH) || p.getInt(KEY_PANEL_WIDTH, 80) == 80) {
            ed.putInt(KEY_PANEL_WIDTH, 55)
        }
        ed.apply()
    }

    /** One-time: previous default 55 → 68; leave alone if user picked another width. */
    fun migratePanelWidthWidenV2(ctx: Context) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_PANEL_WIDTH_WIDEN_V2, false)) return
        val ed = p.edit().putBoolean(KEY_PANEL_WIDTH_WIDEN_V2, true)
        if (!p.contains(KEY_PANEL_WIDTH) || p.getInt(KEY_PANEL_WIDTH, DEF_PANEL_WIDTH) == 55) {
            ed.putInt(KEY_PANEL_WIDTH, DEF_PANEL_WIDTH)
        }
        ed.apply()
    }
    /** One-time: previous default 68 → 50 (half the screen); leave alone if user picked another width. */
    fun migratePanelWidthHalfV3(ctx: Context) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_PANEL_WIDTH_HALF_V3, false)) return
        val ed = p.edit().putBoolean(KEY_PANEL_WIDTH_HALF_V3, true)
        if (!p.contains(KEY_PANEL_WIDTH) || p.getInt(KEY_PANEL_WIDTH, DEF_PANEL_WIDTH) == 68) {
            ed.putInt(KEY_PANEL_WIDTH, DEF_PANEL_WIDTH)
        }
        ed.apply()
    }
    fun panelHeight(ctx: Context) = sp(ctx).getInt(KEY_PANEL_HEIGHT, 90).coerceIn(PH_MIN, PH_MAX)
    fun panelPosition(ctx: Context) = sp(ctx).getString(KEY_PANEL_POS, POS_CENTER) ?: POS_CENTER
    fun panelRadius(ctx: Context) = sp(ctx).getInt(KEY_PANEL_RADIUS, 26).coerceIn(0, PR_MAX)
    /** 0 = 小, 1 = 中, 2 = 大 */
    fun panelTextSize(ctx: Context) = sp(ctx).getInt(KEY_PANEL_TEXT, 1).coerceIn(0, 2)
    fun panelTextScale(ctx: Context) = when (panelTextSize(ctx)) { 0 -> 0.88f; 2 -> 1.18f; else -> 1f }

    fun setString(ctx: Context, key: String, value: String) = sp(ctx).edit().putString(key, value).apply()

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, true)
    /** Hiding LINE's own notifications is part of the master switch「用 Sidepeek 接管 LINE」, so it defaults
     *  to ON like the master switch. (Defaulting to false made fresh installs show both the
     *  Sidepeek notification and LINE's original one.) */
    fun hideOriginal(ctx: Context) = sp(ctx).getBoolean(KEY_HIDE_ORIGINAL, true)
    fun edgePanel(ctx: Context) = sp(ctx).getBoolean(KEY_EDGE_PANEL, false)
    fun handleY(ctx: Context) = sp(ctx).getInt(KEY_HANDLE_Y, -1)

    fun set(ctx: Context, key: String, value: Boolean) = sp(ctx).edit().putBoolean(key, value).apply()
    fun setInt(ctx: Context, key: String, value: Int) = sp(ctx).edit().putInt(key, value).apply()
}

/** True when "使用情況存取權" (PACKAGE_USAGE_STATS app-op) is granted. */
fun hasUsageAccess(ctx: Context): Boolean = try {
    val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
    val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
        ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
    else @Suppress("DEPRECATION")
        ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
    mode == android.app.AppOpsManager.MODE_ALLOWED
} catch (_: Exception) { false }

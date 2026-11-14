package app.sidepeek

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    private val requestPost = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    /** 「通知鈴聲」: the system ringtone picker; its pick moves 「LINE 訊息預覽」 to that sound. */
    private val pickSound = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK || r.data == null) return@registerForActivityResult
        @Suppress("DEPRECATION")
        val uri = r.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        Notifier.setMessageSound(this, uri)
        refresh()
    }
    /** 「從我的檔案選音檔」: copy the file where the system can play it, then use it. */
    /** 通知外觀 → 狀態列小圖示 →「自訂」：選一張圖，轉成單色剪影後套用。 */
    private val pickNotifIcon = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (NotifIcons.saveCustom(this, uri)) {
            Prefs.setString(this, Prefs.KEY_NOTIF_ICON, NotifIcons.CUSTOM)
            Notifier.refreshAllSilently(this)
            Toast.makeText(this, getString(R.string.toast_icon_custom), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_icon_bad), Toast.LENGTH_LONG).show()
        }
        syncNotifIconPicker()
    }

    private val pickAudioFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { src ->
        if (src == null) return@registerForActivityResult
        Thread {
            val sound = SoundFiles.importNotificationSound(this, src)
            runOnUiThread {
                if (sound == null) {
                    Toast.makeText(this, getString(R.string.toast_sound_bad), Toast.LENGTH_LONG).show()
                } else {
                    Notifier.setMessageSound(this, sound)
                    Toast.makeText(this, getString(R.string.toast_sound_set), Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }.start()
    }

    private lateinit var statusSummary: TextView
    private lateinit var statusAccess: TextView
    private lateinit var statusListener: TextView
    private lateinit var statusPost: TextView
    private lateinit var statusBattery: TextView
    private lateinit var statusLineMute: TextView
    private lateinit var statusNeverSleep: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var statusUsage: TextView
    private lateinit var statusA11y: TextView
    private lateinit var swEdge: SwitchCompat

    private lateinit var howtoCard: View
    private lateinit var wizardCard: View
    private lateinit var dashGroup: View
    private lateinit var cardSuggest: View
    private lateinit var a11yBox: View
    private lateinit var a11yGuideBox: View
    private lateinit var backupBox: View
    private lateinit var hdrFaq: View
    private lateinit var hdrHelp: View

    private lateinit var wizardProgress: TextView
    private lateinit var wizardTitle: TextView
    private lateinit var wizardHint: TextView
    private lateinit var wizardImage: ImageView
    private lateinit var wizardVideo: GuideVideoView
    private lateinit var wizardPrimary: Button
    private lateinit var wizardSecondary: Button
    private lateinit var wizardSkip: Button

    /** True while the wizard flips the side-panel switch itself (don't run the switch's own action). */
    private var suppressEdgeSwitch = false

    /** Current wizard step index 0..STEP_DONE (only meaningful while wizard visible). */
    private var wizardStep = 0

    /** 還沒設定好時，使用者按了「先看看其他設定」就把控制台也顯示出來。 */
    private var showAll = false

    /** 底部分頁：目前在哪一頁（TAB_HOME／TAB_SETTINGS／TAB_HELP）。 */
    private var tab = TAB_HOME
    private lateinit var bottomNav: GlassTabBar
    private val pageIds = intArrayOf(R.id.pageHome, R.id.pageSettings, R.id.pageHelp)

    /** 進階設定的收合區塊：標題 id、內容 id、標題文字。 */
    private val sections by lazy { listOf(
        Triple(R.id.hdrLook, R.id.grpLook, getString(R.string.sec_look)),
        Triple(R.id.hdrLineClear, R.id.grpLineClear, getString(R.string.sec_line_clear)),
        Triple(R.id.hdrSources, R.id.grpSources, getString(R.string.sec_sources)),
        Triple(R.id.hdrPanelLook, R.id.grpPanelLook, getString(R.string.sec_panel_look)),
        Triple(R.id.hdrHandle, R.id.grpHandle, getString(R.string.sec_handle)),
        Triple(R.id.hdrHandleWhen, R.id.grpHandleWhen, getString(R.string.sec_handle_when)),
        Triple(R.id.hdrOpen, R.id.grpOpen, getString(R.string.sec_open)),
        Triple(R.id.hdrOther, R.id.grpOther, getString(R.string.sec_other)),
        Triple(R.id.hdrFaq, R.id.grpFaq, getString(R.string.sec_faq)),
        Triple(R.id.hdrAbout, R.id.grpAbout, getString(R.string.sec_about)),
        Triple(R.id.hdrHelp, R.id.grpHelp, getString(R.string.sec_help)),
    ) }
    /** 目前展開的區塊（存 sections 的索引，旋轉螢幕或 recreate() 後會還原）。 */
    private val expanded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Title bar: the Sidepeek wordmark with its light sweep instead of plain text.
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayShowCustomEnabled(true)
            customView = BrandTitleView(themedContext)
        }
        findViewById<ImageView>(R.id.imgEdgeHandles).setImageDrawable(EdgeHandlesDrawable(this))
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0).padForSystemBars()
        addStatusBarScrim()
        Prefs.migrateSamsungHandleDefaultsV1(this)
        Prefs.migrateHandleInsetGestureV1(this)
        Prefs.migrateNotifMaxV1(this)
        Prefs.migratePanelWidthNarrowV1(this)
        Prefs.migratePanelWidthWidenV2(this)
        Prefs.migratePanelWidthHalfV3(this)
        Notifier.ensureChannels(this)

        howtoCard = findViewById(R.id.howtoCard)
        wizardCard = findViewById(R.id.wizardCard)
        dashGroup = findViewById(R.id.dashGroup)
        cardSuggest = findViewById(R.id.cardSuggest)
        a11yBox = findViewById(R.id.a11yBox)
        a11yGuideBox = findViewById(R.id.a11yGuideBox)
        backupBox = findViewById(R.id.backupBox)
        hdrFaq = findViewById(R.id.hdrFaq)
        hdrHelp = findViewById(R.id.hdrHelp)

        wizardProgress = findViewById(R.id.wizardProgress)
        wizardTitle = findViewById(R.id.wizardTitle)
        wizardHint = findViewById(R.id.wizardHint)
        wizardImage = findViewById(R.id.wizardImage)
        wizardVideo = findViewById(R.id.wizardVideo)
        wizardPrimary = findViewById(R.id.wizardPrimary)
        wizardSecondary = findViewById(R.id.wizardSecondary)
        wizardSkip = findViewById(R.id.wizardSkip)

        // Legacy hidden buttons (ids kept for safety); wire same actions.
        findViewById<Button>(R.id.btnSkipSetup).setOnClickListener { showAll = true; refresh() }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnRebind).setOnClickListener { rebindListener() }
        findViewById<Button>(R.id.btnPost).setOnClickListener { requestPostNotifications() }
        findViewById<Button>(R.id.btnRepostFix).setOnClickListener { openRepostSettings() }
        findViewById<Button>(R.id.btnResumeA11y).setOnClickListener { resumeA11ySetup() }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            UpdateChecker.available(this)?.let { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
        }

        wizardStep = savedInstanceState?.getInt(STATE_WIZARD_STEP, 0) ?: 0
        setupCollapsibles(savedInstanceState)
        setupTabs(savedInstanceState)
        setupSettingsPages(savedInstanceState)

        statusSummary = findViewById(R.id.statusSummary)
        statusAccess = findViewById(R.id.statusAccess)
        statusListener = findViewById(R.id.statusListener)
        statusPost = findViewById(R.id.statusPost)
        statusBattery = findViewById(R.id.statusBattery)
        statusLineMute = findViewById(R.id.statusLineMute)
        statusNeverSleep = findViewById(R.id.statusNeverSleep)
        statusOverlay = findViewById(R.id.statusOverlay)
        statusUsage = findViewById(R.id.statusUsage)
        statusA11y = findViewById(R.id.statusA11y)
        findViewById<Button>(R.id.btnA11yAppInfo).setOnClickListener { openA11yAppInfo() }
        findViewById<Button>(R.id.btnA11y).setOnClickListener { openA11ySettings() }
        findViewById<Button>(R.id.btnFgsChannel).setOnClickListener {
            Notifier.ensureChannels(this)
            val ok = safeStart(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_PANEL))
            if (!ok) safeStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        findViewById<Button>(R.id.btnLineMute).setOnClickListener { openLineNotificationSettings() }
        findViewById<Button>(R.id.btnLineMuteDone).setOnClickListener {
            Prefs.set(this, Prefs.KEY_LINE_MUTE_DONE, true); refresh()
            Toast.makeText(this, getString(R.string.toast_line_mute_done), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnNeverSleep).setOnClickListener { openBackgroundSettings() }
        findViewById<Button>(R.id.btnNeverSleepDone).setOnClickListener {
            Prefs.set(this, Prefs.KEY_NEVER_SLEEP_DONE, true); refresh()
            Toast.makeText(this, getString(R.string.toast_never_sleep_done), Toast.LENGTH_SHORT).show()
        }

        val swEnabled = findViewById<SwitchCompat>(R.id.swEnabled)
        swEnabled.isChecked = Prefs.enabled(this)
        swEnabled.setOnCheckedChangeListener { _, on ->
            Prefs.set(this, Prefs.KEY_ENABLED, on)
            Prefs.set(this, Prefs.KEY_HIDE_ORIGINAL, on)
            findViewById<SwitchCompat>(R.id.swHide).isChecked = on
            refresh()
        }

        val swHide = findViewById<SwitchCompat>(R.id.swHide)
        swHide.isChecked = Prefs.enabled(this) && Prefs.hideOriginal(this)

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, getString(R.string.toast_test_need_post), Toast.LENGTH_LONG).show()
            }
            Notifier.postTest(this)
        }

        swEdge = findViewById(R.id.swEdge)
        swEdge.isChecked = Prefs.edgePanel(this)
        swEdge.setOnCheckedChangeListener { _, on ->
            if (suppressEdgeSwitch) return@setOnCheckedChangeListener
            Prefs.set(this, Prefs.KEY_EDGE_PANEL, on)
            if (on) {
                if (!PanelHost.a11yActive && !PanelHost.a11yEnabledInSettings(this) && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, getString(R.string.toast_panel_need_a11y), Toast.LENGTH_LONG).show()
                    openA11yEnablePath()
                } else PanelHost.start(this)
            } else PanelHost.stop(this)
            refresh()
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btnOpenPanel).setOnClickListener { openPanelNow() }

        val swHomeOnly = findViewById<SwitchCompat>(R.id.swHomeOnly)
        swHomeOnly.isChecked = Prefs.homeOnly(this)
        swHomeOnly.setOnCheckedChangeListener { _, on ->
            Prefs.set(this, Prefs.KEY_HOME_ONLY, on)
            if (on && !hasUsageAccess(this) && !PanelHost.a11yActive) {
                Toast.makeText(this, getString(R.string.toast_need_usage), Toast.LENGTH_LONG).show()
                openUsageSettings()
            }
            refresh()
        }
        findViewById<Button>(R.id.btnUsage).setOnClickListener { openUsageSettings() }
        findViewById<Button>(R.id.btnSound).setOnClickListener {
            // Android 9 and older cannot copy a file in without a storage permission: built-in sounds only.
            if (Build.VERSION.SDK_INT < 29) { pickBuiltInSound(); return@setOnClickListener }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.sound_title))
                .setItems(arrayOf(getString(R.string.sound_from_files), getString(R.string.sound_from_system))) { _, which ->
                    if (which == 0) {
                        try { pickAudioFile.launch(arrayOf("audio/*")) } catch (_: android.content.ActivityNotFoundException) { pickBuiltInSound() }
                    } else pickBuiltInSound()
                }
                .show()
        }
        findViewById<SwitchCompat>(R.id.swHideIme).apply {
            isChecked = Prefs.hideOnIme(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_HIDE_ON_IME, on) }
        }
        findViewById<SwitchCompat>(R.id.swClearOnLine).apply {
            isChecked = Prefs.clearOnLineOpen(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_CLEAR_ON_LINE_OPEN, on); refresh() }
        }
        findViewById<SwitchCompat>(R.id.swSmartClear).apply {
            isChecked = Prefs.smartClear(this@MainActivity)
            setOnCheckedChangeListener { sw, on ->
                if (!on) { Prefs.set(this@MainActivity, Prefs.KEY_SMART_CLEAR, false); refresh(); return@setOnCheckedChangeListener }
                // Reading LINE's top bar needs its own consent, asked every time it is switched on.
                A11yDisclosure.smartClear(this@MainActivity) { ok ->
                    Prefs.set(this@MainActivity, Prefs.KEY_SMART_CLEAR, ok)
                    if (!ok) sw.isChecked = false
                    refresh()
                }
            }
        }
        findViewById<SwitchCompat>(R.id.swShowOnLock).apply {
            isChecked = Prefs.showOnLock(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_SHOW_ON_LOCK, on) }
        }
        setupPanelAppearance()

        val rgStyle = findViewById<RadioGroup>(R.id.rgNotifStyle)
        rgStyle.check(when (Prefs.notifStyle(this)) {
            Prefs.STYLE_INBOX -> R.id.rbStyleInbox; Prefs.STYLE_BIGTEXT -> R.id.rbStyleBig; else -> R.id.rbStyleMsg
        })
        fun syncGapVisibility() {
            val big = Prefs.notifStyle(this) == Prefs.STYLE_BIGTEXT
            val vis = if (big) View.VISIBLE else View.GONE
            findViewById<View>(R.id.lblMsgGap).visibility = vis
            findViewById<View>(R.id.rgMsgGap).visibility = vis
            findViewById<View>(R.id.hintMsgGap).visibility = vis
        }
        rgStyle.setOnCheckedChangeListener { _, id ->
            Prefs.setString(this, Prefs.KEY_NOTIF_STYLE, when (id) {
                R.id.rbStyleInbox -> Prefs.STYLE_INBOX; R.id.rbStyleBig -> Prefs.STYLE_BIGTEXT; else -> Prefs.STYLE_MESSAGING
            })
            syncGapVisibility()
            Notifier.refreshAllSilently(this)
        }
        syncGapVisibility()
        val rgMax = findViewById<RadioGroup>(R.id.rgNotifMax)
        rgMax.check(when (Prefs.notifMax(this)) { 5 -> R.id.rbMax5; 25 -> R.id.rbMax25; else -> R.id.rbMax10 })
        rgMax.setOnCheckedChangeListener { _, id ->
            Prefs.setInt(this, Prefs.KEY_NOTIF_MAX, when (id) {
                R.id.rbMax5 -> 5; R.id.rbMax25 -> 25; else -> 10
            })
            Notifier.refreshAllSilently(this)
        }
        val rgGap = findViewById<RadioGroup>(R.id.rgMsgGap)
        rgGap.check(when (Prefs.msgGap(this)) { Prefs.GAP_NONE -> R.id.rbGapNone; Prefs.GAP_LARGE -> R.id.rbGapLarge; else -> R.id.rbGapSmall })
        rgGap.setOnCheckedChangeListener { _, id ->
            Prefs.setInt(this, Prefs.KEY_MSG_GAP, when (id) { R.id.rbGapNone -> Prefs.GAP_NONE; R.id.rbGapLarge -> Prefs.GAP_LARGE; else -> Prefs.GAP_SMALL })
            Notifier.refreshAllSilently(this)
        }

        setupHandleSettings()
        setupSourceSwitches()
        setupNotifIconPicker()

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportDiagnostics() }
        findViewById<Button>(R.id.btnClearDiag).setOnClickListener {
            Diagnostics.clear(this)
            Toast.makeText(this, getString(R.string.toast_diag_cleared), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnDiagFull).setOnClickListener {
            if (Diagnostics.full(this)) {
                Diagnostics.stopFull(this)
                Toast.makeText(this, getString(R.string.toast_diag_full_stopped), Toast.LENGTH_SHORT).show()
            } else {
                Diagnostics.startFull(this)
                Toast.makeText(this, getString(R.string.toast_diag_full_started, Diagnostics.FULL_HOURS), Toast.LENGTH_LONG).show()
            }
            refresh()
        }
        findViewById<SwitchCompat>(R.id.swUpdateCheck).apply {
            isChecked = UpdateChecker.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, UpdateChecker.KEY_ENABLED, on); refresh() }
        }

        wizardPrimary.setOnClickListener { onWizardPrimary() }
        wizardSecondary.setOnClickListener { onWizardSecondary() }
        wizardSkip.setOnClickListener { onWizardSkip() }

        // Already fully set up from an older install → mark wizard done once.
        if (!Prefs.wizardDone(this) && isCoreReady()) {
            Prefs.set(this, Prefs.KEY_WIZARD_DONE_V1, true)
        }
    }

    override fun onPause() {
        super.onPause()
        wizardVideo.pause()
    }

    override fun onResume() {
        super.onResume()
        wizardVideo.resume()
        if (Prefs.edgePanel(this) && !PanelHost.running()) PanelHost.start(this)
        refresh()
        if (openSettingsCard == null) updateSettingsSummaries()
        window.decorView.postDelayed({ if (!isFinishing) refresh() }, 1200)
        UpdateChecker.checkIfDue(this) { if (!isFinishing) refresh() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_EXPANDED, expanded.toIntArray())
        outState.putBoolean(STATE_SHOW_ALL, showAll)
        outState.putInt(STATE_WIZARD_STEP, wizardStep)
        outState.putInt(STATE_TAB, tab)
        outState.putInt(STATE_SETTINGS_PAGE, openSettingsCard ?: 0)
    }

    // ---- 設定分頁：清單＋子頁面 ----------------------------------------------------------------

    private class SettingsItem(
        val cardId: Int, val hdrId: Int, val grpId: Int,
        val title: String, val icon: Int, val group: String,
        val summary: () -> String,
    )

    /** The card shown as a sub-page, or null while the list is shown. */
    private var openSettingsCard: Int? = null
    private val settingsSummaryViews = HashMap<Int, TextView>()
    private val settingsBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showSettingsList()
    }

    private fun checkedText(groupId: Int): String? = findViewById<RadioGroup>(groupId).let { g ->
        g.findViewById<android.widget.RadioButton>(g.checkedRadioButtonId)?.text?.toString()
    }
    private fun isOn(switchId: Int) = findViewById<SwitchCompat>(switchId).isChecked
    /** The value cell of a 「名稱｜滑桿｜數值」 row, by its name label. */
    private fun rowValue(labelId: Int): String = (findViewById<View>(labelId).parent as? ViewGroup)?.let { g ->
        (g.childCount - 1 downTo 0).map { g.getChildAt(it) }
            .firstOrNull { it is TextView && it.id != labelId }?.let { (it as TextView).text.toString() }
    } ?: ""

    private val settingsItems: List<SettingsItem> by lazy { listOf(
        SettingsItem(R.id.cardLook, R.id.hdrLook, R.id.grpLook, getString(R.string.set_look), R.drawable.ic_set_look, getString(R.string.grp_notif)) {
            listOfNotNull(
                checkedText(R.id.rgNotifStyle)?.substringBefore('（')?.substringBefore(" ("),
                checkedText(R.id.rgNotifMax)?.takeWhile { it.isDigit() }?.let { getString(R.string.sum_notif_max, it) },
                getString(R.string.sum_sound, soundName(Notifier.messageSound(this))),
            ).joinToString("・")
        },
        SettingsItem(R.id.cardLineClear, R.id.hdrLineClear, R.id.grpLineClear, getString(R.string.set_line_clear), R.drawable.ic_set_clean, getString(R.string.grp_notif)) {
            when {
                !isOn(R.id.swClearOnLine) -> getString(R.string.sum_clear_tap_only)
                isOn(R.id.swSmartClear) -> getString(R.string.sum_clear_smart)
                else -> getString(R.string.sum_clear_all)
            }
        },
        SettingsItem(R.id.cardSources, R.id.hdrSources, R.id.grpSources, getString(R.string.set_sources), R.drawable.ic_set_apps, getString(R.string.grp_panel)) {
            val names = ArrayList<String>()
            fun walk(v: View) {
                if (v is SwitchCompat) { if (v.isChecked) names += v.text.toString().lineSequence().first().trim() }
                else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(findViewById(R.id.appSwitches))
            when {
                names.isEmpty() -> getString(R.string.sum_apps_none)
                names.size <= 3 -> names.joinToString(getString(R.string.list_sep))
                else -> getString(R.string.sum_apps_more, names.take(3).joinToString(getString(R.string.list_sep)), names.size)
            }
        },
        SettingsItem(R.id.cardPanelLook, R.id.hdrPanelLook, R.id.grpPanelLook, getString(R.string.set_panel), R.drawable.ic_set_panel, getString(R.string.grp_panel)) {
            listOf(getString(R.string.sum_w, rowValue(R.id.lblPanelW)), getString(R.string.sum_h, rowValue(R.id.lblPanelH)),
                checkedText(R.id.rgPanelPos) ?: "", getString(R.string.sum_text, checkedText(R.id.rgPanelText) ?: ""))
                .filter { it.isNotBlank() }.joinToString("・")
        },
        SettingsItem(R.id.cardHandle, R.id.hdrHandle, R.id.grpHandle, getString(R.string.set_handle), R.drawable.ic_set_peek, getString(R.string.grp_panel)) {
            listOf(checkedText(R.id.rgSide) ?: "", getString(R.string.sum_w, rowValue(R.id.lblWidth)), getString(R.string.sum_sens, rowValue(R.id.lblSwipeSens)))
                .filter { it.isNotBlank() }.joinToString("・")
        },
        SettingsItem(R.id.cardHandleWhen, R.id.hdrHandleWhen, R.id.grpHandleWhen, getString(R.string.set_when), R.drawable.ic_set_when, getString(R.string.grp_panel)) {
            listOfNotNull(
                if (isOn(R.id.swHomeOnly)) getString(R.string.sum_home_only) else getString(R.string.sum_all_apps),
                if (isOn(R.id.swHideIme)) getString(R.string.sum_hide_ime) else null,
                if (isOn(R.id.swShowOnLock)) getString(R.string.sum_on_lock) else null,
            ).joinToString("・")
        },
        SettingsItem(R.id.cardOpen, R.id.hdrOpen, R.id.grpOpen, getString(R.string.set_open), R.drawable.ic_set_open, getString(R.string.grp_panel)) {
            getString(R.string.sum_open)
        },
        SettingsItem(R.id.cardOther, R.id.hdrOther, R.id.grpOther, getString(R.string.set_update), R.drawable.ic_set_update, getString(R.string.grp_other)) {
            if (isOn(R.id.swUpdateCheck)) getString(R.string.sum_update_on) else getString(R.string.sum_update_off)
        },
        SettingsItem(R.id.cardLanguage, R.id.hdrLanguage, R.id.grpLanguage, getString(R.string.set_language), R.drawable.ic_set_language, getString(R.string.grp_other)) {
            checkedText(R.id.rgLanguage) ?: ""
        },
    ).filter { BuildConfig.SELF_UPDATE_CHECK || it.cardId != R.id.cardOther } }  // Play 版沒有「檢查更新」

    /**
     * 語言：跟著系統，或指定 App 自己的語言。AppCompat 會記住選擇並重建畫面（Android 13 以上
     * 跟系統「應用程式語言」設定是同一個值；12 以下由 AppLocalesMetadataHolderService 記住）。
     */
    private fun setupLanguage() {
        val tags = mapOf(R.id.rbLangSystem to "", R.id.rbLangZh to "zh-TW", R.id.rbLangEn to "en",
            R.id.rbLangJa to "ja", R.id.rbLangKo to "ko")
        val rg = findViewById<RadioGroup>(R.id.rgLanguage)
        fun current() = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val lang = current().substringBefore(',').substringBefore('-')
        rg.setOnCheckedChangeListener(null)
        rg.check(tags.entries.firstOrNull { it.value.isNotEmpty() && it.value.substringBefore('-') == lang }?.key ?: R.id.rbLangSystem)
        rg.setOnCheckedChangeListener { _, id ->
            val tag = tags[id] ?: return@setOnCheckedChangeListener
            if (tag == current()) return@setOnCheckedChangeListener
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        }
    }

    private fun setupSettingsPages(saved: Bundle?) {
        // Play 版不在 settingsItems 裡，清單和子頁面都不會碰它，所以這裡直接藏起來。
        if (!BuildConfig.SELF_UPDATE_CHECK) findViewById<View>(R.id.cardOther).visibility = View.GONE
        onBackPressedDispatcher.addCallback(this, settingsBack)
        // Keep the open page's preview sized to the screen (insets, tab bar, text changes).
        findViewById<ScrollView>(R.id.scroll).viewTreeObserver.addOnGlobalLayoutListener { fitSettingsPreview() }
        findViewById<View>(R.id.settingsBack).setOnClickListener { showSettingsList() }
        val ink = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary)).run {
            val c = getColor(0, android.graphics.Color.GRAY); recycle(); c
        }
        val green = ContextCompat.getColor(this, R.color.section_text)
        findViewById<ImageView>(R.id.settingsBack).imageTintList = android.content.res.ColorStateList.valueOf(ink)
        val list = findViewById<LinearLayout>(R.id.settingsList)
        var group: String? = null
        var box: LinearLayout? = null
        for (item in settingsItems) {
            if (item.group != group) {
                group = item.group
                list.addView(layoutInflater.inflate(R.layout.settings_group_label, list, false).also {
                    (it as TextView).text = item.group
                })
                box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
                    clipToOutline = true
                }
                list.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dpx(4f) })
            } else {
                box!!.addView(View(this).apply { setBackgroundColor(0x1F888888) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dpx(60f) })
            }
            val row = layoutInflater.inflate(R.layout.settings_row, box, false)
            row.findViewById<ImageView>(R.id.rowIcon).apply {
                setImageResource(item.icon)
                imageTintList = android.content.res.ColorStateList.valueOf(green)
            }
            row.findViewById<TextView>(R.id.rowTitle).text = item.title
            settingsSummaryViews[item.cardId] = row.findViewById(R.id.rowSummary)
            row.findViewById<ImageView>(R.id.rowChevron).imageTintList =
                android.content.res.ColorStateList.valueOf((ink and 0x00FFFFFF) or 0x66000000)
            row.setOnClickListener { openSettingsPage(item.cardId) }
            box!!.addView(row)
        }
        val reopen = saved?.getInt(STATE_SETTINGS_PAGE, 0) ?: 0
        if (reopen != 0 && settingsItems.any { it.cardId == reopen }) openSettingsPage(reopen, scroll = false)
        else showSettingsList(scroll = false)
        // Back returns to the list only while the 設定 tab is showing a sub-page.
        val select = bottomNav.onTabSelected
        bottomNav.onTabSelected = { t ->
            select?.invoke(t)
            settingsBack.isEnabled = t == TAB_SETTINGS && openSettingsCard != null
        }
        // Tapping 設定 again while a sub-page is open goes back to the list.
        val reselect = bottomNav.onTabReselected
        bottomNav.onTabReselected = { t ->
            if (t == TAB_SETTINGS && openSettingsCard != null) showSettingsList() else reselect?.invoke(t)
        }
    }

    /** Settings list: title, hint and the rows; every card hidden. */
    private fun showSettingsList(scroll: Boolean = true) {
        openSettingsCard = null
        settingsBack.isEnabled = false
        setSettingsChrome(list = true)
        for (item in settingsItems) findViewById<View>(item.cardId).visibility = View.GONE
        updateSettingsSummaries()
        if (scroll) findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
    }

    /** One card as a full page: back + title on top, the card's header hidden, its content open. */
    private fun openSettingsPage(cardId: Int, scroll: Boolean = true) {
        val item = settingsItems.firstOrNull { it.cardId == cardId } ?: return
        openSettingsCard = cardId
        settingsBack.isEnabled = tab == TAB_SETTINGS
        setSettingsChrome(list = false)
        findViewById<TextView>(R.id.settingsSubTitle).text = item.title
        for (other in settingsItems) findViewById<View>(other.cardId).visibility = vis(other.cardId == cardId)
        findViewById<View>(item.hdrId).visibility = View.GONE
        findViewById<View>(item.grpId).visibility = View.VISIBLE
        // Without its header the card needs its own top padding.
        findViewById<View>(cardId).apply { setPaddingRelative(paddingStart, dpx(12f), paddingEnd, dpx(12f)) }
        if (scroll) findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
        findViewById<ScrollView>(R.id.scroll).post { fitSettingsPreview() }
    }

    /** The live preview at the top of a settings page that has one (側邊欄外觀, Peek 條樣式). */
    private fun settingsPreview(cardId: Int): View? {
        val grp = settingsItems.firstOrNull { it.cardId == cardId }?.let { findViewById<ViewGroup>(it.grpId) } ?: return null
        // Directly in the page, or in the page's top row (側邊欄外觀: preview left, controls right).
        val kids = (0 until grp.childCount).map { grp.getChildAt(it) }
        val all = kids + kids.filterIsInstance<ViewGroup>().flatMap { g -> (0 until g.childCount).map { g.getChildAt(it) } }
        return all.firstOrNull { it is PanelMiniature || it is SettingPreview }
    }

    /**
     * Gives the open page's preview whatever height is left, so the whole page fits on the screen
     * without scrolling (within a min / max; on a very short screen the page simply scrolls).
     * Runs after every layout: once the height is right the difference is 0 and nothing changes.
     */
    private fun fitSettingsPreview() {
        val card = openSettingsCard ?: return
        if (tab != TAB_SETTINGS) return
        val preview = settingsPreview(card) ?: return
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val content = scroll.getChildAt(0) ?: return
        val page = findViewById<View>(R.id.pageSettings)
        if (scroll.height == 0 || preview.height == 0 || !preview.isShown) return
        val room = scroll.height - scroll.paddingTop - scroll.paddingBottom
        // The ScrollView fills its viewport, so measure the page's own bottom, not the content's height.
        val used = page.bottom + content.paddingBottom
        val (minDp, maxDp) = if (preview is PanelMiniature) 180f to 460f else 120f to 300f
        // In a side-by-side row the row is as tall as its taller side, so grow from the row's height
        // and never make the preview shorter than the controls beside it.
        val row = preview.parent as? ViewGroup
        val inRow = row != null && row.id == R.id.panelTopRow
        val current = if (inRow) row!!.height else preview.height
        var want = (current + room - used).coerceIn(dpx(minDp), dpx(maxDp))
        if (inRow) want = maxOf(want, (0 until row!!.childCount).map { row.getChildAt(it) }
            .filter { it !== preview }.maxOfOrNull { it.height } ?: 0)
        if (Math.abs(want - preview.height) < dpx(2f)) return
        when (preview) {
            is PanelMiniature -> preview.previewHeightPx = want
            is SettingPreview -> preview.previewHeightPx = want
        }
    }

    /**
     * The tab bar shows once the wizard is done, except on a settings sub-page: there it would
     * cover the page's last row, and the page has its own back arrow.
     */
    private fun syncTabBar() {
        val dashOn = Prefs.wizardDone(this) || showAll
        val subPage = tab == TAB_SETTINGS && openSettingsCard != null
        bottomNav.visibility = vis(dashOn && !subPage)
    }

    private fun setSettingsChrome(list: Boolean) {
        syncTabBar()
        findViewById<View>(R.id.settingsTitle).visibility = vis(list)
        findViewById<View>(R.id.settingsHint).visibility = vis(list)
        findViewById<View>(R.id.settingsList).visibility = vis(list)
        findViewById<View>(R.id.settingsSubHeader).visibility = vis(!list)
        // The old group labels (通知／側邊欄／其他) between the cards are replaced by the list's own.
        val page = findViewById<ViewGroup>(R.id.pageSettings)
        for (i in 0 until page.childCount) page.getChildAt(i).let {
            if (it is TextView && it.id == View.NO_ID) it.visibility = View.GONE
        }
    }

    private fun updateSettingsSummaries() {
        for (item in settingsItems) settingsSummaryViews[item.cardId]?.text =
            try { item.summary() } catch (e: Exception) { "" }
    }

    private fun setupCollapsibles(saved: Bundle?) {
        saved?.getIntArray(STATE_EXPANDED)?.let { expanded.addAll(it.toList()) }
        showAll = saved?.getBoolean(STATE_SHOW_ALL, false) ?: false
        // 說明分頁一打開就看到常見問題的題目清單。
        if (saved == null) expanded.add(sections.indexOfFirst { it.first == R.id.hdrFaq })
        sections.forEachIndexed { index, (hdrId, grpId, title) ->
            val hdr = findViewById<TextView>(hdrId)
            val grp = findViewById<View>(grpId)
            // The header's press highlight spans the card and takes its rounded corners.
            (hdr.parent as? View)?.clipToOutline = true
            val sync = {
                val open = index in expanded
                grp.visibility = if (open) View.VISIBLE else View.GONE
                hdr.text = title
                hdr.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0)
                androidx.core.view.ViewCompat.setStateDescription(hdr, if (open) getString(R.string.state_expanded) else getString(R.string.state_collapsed))
            }
            sync()
            hdr.setOnClickListener {
                if (!expanded.remove(index)) expanded.add(index)
                sync()
            }
        }
        setupFaqAccordion()
        setupWhyLinks()
        setupLanguage()
        setupDetailsToggles(findViewById(android.R.id.content))
    }

    /** 狀態旁的「為什麼？」→ 說明頁裡對應的那一題。 */
    /**
     * Every「詳細說明 ▾」(style DetailsToggle, tag "details") opens and closes the view right after
     * it, so a page shows one short line and keeps the longer explanation one tap away.
     */
    private fun setupDetailsToggles(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v is TextView && v.tag == "details" && i + 1 < root.childCount) {
                val body = root.getChildAt(i + 1)
                v.setOnClickListener {
                    val open = body.visibility != View.VISIBLE
                    body.visibility = vis(open)
                    v.text = getString(if (open) R.string.details_less else R.string.details_more)
                }
            } else if (v is ViewGroup) setupDetailsToggles(v)
        }
    }

    private fun setupWhyLinks() {
        val links = mapOf(
            R.id.whyA11y to getString(R.string.ui_about_2),
            R.id.whyLineClear to getString(R.string.ui_about_2),
            R.id.whySuggest to getString(R.string.ui_faq_1),
            R.id.whyUsage to getString(R.string.ui_about_4),
        )
        val size = dpx(18f)
        for ((id, question) in links) findViewById<TextView>(id).apply {
            compoundDrawablesRelative[0]?.let { it.setBounds(0, 0, size, size); setCompoundDrawablesRelative(it, null, null, null) }
            setOnClickListener { openFaqQuestion(question) }
        }
    }

    /**
     * Switches to the 說明 tab, opens the card holding [question] (常見問題 or 認識 Sidepeek),
     * opens that question's answer and scrolls to it.
     */
    private fun openFaqQuestion(question: String) {
        for ((hdrId, grpId) in listOf(R.id.hdrFaq to R.id.grpFaq, R.id.hdrAbout to R.id.grpAbout)) {
            val grp = findViewById<ViewGroup>(grpId)
            val card = (0 until grp.childCount).map { grp.getChildAt(it) }.firstOrNull { c ->
                c.tag == "faq" && ((c as? ViewGroup)?.getChildAt(0) as? ViewGroup)
                    ?.getChildAt(1)?.let { (it as? TextView)?.text?.toString() } == question
            } as? ViewGroup ?: continue
            tab = TAB_HELP; bottomNav.select(TAB_HELP); showPages()
            // Coming from a settings sub-page: show the tab bar again and let back leave normally.
            settingsBack.isEnabled = false; syncTabBar()
            if (grp.visibility != View.VISIBLE) findViewById<View>(hdrId).performClick()
            if (card.getChildAt(1).visibility != View.VISIBLE) card.performClick()
            val scroll = findViewById<ScrollView>(R.id.scroll)
            scroll.post {
                val r = android.graphics.Rect()
                card.getDrawingRect(r)
                scroll.offsetDescendantRectToMyCoords(card, r)
                scroll.smoothScrollTo(0, (r.top - dpx(72f)).coerceAtLeast(0))
            }
            return
        }
        Diagnostics.note(this, "FAQ link: question not found: $question")
    }

    private fun setupTabs(saved: Bundle?) {
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setTabs(listOf(
            GlassTabBar.Tab(R.drawable.ic_tab_home, getString(R.string.tab_home)),
            GlassTabBar.Tab(R.drawable.ic_tab_settings, getString(R.string.tab_settings)),
            GlassTabBar.Tab(R.drawable.ic_tab_help, getString(R.string.tab_help)),
        ))
        tab = (saved?.getInt(STATE_TAB, TAB_HOME) ?: TAB_HOME).coerceIn(0, pageIds.size - 1)
        bottomNav.select(tab)
        showPages()
        bottomNav.onTabSelected = { t ->
            if (t != tab) {
                tab = t
                showPages()
                findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
            }
        }
        bottomNav.onTabReselected = { findViewById<ScrollView>(R.id.scroll).smoothScrollTo(0, 0) }
        keepTabsAboveNavBar()
    }

    /**
     * The tab bar floats over the content. Keep its glass [TAB_GAP_DP] above the navigation
     * bar / gesture strip, and leave room under the content so the bar does not cover its end.
     * One UI with gesture navigation drew the handle over the labels when only the navigation-bar
     * inset was used, so also keep clear of the strip the handle swipe needs; measure the actual
     * overlap so it is right whoever (AppCompat, our root padding) already took the insets.
     */
    private fun keepTabsAboveNavBar() {
        val loc = IntArray(2)
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val density = resources.displayMetrics.density
        val shadowRoom = (GlassTabBar.SHADOW_ROOM_DP * density).toInt()
        val gap = (TAB_GAP_DP * density).toInt()
        // Fade the content out behind the bar (Android cannot blur what is behind the glass).
        val scrim = findViewById<View>(R.id.tabScrim)
        val bg = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground)).run {
            val c = getColor(0, android.graphics.Color.BLACK); recycle(); c
        }
        scrim.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(bg and 0x00FFFFFF, (bg and 0x00FFFFFF) or (0x80 shl 24), (bg and 0x00FFFFFF) or (0xBF shl 24)),
        )
        // Real glass on Android 12+: the bar blurs the page content behind it (not this fade,
        // so the content stays visible through the glass).
        bottomNav.backdropColor = bg
        bottomNav.backdrop = listOf(
            GlassTabBar.Layer(scroll.getChildAt(0), clip = scroll),
        )
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> bottomNav.invalidate() }
        scroll.getChildAt(0).viewTreeObserver.addOnGlobalLayoutListener { bottomNav.invalidate() }
        var lastOverlap = -1
        fun update() {
            val parent = bottomNav.parent as View
            val ins = androidx.core.view.ViewCompat.getRootWindowInsets(parent)
            fun bottom(type: Int) = ins?.getInsets(type)?.bottom ?: 0
            val navBar = bottom(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val gestures = bottom(androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures())
            val nav = maxOf(navBar, gestures)
            parent.getLocationInWindow(loc)
            // Measured on the parent, which does not move when the bar's margin changes.
            val innerBottom = loc[1] + parent.height - parent.paddingBottom
            val overlap = maxOf(0, innerBottom - (parent.rootView.height - nav))
            val margin = overlap + maxOf(0, gap - shadowRoom)
            val lp = bottomNav.layoutParams as ViewGroup.MarginLayoutParams
            if (overlap != lastOverlap) {
                lastOverlap = overlap
                Diagnostics.note(this, "TABS bottom inset nav=$navBar gestures=$gestures pad=$overlap")
            }
            if (lp.bottomMargin != margin) { lp.bottomMargin = margin; bottomNav.layoutParams = lp }
            // Content ends a little above the glass.
            val room = if (bottomNav.visibility == View.VISIBLE) margin + bottomNav.height - shadowRoom + gap / 2 else 0
            if (scroll.paddingBottom != room) scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, room)
            // The fade covers the bar and a bit above it; it starts above the glass so text fades
            // out before it reaches the tab labels.
            val fade = if (room > 0) room + gap else 0
            scrim.visibility = if (room > 0) View.VISIBLE else View.GONE
            if (scrim.layoutParams.height != fade) { scrim.layoutParams.height = fade; scrim.requestLayout() }
        }
        bottomNav.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post { update() } }
        // The bar coming back after a settings sub-page (GONE → VISIBLE) doesn't always change its
        // bounds, so also check after every layout pass; update() only touches what differs.
        scroll.viewTreeObserver.addOnGlobalLayoutListener { update() }
        (bottomNav.parent as View).addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post { update() } }
    }

    private fun showPages() {
        pageIds.forEachIndexed { i, id -> findViewById<View>(id).visibility = vis(i == tab) }
    }

    /** 常見問題、認識 Sidepeek：預設只顯示題目，點一題才展開那一題的答案。 */
    private fun setupFaqAccordion() {
        for (grpId in intArrayOf(R.id.grpFaq, R.id.grpAbout)) setupFaqAccordion(findViewById(grpId))
    }

    private fun setupFaqAccordion(grp: ViewGroup) {
        for (i in 0 until grp.childCount) {
            val card = grp.getChildAt(i) as? ViewGroup ?: continue
            if (card.tag != "faq" || card.childCount < 2) continue
            val q = (card.getChildAt(0) as? ViewGroup)?.getChildAt(1) as? TextView ?: continue
            val a = card.getChildAt(1)
            val sync = { open: Boolean ->
                a.visibility = if (open) View.VISIBLE else View.GONE
                q.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0)
                androidx.core.view.ViewCompat.setStateDescription(card, if (open) getString(R.string.state_expanded) else getString(R.string.state_collapsed))
            }
            sync(false)
            card.isClickable = true
            card.isFocusable = true
            card.clipToOutline = true   // rounded press highlight
            card.foreground = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).run {
                val d = getDrawable(0); recycle(); d
            }
            card.setOnClickListener { sync(a.visibility != View.VISIBLE) }
        }
    }

    /** The system sound list (built-in sounds, default, silent). */
    private fun pickBuiltInSound() {
        val i = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_title))
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Notifier.messageSound(this))
        // No picker on this phone: the system page for the channel can change the sound too.
        try { pickSound.launch(i) } catch (_: android.content.ActivityNotFoundException) {
            safeStart(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.msgChannel(this)))
        }
    }

    /** A ringtone as the picker names it; a custom file it cannot read is just 「自訂」. */
    private fun soundName(uri: Uri?): String {
        if (uri == null) return getString(R.string.sound_silent)
        if (uri == Settings.System.DEFAULT_NOTIFICATION_URI) return getString(R.string.sound_default)
        return try { android.media.RingtoneManager.getRingtone(this, uri)?.getTitle(this) } catch (_: Exception) { null } ?: getString(R.string.custom)
    }

    private fun vis(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private fun isCoreReady(): Boolean {
        val access = isListenerEnabled(this)
        val linked = LineListenerService.connected
        val post = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val a11y = PanelHost.a11yActive || PanelHost.a11yEnabledInSettings(this)
        return access && linked && post && a11y
    }

    private fun refresh() {
        val access = isListenerEnabled(this)
        val linked = LineListenerService.connected
        val post = NotificationManagerCompat.from(this).areNotificationsEnabled()

        statusAccess.text = if (access) getString(R.string.st_done) else getString(R.string.st_access_todo)
        statusListener.text = when {
            linked -> getString(R.string.st_done)
            !access -> getString(R.string.st_do_first)
            else -> getString(R.string.st_press_reconnect)
        }
        statusPost.text = if (post) getString(R.string.st_done) else getString(R.string.st_post_todo)

        // LINE's own sound no longer needs muting: when Sidepeek hides LINE's notification, Android
        // stops that notification's sound/vibration too. The old "mute LINE" card stays hidden.
        statusLineMute.visibility = View.GONE
        findViewById<Button>(R.id.btnLineMute).visibility = View.GONE
        findViewById<Button>(R.id.btnLineMuteDone).visibility = View.GONE

        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        statusBattery.text = if (ignoring)
            getString(R.string.st_battery_ok)
        else
            getString(R.string.st_battery_bad)
        findViewById<Button>(R.id.btnBattery).visibility = vis(!ignoring)

        val oem = OemGuide.guide(this)
        val neverSleepOk = Prefs.neverSleepDone(this) || oem.brand == OemGuide.Brand.OTHER ||
            (ignoring && !oem.needsAutostart)
        statusNeverSleep.text = if (neverSleepOk)
            getString(R.string.st_bg_ok)
        else
            getString(R.string.st_bg_oem, oem.name, oem.steps)
        findViewById<Button>(R.id.btnNeverSleep).text = oem.button
        findViewById<Button>(R.id.btnNeverSleep).visibility = vis(!neverSleepOk)
        findViewById<Button>(R.id.btnNeverSleepDone).visibility = vis(!neverSleepOk)

        val suggest = listOf(!ignoring, !neverSleepOk).count { it }

        val wizardActive = !Prefs.wizardDone(this)
        if (wizardActive) {
            advanceWizardIfNeeded(access, linked, post)
            bindWizardStep()
        } else wizardVideo.setVideo(0)

        // LINE take-over is paused while Sidepeek cannot post (LINE's own notifications are kept).
        // During the wizard's 「允許顯示通知」 step the wizard itself asks, so only warn once past it.
        val repostBlocked = access && Prefs.enabled(this) && !Notifier.canRepost(this) &&
            (!wizardActive || post)
        findViewById<View>(R.id.statusRepost).visibility = vis(repostBlocked)
        findViewById<View>(R.id.btnRepostFix).visibility = vis(repostBlocked)

        // Side panel set-up postponed in the wizard (e.g. during a phone call): keep a to-do here.
        val a11ySet = PanelHost.a11yActive || PanelHost.a11yEnabledInSettings(this)
        if (a11ySet && Prefs.a11yLater(this)) Prefs.set(this, Prefs.KEY_A11Y_LATER, false)
        val showLater = !wizardActive && Prefs.a11yLater(this) && !a11ySet
        findViewById<View>(R.id.statusA11yLater).visibility = vis(showLater)
        findViewById<View>(R.id.btnResumeA11y).visibility = vis(showLater)

        val upd = UpdateChecker.available(this)
        findViewById<TextView>(R.id.statusUpdate).apply {
            visibility = vis(upd != null)
            if (upd != null) text = getString(R.string.st_update, upd.tag.ifBlank { "build ${upd.build}" })
        }
        findViewById<View>(R.id.btnUpdate).visibility = vis(upd != null)

        val full = Diagnostics.full(this)
        findViewById<TextView>(R.id.statusDiag).text = if (full)
            getString(R.string.st_diag_full_on,
                android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MdHHmm")
                    .let { java.text.SimpleDateFormat(it, java.util.Locale.getDefault()).format(java.util.Date(Diagnostics.fullUntil(this))) })
        else getString(R.string.st_diag_full_off)
        findViewById<Button>(R.id.btnDiagFull).text = if (full) getString(R.string.btn_diag_full_stop) else getString(R.string.btn_diag_full_start, Diagnostics.FULL_HOURS)

        statusSummary.text = when {
            wizardActive -> getString(R.string.wiz_progress, wizardStep + 1, WIZARD_STEPS)
            !Prefs.enabled(this) -> getString(R.string.st_off)
            suggest > 0 -> getString(R.string.st_running)
            else -> getString(R.string.st_all_good)
        }

        val dashOn = !wizardActive || showAll
        val dotColor = when {
            wizardActive || !Prefs.enabled(this) -> 0xFF9CA3AF.toInt()
            suggest > 0 -> 0xFFF59E0B.toInt()
            else -> getColor(R.color.section_text)
        }
        findViewById<View>(R.id.statusDot).backgroundTintList = android.content.res.ColorStateList.valueOf(dotColor)
        findViewById<TextView>(R.id.statusSub).apply {
            val sub = when {
                wizardActive -> getString(R.string.st_hint_wizard)
                !Prefs.enabled(this@MainActivity) -> getString(R.string.st_hint_off)
                suggest > 0 -> getString(R.string.st_hint_suggest, suggest)
                else -> null
            }
            visibility = vis(sub != null)
            if (sub != null) text = sub
        }

        // Wizard: only the home page (status + one-step card). Tabs and dash after complete (or showAll escape).
        syncTabBar()
        if (!dashOn && tab != TAB_HOME) { tab = TAB_HOME; bottomNav.select(TAB_HOME) }
        showPages()
        findViewById<View>(R.id.mainSwitches).visibility = vis(dashOn)
        wizardCard.visibility = vis(wizardActive)
        howtoCard.visibility = vis(!wizardActive)
        dashGroup.visibility = vis(dashOn)
        findViewById<View>(R.id.lblSupport).visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.cardFaq).visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.grpFaq).visibility =
            if ((!wizardActive || showAll) && sections.indexOfFirst { it.first == R.id.hdrFaq } in expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardAbout).visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.grpAbout).visibility =
            if ((!wizardActive || showAll) && sections.indexOfFirst { it.first == R.id.hdrAbout } in expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardHelp).visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.grpHelp).visibility =
            if ((!wizardActive || showAll) && sections.indexOfFirst { it.first == R.id.hdrHelp } in expanded) View.VISIBLE else View.GONE
        cardSuggest.visibility = vis((!wizardActive || showAll) && suggest > 0)

        // Never dump long a11y guides onto the dash (they live inside wizard a11y steps).
        a11yGuideBox.visibility = View.GONE

        val a11y = PanelHost.a11yActive
        val a11yOn = PanelHost.a11yEnabledInSettings(this)
        statusA11y.text = when {
            a11y -> getString(R.string.st_a11y_ok)
            a11yOn -> getString(R.string.st_a11y_not_bound)
            else -> getString(R.string.st_a11y_off)
        }
        a11yBox.visibility = vis((!wizardActive || showAll) && Prefs.edgePanel(this) && !a11y)

        // LINE 通知清理: both "clear on open" modes need the accessibility service, not the panel switch.
        val clearOn = Prefs.clearOnLineOpen(this)
        findViewById<View>(R.id.statusLineClearA11y).visibility = vis(clearOn && !a11y)
        findViewById<View>(R.id.whyLineClear).visibility = vis(clearOn && !a11y)
        findViewById<SwitchCompat>(R.id.swSmartClear).isEnabled = clearOn
        findViewById<View>(R.id.hintSmartClear).alpha = if (clearOn) 0.75f else 0.4f

        val usage = hasUsageAccess(this)
        statusUsage.text = when {
            a11y -> getString(R.string.st_usage_a11y)
            usage -> if (Prefs.homeOnly(this)) getString(R.string.st_usage_ok_home) else getString(R.string.st_usage_ok)
            Prefs.homeOnly(this) -> getString(R.string.st_usage_missing)
            else -> getString(R.string.st_usage_optional)
        }
        // Only the backup overlay needs usage access to tell the home screen; the side-panel service does it itself.
        findViewById<View>(R.id.btnUsage).visibility = vis(!a11y && !usage)
        // Only next to the warning (Peek 條 shows everywhere because usage access is missing).
        findViewById<View>(R.id.whyUsage).visibility = vis(!a11y && !usage && Prefs.homeOnly(this))
        findViewById<Button>(R.id.btnSound).text = getString(R.string.btn_sound_named, soundName(Notifier.messageSound(this)))

        val overlay = Settings.canDrawOverlays(this)
        statusOverlay.text = when {
            a11y -> getString(R.string.st_edge_a11y)
            !overlay -> getString(R.string.st_edge_need_a11y)
            Prefs.edgePanel(this) && EdgePanelService.running -> getString(R.string.st_edge_overlay)
            Prefs.edgePanel(this) -> getString(R.string.st_edge_overlay_warn)
            else -> getString(R.string.st_edge_off)
        }
        backupBox.visibility = vis(!a11y)
    }

    /** Auto-advance when the current step's completion condition becomes true. */
    private fun advanceWizardIfNeeded(access: Boolean, linked: Boolean, post: Boolean) {
        val a11yOk = PanelHost.a11yActive || PanelHost.a11yEnabledInSettings(this)
        var guard = 0
        while (guard++ < WIZARD_STEPS) {
            val next = when (wizardStep) {
                STEP_LISTENER -> if (access && linked) STEP_POST else -1
                STEP_POST -> if (post) STEP_EDGE else -1
                STEP_EDGE -> if (Prefs.edgePanel(this)) STEP_TRY_A11Y else -1
                // Already enabled (upgrade / re-run): skip the Samsung denial dance.
                STEP_TRY_A11Y -> if (a11yOk) STEP_BATTERY else -1
                STEP_RESTRICTED -> if (a11yOk) STEP_BATTERY else -1
                STEP_REOPEN_A11Y -> if (a11yOk) STEP_BATTERY else -1
                STEP_BATTERY -> {
                    val ignoring = getSystemService(PowerManager::class.java)
                        .isIgnoringBatteryOptimizations(packageName)
                    // Xiaomi/OPPO/vivo…: battery exemption alone isn't enough, auto-start too.
                    if (ignoring && (!OemGuide.guide(this).needsAutostart || Prefs.neverSleepDone(this))) STEP_DONE else -1
                }
                else -> -1
            }
            if (next < 0) break
            wizardStep = next
        }
        if (wizardStep > STEP_DONE) wizardStep = STEP_DONE
    }

    private fun bindWizardStep() {
        val access = isListenerEnabled(this)
        wizardProgress.text = getString(R.string.wiz_progress, wizardStep + 1, WIZARD_STEPS)
        wizardSecondary.visibility = View.GONE
        wizardSkip.visibility = View.GONE
        wizardImage.visibility = View.GONE
        wizardImage.setImageDrawable(null)
        var video = 0   // screen-recording clip for steps done in system settings
        val panelStep = wizardStep == STEP_EDGE || wizardStep == STEP_TRY_A11Y ||
            wizardStep == STEP_RESTRICTED || wizardStep == STEP_REOPEN_A11Y

        when (wizardStep) {
            STEP_LISTENER -> {
                wizardTitle.text = getString(R.string.wiz_access_title)
                if (!access && !Prefs.listenerTryOpened(this)) {
                    wizardHint.text = getString(R.string.wiz_access_hint)
                    wizardPrimary.text = getString(R.string.wiz_access_btn)
                    video = R.raw.guide_step1
                } else if (!access) {
                    // Came back without access: on stock Android the switch is greyed out by Restricted Settings.
                    wizardTitle.text = getString(R.string.wiz_access_grey_title)
                    wizardHint.text = getString(R.string.wiz_access_grey_1) +
                        getString(R.string.wiz_access_grey_2) +
                        getString(R.string.wiz_access_grey_3) +
                        getString(R.string.wiz_access_grey_4)
                    wizardPrimary.text = getString(R.string.wiz_access_btn)
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = getString(R.string.wiz_access_unrestrict)
                    video = R.raw.guide_step1r
                } else {
                    wizardHint.text = getString(R.string.wiz_reconnect_hint)
                    wizardPrimary.text = getString(R.string.wiz_reconnect)
                }
            }
            STEP_POST -> {
                wizardTitle.text = getString(R.string.wiz_post_title)
                wizardHint.text = getString(R.string.wiz_post_hint)
                wizardPrimary.text = getString(R.string.wiz_post_title)
            }
            STEP_EDGE -> {
                wizardTitle.text = getString(R.string.wiz_panel_title)
                wizardHint.text = getString(R.string.wiz_panel_hint)
                wizardPrimary.text = getString(R.string.wiz_panel_title)
                wizardImage.visibility = View.VISIBLE
                wizardImage.setImageDrawable(SidebarStepDrawable(this))
            }
            STEP_TRY_A11Y -> {
                wizardTitle.text = getString(R.string.wiz_a11y_title)
                wizardHint.text = getString(R.string.wiz_a11y_hint) +
                    getString(R.string.wiz_a11y_hint_shortcut)
                wizardPrimary.text = getString(R.string.wiz_a11y_btn)
                video = R.raw.guide_step4
                if (Prefs.a11yTryOpened(this)) {
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = getString(R.string.wiz_a11y_denied_next)
                }
            }
            STEP_RESTRICTED -> {
                wizardTitle.text = getString(R.string.wiz_restricted_title)
                wizardHint.text = getString(R.string.wiz_restricted_hint)
                wizardPrimary.text = getString(R.string.wiz_open_app_info)
                video = R.raw.guide_step5
                if (Prefs.a11yAppInfoOpened(this)) {
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = getString(R.string.wiz_restricted_next)
                }
            }
            STEP_REOPEN_A11Y -> {
                wizardTitle.text = getString(R.string.wiz_a11y2_title)
                wizardHint.text = getString(R.string.wiz_a11y2_hint) +
                    getString(R.string.wiz_a11y2_hint_shortcut)
                wizardPrimary.text = getString(R.string.wiz_a11y_btn)
                video = R.raw.guide_step6
            }
            STEP_BATTERY -> {
                val g = OemGuide.guide(this)
                val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
                if (!ignoring) {
                    wizardTitle.text = getString(R.string.wiz_battery_title)
                    wizardHint.text = getString(R.string.wiz_battery_hint) +
                        (if (g.needsAutostart) getString(R.string.wiz_battery_autostart, g.name) else "") + getString(R.string.wiz_battery_skippable)
                    wizardPrimary.text = getString(R.string.wiz_battery_btn)
                } else {
                    // Battery done; this phone brand also needs its own auto-start switch.
                    wizardTitle.text = getString(R.string.wiz_autostart_title, g.name)
                    wizardHint.text = g.steps + getString(R.string.wiz_autostart_hint)
                    wizardPrimary.text = g.button
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = getString(R.string.wiz_autostart_next)
                }
                wizardSkip.visibility = View.VISIBLE
                wizardSkip.text = getString(R.string.wiz_skip)
            }
            STEP_DONE -> {
                wizardTitle.text = getString(R.string.wiz_done_title)
                wizardHint.text = getString(R.string.wiz_done_hint)
                wizardPrimary.text = getString(R.string.wiz_done_btn)
                wizardImage.visibility = View.VISIBLE
                wizardImage.setImageDrawable(StepsOverviewDrawable(this))
            }
        }
        if (panelStep) {
            // Android blocks turning on accessibility / restricted settings during a call (anti-scam).
            if (wizardStep != STEP_EDGE && inCall()) {
                wizardHint.text = getString(R.string.wiz_in_call_1) +
                    getString(R.string.wiz_in_call_2) + wizardHint.text
            }
            wizardSkip.visibility = View.VISIBLE
            wizardSkip.text = getString(R.string.wiz_later)
        }
        wizardVideo.setVideo(video)
    }

    /** Phone or VoIP call in progress (no permission needed). */
    private fun inCall(): Boolean = try {
        val mode = getSystemService(android.media.AudioManager::class.java)?.mode
        mode == android.media.AudioManager.MODE_IN_CALL || mode == android.media.AudioManager.MODE_IN_COMMUNICATION
    } catch (_: Exception) { false }

    /** From the status card: reopen the wizard at the side-panel steps. */
    private fun resumeA11ySetup() {
        Prefs.set(this, Prefs.KEY_WIZARD_DONE_V1, false)
        wizardStep = if (Prefs.edgePanel(this)) STEP_TRY_A11Y else STEP_EDGE
        refresh()
        findViewById<View>(android.R.id.content).post {
            findViewById<ScrollView>(R.id.scroll).smoothScrollTo(0, wizardCard.top)
        }
    }

    private fun onWizardPrimary() {
        when (wizardStep) {
            STEP_LISTENER -> {
                if (!isListenerEnabled(this)) {
                    Prefs.set(this, Prefs.KEY_LISTENER_TRY_OPENED_V1, true)
                    safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } else {
                    rebindListener()
                }
            }
            STEP_POST -> requestPostNotifications()
            STEP_EDGE -> {
                Prefs.set(this, Prefs.KEY_EDGE_PANEL, true)
                // Flip the switch silently: its listener would jump straight to the Accessibility
                // settings, skipping the step-4 explanation. The user opens it from step 4 instead.
                suppressEdgeSwitch = true
                swEdge.isChecked = true
                suppressEdgeSwitch = false
                PanelHost.start(this)
                wizardStep = STEP_TRY_A11Y
                refresh()
            }
            STEP_TRY_A11Y -> openA11ySettings(markTry = true)
            STEP_RESTRICTED -> openA11yAppInfo()
            STEP_REOPEN_A11Y -> openA11ySettings(markTry = false)
            STEP_BATTERY -> {
                val ignoring = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
                if (!ignoring) requestBatteryExemption() else openBackgroundSettings()
            }
            STEP_DONE -> finishWizard()
        }
    }

    private fun onWizardSecondary() {
        when (wizardStep) {
            STEP_LISTENER -> {
                val ok = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                if (!ok) safeStart(Intent(Settings.ACTION_SETTINGS))
                Toast.makeText(this, getString(R.string.toast_restricted_access), Toast.LENGTH_LONG).show()
            }
            STEP_BATTERY -> {
                Prefs.set(this, Prefs.KEY_NEVER_SLEEP_DONE, true)
                wizardStep = STEP_DONE
                refresh()
            }
            STEP_TRY_A11Y -> {
                wizardStep = STEP_RESTRICTED
                refresh()
            }
            STEP_RESTRICTED -> {
                wizardStep = STEP_REOPEN_A11Y
                refresh()
            }
        }
    }

    private fun onWizardSkip() {
        when (wizardStep) {
            STEP_BATTERY -> {
                wizardStep = STEP_DONE
                refresh()
            }
            STEP_EDGE, STEP_TRY_A11Y, STEP_RESTRICTED, STEP_REOPEN_A11Y -> {
                // Notification tidying works without the side panel; come back to it from the status card.
                Prefs.set(this, Prefs.KEY_A11Y_LATER, true)
                wizardStep = STEP_BATTERY
                Toast.makeText(this, getString(R.string.toast_panel_later), Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun finishWizard() {
        Prefs.set(this, Prefs.KEY_WIZARD_DONE_V1, true)
        if (!Prefs.edgePanel(this) && !Prefs.a11yLater(this)) {
            Prefs.set(this, Prefs.KEY_EDGE_PANEL, true)
            swEdge.isChecked = true
        }
        if (Prefs.edgePanel(this) && !PanelHost.running()) PanelHost.start(this)
        Toast.makeText(this, getString(R.string.wiz_done_title), Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun rebindListener() {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, LineListenerService::class.java)
            )
            Diagnostics.note(this, "LISTENER requestRebind from UI")
            Toast.makeText(this, getString(R.string.toast_reconnecting), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Diagnostics.fail(this, "LISTENER UI requestRebind", e)
            Toast.makeText(this, getString(R.string.toast_reconnect_failed), Toast.LENGTH_LONG).show()
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        statusListener.postDelayed({ refresh() }, 800)
    }

    /** 「打開通知設定」 on the warning shown while Sidepeek cannot post (see Notifier.canRepost). */
    private fun openRepostSettings() {
        val appSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            // The settings page works even after the permission dialog was denied twice
            // (the system then stops showing the dialog).
            safeStart(appSettings)
            return
        }
        // App notifications are on, so the 「LINE 訊息預覽」 channel itself is off.
        Notifier.ensureChannels(this)
        val ok = safeStart(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.msgChannel(this)))
        if (!ok) safeStart(appSettings)
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPost.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            safeStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openPanelNow() {
        if (!PanelHost.a11yActive && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.toast_panel_need_a11y), Toast.LENGTH_LONG).show()
            openA11yEnablePath(); return
        }
        if (!Prefs.edgePanel(this)) { swEdge.isChecked = true }
        PanelHost.open(this)
    }

    /** Open App Info so user can allow Restricted Settings — only after trying a11y (Samsung). */
    private fun openA11yAppInfo() {
        if (!Prefs.a11yConsent(this)) { A11yDisclosure.ensure(this) { openA11yAppInfo() }; return }
        Prefs.set(this, Prefs.KEY_A11Y_APPINFO_OPENED_V1, true)
        val ok = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        if (!ok) safeStart(Intent(Settings.ACTION_SETTINGS))
        Toast.makeText(this, getString(R.string.toast_restricted_a11y), Toast.LENGTH_LONG).show()
    }

    /** Open Accessibility settings to toggle the sidebar service. */
    private fun openA11ySettings(markTry: Boolean = true) {
        if (!Prefs.a11yConsent(this)) { A11yDisclosure.ensure(this) { openA11ySettings(markTry) }; return }
        if (markTry) Prefs.set(this, Prefs.KEY_A11Y_TRY_OPENED_V1, true)
        if (!safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) safeStart(Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Correct Samsung / Android 13+ order: try Accessibility first (may show denial),
     * only then App Info for 允許受限設定. Do NOT send users to App Info first.
     */
    private fun openA11yEnablePath() {
        if (!Prefs.a11yTryOpened(this) || !Prefs.a11yAppInfoOpened(this)) {
            // Prefer Accessibility first; if they already tried and still blocked, App Info.
            if (Prefs.a11yTryOpened(this) && !Prefs.a11yAppInfoOpened(this)) openA11yAppInfo()
            else openA11ySettings(markTry = true)
        } else {
            openA11ySettings(markTry = false)
        }
    }

    private fun openUsageSettings() {
        val ok = safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        if (!ok) safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun setupPanelAppearance() {
        bindSeek(R.id.sbPanelW, R.id.lblPanelW, Prefs.KEY_PANEL_WIDTH, Prefs.PW_MIN, Prefs.PW_MAX, Prefs.panelWidth(this)) { getString(R.string.seek_panel_w, it) }
        bindSeek(R.id.sbPanelH, R.id.lblPanelH, Prefs.KEY_PANEL_HEIGHT, Prefs.PH_MIN, Prefs.PH_MAX, Prefs.panelHeight(this)) { getString(R.string.seek_panel_h, it) }
        bindSeek(R.id.sbPanelML, R.id.lblPanelML, Prefs.KEY_PANEL_MAXLINES, Prefs.ML_MIN, Prefs.ML_MAX, Prefs.panelMaxLines(this)) { getString(R.string.seek_max_lines, it) }
        bindSeek(R.id.sbPanelR, R.id.lblPanelR, Prefs.KEY_PANEL_RADIUS, 0, Prefs.PR_MAX, Prefs.panelRadius(this)) { getString(R.string.seek_radius, it) }

        val rgPos = findViewById<RadioGroup>(R.id.rgPanelPos)
        rgPos.check(when (Prefs.panelPosition(this)) {
            Prefs.POS_TOP -> R.id.rbPosTop; Prefs.POS_BOTTOM -> R.id.rbPosBottom; else -> R.id.rbPosCenter
        })
        rgPos.setOnCheckedChangeListener { _, id ->
            Prefs.setString(this, Prefs.KEY_PANEL_POS, when (id) {
                R.id.rbPosTop -> Prefs.POS_TOP; R.id.rbPosBottom -> Prefs.POS_BOTTOM; else -> Prefs.POS_CENTER
            })
        }
        val rgText = findViewById<RadioGroup>(R.id.rgPanelText)
        rgText.check(when (Prefs.panelTextSize(this)) { 0 -> R.id.rbTextS; 2 -> R.id.rbTextL; else -> R.id.rbTextM })
        rgText.setOnCheckedChangeListener { _, id ->
            Prefs.setInt(this, Prefs.KEY_PANEL_TEXT, when (id) { R.id.rbTextS -> 0; R.id.rbTextL -> 2; else -> 1 })
        }
        findViewById<Button>(R.id.btnPanelPreview).setOnClickListener { openPanelNow() }
        findViewById<TextView>(R.id.linkFeedback).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { Feedback.send(this@MainActivity, getString(R.string.feedback_subject)) }
        }
        findViewById<Button>(R.id.btnFeedback).setOnClickListener { Feedback.send(this, getString(R.string.report_subject)) }
        // 常見問題「收不到訊息？」: per-app notifications, then this phone's battery steps.
        findViewById<TextView>(R.id.tipBackground).text = OemGuide.guide(this).let { g ->
            val battery = if (g.brand == OemGuide.Brand.OTHER) getString(R.string.tip_battery_generic)
                else getString(R.string.tip_battery_oem, g.name, g.steps)
            getString(R.string.tip_background, battery)
        }
        findViewById<TextView>(R.id.linkPrivacy).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { startActivity(Intent(this@MainActivity, PrivacyActivity::class.java)) }
        }
        findViewById<TextView>(R.id.linkWebsite).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))) }
        }
        findViewById<SwitchCompat>(R.id.swPanelScrim).apply {
            setTitleSummary(getString(R.string.dim_title), getString(R.string.dim_summary))
            isChecked = Prefs.panelScrim(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_PANEL_SCRIM, on) }
        }
        findViewById<Button>(R.id.btnPanelReset).setOnClickListener {
            Prefs.sp(this).edit().apply { Prefs.PANEL_KEYS.forEach { remove(it) } }.apply()
            // Re-bind the controls in place. recreate() used to bring the old slider / option
            // positions back (saved view state), so the controls disagreed with the preview.
            setupPanelAppearance()
            Toast.makeText(this, getString(R.string.toast_panel_reset), Toast.LENGTH_SHORT).show()
        }
    }

    /** 狀態列小圖示：每格 id（內建圖示的 id 或 NotifIcons.CUSTOM）→ 那一格。 */
    private val notifIconCells = LinkedHashMap<String, View>()

    /** 通知外觀 → 狀態列小圖示：內建圖示一排五格，最後一格是「自訂」。 */
    private fun setupNotifIconPicker() {
        val box = findViewById<LinearLayout>(R.id.iconPicker)
        box.removeAllViews(); notifIconCells.clear()
        val ids = NotifIcons.CHOICES.map { it.id } + NotifIcons.CUSTOM
        var row: LinearLayout? = null
        ids.forEachIndexed { i, id ->
            if (i % 5 == 0) row = LinearLayout(this).also { box.addView(it) }
            val cell = android.widget.FrameLayout(this).apply {
                minimumHeight = dpx(52f)
                isClickable = true; isFocusable = true
                contentDescription = NotifIcons.CHOICES.firstOrNull { it.id == id }?.label?.let { getString(it) } ?: getString(R.string.custom)
                setOnClickListener { onNotifIconTapped(id) }
            }
            row!!.addView(cell, LinearLayout.LayoutParams(0, dpx(52f), 1f).apply {
                setMargins(dpx(3f), dpx(3f), dpx(3f), dpx(3f))
            })
            notifIconCells[id] = cell
        }
        // Keep the last row's cells the same width as the others.
        val rest = (5 - ids.size % 5) % 5
        repeat(rest) { row!!.addView(View(this), LinearLayout.LayoutParams(0, dpx(52f), 1f).apply {
            setMargins(dpx(3f), dpx(3f), dpx(3f), dpx(3f))
        }) }
        syncNotifIconPicker()
    }

    private fun onNotifIconTapped(id: String) {
        if (id == NotifIcons.CUSTOM && (!NotifIcons.hasCustom(this) || Prefs.notifIcon(this) == NotifIcons.CUSTOM)) {
            // No picture yet, or 自訂 is already chosen: pick a (new) picture.
            try {
                pickNotifIcon.launch(androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: Exception) { Toast.makeText(this, getString(R.string.toast_no_picker), Toast.LENGTH_SHORT).show() }
            return
        }
        if (Prefs.notifIcon(this) == id) return
        Prefs.setString(this, Prefs.KEY_NOTIF_ICON, id)
        Notifier.refreshAllSilently(this)
        syncNotifIconPicker()
    }

    /** Draws each cell: the icon (tinted like text), and a green ring around the chosen one. */
    private fun syncNotifIconPicker() {
        val chosen = Prefs.notifIcon(this).let { if (it == NotifIcons.CUSTOM && !NotifIcons.hasCustom(this)) NotifIcons.DEFAULT else it }
        val ink = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary)).run {
            val c = getColor(0, android.graphics.Color.GRAY); recycle(); c
        }
        val green = ContextCompat.getColor(this, R.color.section_text)
        for ((id, cell) in notifIconCells) {
            val sel = id == chosen
            cell.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpx(14f).toFloat()
                setColor(if (sel) (green and 0x00FFFFFF) or 0x26000000 else 0x14888888)
                setStroke(dpx(if (sel) 2f else 1f), if (sel) green else 0x33888888)
            }
            (cell as ViewGroup).removeAllViews()
            val custom = id == NotifIcons.CUSTOM
            val bmp = if (custom) NotifIcons.customBitmap(this) else null
            if (custom && bmp == null) {
                cell.addView(TextView(this).apply {
                    text = getString(R.string.icon_custom_cell); textSize = 11f; gravity = android.view.Gravity.CENTER
                    setTextColor(ink); setLineSpacing(0f, 0.9f)
                }, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                continue
            }
            cell.addView(ImageView(this).apply {
                if (bmp != null) setImageBitmap(bmp)
                else setImageResource(NotifIcons.CHOICES.first { it.id == id }.res)
                imageTintList = android.content.res.ColorStateList.valueOf(if (sel) green else ink)
            }, android.widget.FrameLayout.LayoutParams(dpx(26f), dpx(26f), android.view.Gravity.CENTER))
        }
    }

    private fun dpx(v: Float) = (v * resources.displayMetrics.density).toInt()

    /**
     * 「側邊欄：要收哪些 App」: the well-known apps (LINE, IG, Threads, Messenger, Teams) plus any
     * other app the user picked, each with its icon and an on/off switch, and a button that opens
     * a searchable list of every app on the phone.
     */
    private fun setupSourceSwitches() {
        val box = findViewById<LinearLayout>(R.id.appSwitches)
        box.removeAllViews()
        val pm = packageManager
        val selected = Sources.selected(this)

        fun row(icon: android.graphics.drawable.Drawable?, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val sw = SwitchCompat(this).apply {
                text = label
                textSize = 16f
                minHeight = dpx(52f)
                isChecked = checked
                icon?.let { d ->
                    val sized = d.mutate().apply { setBounds(0, 0, dpx(30f), dpx(30f)) }
                    setCompoundDrawablesRelative(sized, null, null, null)
                }
                compoundDrawablePadding = dpx(12f)
                setOnCheckedChangeListener { _, on -> onChange(on) }
            }
            box.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        fun iconOf(pkg: String) = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }

        // Well-known apps first (installed ones only).
        for (src in Sources.KNOWN) {
            val pkg = src.packages.firstOrNull { Sources.isInstalled(this, it) } ?: continue
            row(iconOf(pkg), src.name, Sources.isEnabled(this, src)) { on -> Sources.setEnabled(this, src, on) }
        }
        // Then every other app the user picked.
        val known = Sources.KNOWN.flatMap { it.packages }.toSet()
        selected.filter { it !in known && Sources.isInstalled(this, it) }
            .map { it to Sources.displayName(this, it) }
            .sortedBy { it.second }
            .forEach { (pkg, label) ->
                row(iconOf(pkg), label, true) { on -> Sources.setPackageSelected(this, pkg, on) }
            }

        box.addView(com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.apps_pick_other)
            setOnClickListener { openAppPicker() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpx(6f) })
    }

    /** Searchable multi-select list of every launchable app on the phone. */
    private fun openAppPicker() {
        Toast.makeText(this, getString(R.string.apps_loading), Toast.LENGTH_SHORT).show()
        Thread {
            val apps = Sources.installedApps(this)
            runOnUiThread { if (!isFinishing) showAppPicker(apps) }
        }.start()
    }

    private fun showAppPicker(all: List<InstalledApp>) {
        val chosen = HashSet(Sources.selected(this))
        val known = Sources.KNOWN.flatMap { it.packages }.toSet()
        // Picked apps first, then the well-known ones, then everything else A–Z.
        val ordered = all.sortedWith(compareBy<InstalledApp>({ it.pkg !in chosen }, { it.pkg !in known }))
        var shown = ordered

        val list = android.widget.ListView(this)
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val app = shown[i]
                val cb = (convertView as? android.widget.CheckBox) ?: android.widget.CheckBox(this@MainActivity).apply {
                    textSize = 16f
                    minHeight = dpx(52f)
                    compoundDrawablePadding = dpx(12f)
                    isClickable = false; isFocusable = false
                }
                cb.text = app.label
                cb.isChecked = app.pkg in chosen
                val d = app.icon?.constantState?.newDrawable()?.mutate() ?: app.icon
                d?.setBounds(0, 0, dpx(30f), dpx(30f))
                cb.setCompoundDrawablesRelative(null, null, d, null)
                return cb
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, view, pos, _ ->
            val pkg = shown[pos].pkg
            if (!chosen.add(pkg)) chosen.remove(pkg)
            (view as? android.widget.CheckBox)?.isChecked = pkg in chosen
        }

        val search = android.widget.EditText(this).apply {
            hint = getString(R.string.apps_search)
            setSingleLine()
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) {
                    val q = e?.toString()?.trim().orEmpty()
                    shown = if (q.isEmpty()) ordered else ordered.filter {
                        it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true)
                    }
                    adapter.notifyDataSetChanged()
                }
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpx(20f), dpx(8f), dpx(20f), 0)
            addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpx(420f)))
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.apps_dialog_title))
            .setView(content)
            .setPositiveButton(getString(R.string.done)) { _, _ ->
                Sources.setSelected(this, chosen)
                setupSourceSwitches()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupHandleSettings() {
        val rg = findViewById<RadioGroup>(R.id.rgSide)
        rg.check(if (Prefs.handleSide(this) == Prefs.SIDE_LEFT) R.id.rbLeft else R.id.rbRight)
        rg.setOnCheckedChangeListener { _, id ->
            Prefs.setString(this, Prefs.KEY_HANDLE_SIDE, if (id == R.id.rbLeft) Prefs.SIDE_LEFT else Prefs.SIDE_RIGHT)
        }
        bindSeek(R.id.sbInset, R.id.lblInset, Prefs.KEY_HANDLE_INSET, 0, Prefs.INSET_MAX, Prefs.handleInset(this)) { getString(R.string.seek_inset, it) }
        bindSeek(R.id.sbWidth, R.id.lblWidth, Prefs.KEY_HANDLE_WIDTH, Prefs.WIDTH_MIN, Prefs.WIDTH_MAX, Prefs.handleWidth(this)) { getString(R.string.seek_handle_w, it) }
        bindSeek(R.id.sbHeight, R.id.lblHeight, Prefs.KEY_HANDLE_HEIGHT, Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX, Prefs.handleHeight(this)) { getString(R.string.seek_handle_h, it) }
        bindSeek(R.id.sbOpacity, R.id.lblOpacity, Prefs.KEY_HANDLE_OPACITY, Prefs.OPACITY_MIN, Prefs.OPACITY_MAX, Prefs.handleOpacity(this)) {
            getString(R.string.seek_opacity, it)
        }
        val sensName = arrayOf("", getString(R.string.sens_1), getString(R.string.sens_2), getString(R.string.sens_3), getString(R.string.sens_4), getString(R.string.sens_5))
        bindSeek(R.id.sbSwipeSens, R.id.lblSwipeSens, Prefs.KEY_SWIPE_SENS, Prefs.SENS_MIN, Prefs.SENS_MAX, Prefs.swipeSensitivity(this)) {
            getString(R.string.seek_sens, sensName[it])
        }

        // 「什麼時候顯示」: each switch carries its own one-line explanation.
        findViewById<SwitchCompat>(R.id.swHomeOnly).setTitleSummary(getString(R.string.sw_home_only), getString(R.string.sw_home_only_sum))
        findViewById<SwitchCompat>(R.id.swHideIme).setTitleSummary(getString(R.string.sw_hide_ime), getString(R.string.sw_hide_ime_sum))
        findViewById<SwitchCompat>(R.id.swClearOnLine).setTitleSummary(getString(R.string.sw_clear_on_line), getString(R.string.sw_clear_on_line_sum))
        findViewById<SwitchCompat>(R.id.swSmartClear).setTitleSummary(getString(R.string.sw_smart_clear), getString(R.string.sw_smart_clear_sum))
        findViewById<SwitchCompat>(R.id.swShowOnLock).setTitleSummary(getString(R.string.sw_on_lock), getString(R.string.sw_on_lock_sum))

        findViewById<Button>(R.id.btnResetHandle).setOnClickListener {
            Prefs.writeHandleDefaults(this)
            // Re-bind in place (recreate() restored the old control positions, see btnPanelReset).
            setupHandleSettings()
            Toast.makeText(this, getString(R.string.toast_handle_reset), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnGroupPics).setOnClickListener {
            startActivity(Intent(this, GroupPicsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAddTile).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                val sbm = getSystemService(android.app.StatusBarManager::class.java)
                sbm.requestAddTileService(
                    android.content.ComponentName(this, PanelTileService::class.java),
                    getString(R.string.tile_label),
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_msg),
                    mainExecutor
                ) { result ->
                    val msg = when (result) {
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> getString(R.string.tile_added)
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> getString(R.string.tile_already)
                        else -> getString(R.string.tile_not_added)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.tile_manual), Toast.LENGTH_LONG).show()
            }
        }
    }


    /** A switch label with a smaller, dimmer second line, like system settings rows. */
    private fun SwitchCompat.setTitleSummary(title: String, summary: String) {
        val dim = obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary)).run {
            val c = getColor(0, 0xFF888888.toInt()); recycle(); c
        }
        text = android.text.SpannableStringBuilder(title).append("\n").apply {
            val start = length
            append(summary)
            setSpan(android.text.style.RelativeSizeSpan(0.82f), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(dim), start, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        setLineSpacing(resources.displayMetrics.density * 2, 1f)
    }

    private fun bindSeek(seekId: Int, labelId: Int, key: String, min: Int, max: Int, value: Int,
                         label: (Int) -> String) {
        val sb = findViewById<SeekBar>(seekId)
        val lbl = findViewById<TextView>(labelId)
        // 版面是「名稱｜數值」或「名稱｜滑桿｜數值」一列：label 文字以「：」切開，數值放到同一列最後一個文字格。
        val valueView = (lbl.parent as? ViewGroup)?.let { g ->
            (g.childCount - 1 downTo 0).map { g.getChildAt(it) }.firstOrNull { it is TextView && it !== lbl } as? TextView
        }
        val show = { v: Int ->
            val text = label(v)
            val cut = text.indexOfFirst { it == '：' || it == ':' }
            if (valueView != null && cut > 0) {
                lbl.text = text.substring(0, cut)
                valueView.text = text.substring(cut + 1).trim()
                sb.contentDescription = text
            } else lbl.text = text
        }
        sb.max = max - min
        sb.progress = value - min
        show(value)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                show(p + min)
                if (fromUser) Prefs.setInt(this@MainActivity, key, p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    @SuppressLint("BatteryLife")
    private fun openLineNotificationSettings() {
        val chId = Prefs.lineChannelId(this)
        // Prefer channel page when known, then system app notification settings for LINE.
        if (!chId.isNullOrBlank()) {
            val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, LineListenerService.LINE_PKG)
                .putExtra(Settings.EXTRA_CHANNEL_ID, chId)
            if (safeStart(channel)) {
                Toast.makeText(this, getString(R.string.toast_channel_silent), Toast.LENGTH_LONG).show()
                return
            }
        }
        val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, LineListenerService.LINE_PKG)
        if (safeStart(app)) {
            Toast.makeText(this, getString(R.string.toast_line_sound_off), Toast.LENGTH_LONG).show()
            return
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${LineListenerService.LINE_PKG}"))
        if (safeStart(details)) {
            Toast.makeText(this, getString(R.string.toast_line_sound_off), Toast.LENGTH_LONG).show()
            return
        }
        packageManager.getLaunchIntentForPackage(LineListenerService.LINE_PKG)?.let {
            if (safeStart(it)) return
        }
        Toast.makeText(this, getString(R.string.toast_open_line_notif), Toast.LENGTH_LONG).show()
    }

    /** Brand-specific background / auto-start screen (Samsung 永不休眠, Xiaomi 自動啟動…). */
    private fun openBackgroundSettings() {
        if (!OemGuide.open(this)) Toast.makeText(this, getString(R.string.toast_open_battery), Toast.LENGTH_LONG).show()
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, getString(R.string.toast_battery_ok), Toast.LENGTH_SHORT).show()
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }
        if (!BuildConfig.DIRECT_BATTERY_REQUEST) {
            // Play 版：沒有 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，帶到 App 資訊頁讓使用者自己選。
            // Android 12 以上（含 One UI）「電池 → 不受限制」同時把 App 加進電池最佳化例外。
            val ok = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            if (ok) Toast.makeText(this, getString(R.string.toast_battery_app_info), Toast.LENGTH_LONG).show()
            else safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }
        val ok = safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        if (!ok) safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openOverlaySettings() {
        val ok = safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        if (!ok) safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    private fun exportDiagnostics() {
        try {
            val f = Diagnostics.export(this)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diag_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.diag_share)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_export_failed, e.toString()), Toast.LENGTH_LONG).show()
        }
    }

    private fun safeStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val WEBSITE = "https://sidepeekapp.github.io/Sidepeek/"
        private const val STATE_EXPANDED = "main.expanded"
        private const val STATE_SHOW_ALL = "main.showAll"
        private const val STATE_WIZARD_STEP = "main.wizardStep"
        private const val STATE_TAB = "main.tab"
        private const val STATE_SETTINGS_PAGE = "main.settingsPage"
        /** 設定分頁（pageIds 的第二頁）。 */
        private const val TAB_SETTINGS = 1
        /** Gap between the floating tab bar's glass and the bottom of the screen (reference: bottom-6). */
        private const val TAB_GAP_DP = 24
        private const val TAB_HOME = 0
        /** 說明分頁（pageIds 的第三頁）。 */
        private const val TAB_HELP = 2

        private const val WIZARD_STEPS = 8
        private const val STEP_LISTENER = 0
        private const val STEP_POST = 1
        private const val STEP_EDGE = 2
        private const val STEP_TRY_A11Y = 3
        private const val STEP_RESTRICTED = 4
        private const val STEP_REOPEN_A11Y = 5
        private const val STEP_BATTERY = 6
        private const val STEP_DONE = 7
    }
}

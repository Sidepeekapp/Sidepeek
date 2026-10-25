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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    private val requestPost = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

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

    /** 進階設定的收合區塊：標題 id、內容 id、標題文字。 */
    private val sections = listOf(
        Triple(R.id.hdrLook, R.id.grpLook, "通知外觀"),
        Triple(R.id.hdrPanelMode, R.id.grpPanelMode, "側邊欄：顯示方式"),
        Triple(R.id.hdrSources, R.id.grpSources, "側邊欄：要收哪些 App"),
        Triple(R.id.hdrHandle, R.id.grpHandle, "側邊欄：Peek 條與外觀"),
        Triple(R.id.hdrFaq, R.id.grpFaq, "常見問題"),
        Triple(R.id.hdrHelp, R.id.grpHelp, "遇到問題"),
    )
    /** 目前展開的區塊（存 sections 的索引，旋轉螢幕或 recreate() 後會還原）。 */
    private val expanded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

        wizardStep = savedInstanceState?.getInt(STATE_WIZARD_STEP, 0) ?: 0
        setupCollapsibles(savedInstanceState)

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
            Toast.makeText(this, "好，記下來了：LINE 自己的通知已關閉", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnNeverSleep).setOnClickListener { openNeverSleepSettings() }
        findViewById<Button>(R.id.btnNeverSleepDone).setOnClickListener {
            Prefs.set(this, Prefs.KEY_NEVER_SLEEP_DONE, true); refresh()
            Toast.makeText(this, "好，記下來了：已加入永不休眠", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "要先允許「Sidepeek」顯示通知，才看得到測試通知", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "側邊欄要先開啟「側邊欄服務」。請先試開無障礙；若被拒絕再允許受限設定", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "請允許「讀取使用情況」，不然 Peek 條會一直顯示", Toast.LENGTH_LONG).show()
                openUsageSettings()
            }
            refresh()
        }
        findViewById<Button>(R.id.btnUsage).setOnClickListener { openUsageSettings() }
        findViewById<SwitchCompat>(R.id.swHideIme).apply {
            isChecked = Prefs.hideOnIme(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_HIDE_ON_IME, on) }
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

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportDiagnostics() }
        findViewById<Button>(R.id.btnClearDiag).setOnClickListener {
            Diagnostics.clear(this)
            Toast.makeText(this, "已清除診斷記錄", Toast.LENGTH_SHORT).show()
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
        window.decorView.postDelayed({ if (!isFinishing) refresh() }, 1200)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_EXPANDED, expanded.toIntArray())
        outState.putBoolean(STATE_SHOW_ALL, showAll)
        outState.putInt(STATE_WIZARD_STEP, wizardStep)
    }

    private fun setupCollapsibles(saved: Bundle?) {
        saved?.getIntArray(STATE_EXPANDED)?.let { expanded.addAll(it.toList()) }
        showAll = saved?.getBoolean(STATE_SHOW_ALL, false) ?: false
        sections.forEachIndexed { index, (hdrId, grpId, title) ->
            val hdr = findViewById<TextView>(hdrId)
            val grp = findViewById<View>(grpId)
            val sync = {
                val open = index in expanded
                grp.visibility = if (open) View.VISIBLE else View.GONE
                hdr.text = if (open) "$title　▴" else "$title　▾"
            }
            sync()
            hdr.setOnClickListener {
                if (!expanded.remove(index)) expanded.add(index)
                sync()
            }
        }
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

        statusAccess.text = if (access) "✅ 完成" else "清單裡打開「Sidepeek」"
        statusListener.text = when {
            linked -> "✅ 完成"
            !access -> "先做①"
            else -> "按重新連線"
        }
        statusPost.text = if (post) "✅ 完成" else "允許本 App 顯示通知"

        // LINE's own sound no longer needs muting: when Sidepeek hides LINE's notification, Android
        // stops that notification's sound/vibration too. The old "mute LINE" card stays hidden.
        statusLineMute.visibility = View.GONE
        findViewById<Button>(R.id.btnLineMute).visibility = View.GONE
        findViewById<Button>(R.id.btnLineMuteDone).visibility = View.GONE

        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        statusBattery.text = if (ignoring)
            "✅ 電池：不受限制"
        else
            "⚠️ 電池會被省電功能限制\n手機省電時可能把 App 關掉，就會漏掉訊息。"
        findViewById<Button>(R.id.btnBattery).visibility = vis(!ignoring)

        val neverSleepOk = Prefs.neverSleepDone(this) || ignoring
        statusNeverSleep.text = if (neverSleepOk)
            "✅ 背景執行：已設定好"
        else
            "⚠️ 三星手機：還沒加入「永不休眠的應用程式」\n設定 → 電池與裝置維護 → 電池 → 背景使用限制 → 永不休眠的應用程式 → 加入「Sidepeek」。不是三星可以略過。"
        findViewById<Button>(R.id.btnNeverSleep).visibility = vis(!neverSleepOk)
        findViewById<Button>(R.id.btnNeverSleepDone).visibility = vis(!neverSleepOk)

        val suggest = listOf(!ignoring, !neverSleepOk).count { it }

        val wizardActive = !Prefs.wizardDone(this)
        if (wizardActive) {
            advanceWizardIfNeeded(access, linked, post)
            bindWizardStep()
        } else wizardVideo.setVideo(0)

        statusSummary.text = when {
            wizardActive -> "第 ${wizardStep + 1}／$WIZARD_STEPS 步"
            !Prefs.enabled(this) -> "已關閉"
            suggest > 0 -> "運作中"
            else -> "一切正常"
        }

        // Wizard: only status + one-step card. Dash after complete (or showAll escape).
        wizardCard.visibility = vis(wizardActive)
        howtoCard.visibility = vis(!wizardActive)
        dashGroup.visibility = vis(!wizardActive || showAll)
        hdrFaq.visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.grpFaq).visibility =
            if ((!wizardActive || showAll) && sections.indexOfFirst { it.first == R.id.hdrFaq } in expanded) View.VISIBLE else View.GONE
        hdrHelp.visibility = vis(!wizardActive || showAll)
        findViewById<View>(R.id.grpHelp).visibility =
            if ((!wizardActive || showAll) && sections.indexOfFirst { it.first == R.id.hdrHelp } in expanded) View.VISIBLE else View.GONE
        cardSuggest.visibility = vis((!wizardActive || showAll) && suggest > 0)

        // Never dump long a11y guides onto the dash (they live inside wizard a11y steps).
        a11yGuideBox.visibility = View.GONE

        val a11y = PanelHost.a11yActive
        val a11yOn = PanelHost.a11yEnabledInSettings(this)
        statusA11y.text = when {
            a11y -> "✅ 側邊欄服務運作正常"
            a11yOn -> "⚠️ 側邊欄服務已打開，但還沒連上\n到無障礙設定，把「Sidepeek 側邊欄」關掉再打開一次。"
            else -> "⚠️ 還沒開側邊欄服務\n不開也能用，但通知欄會多出一直掛著的通知。開啟後就會消失。"
        }
        a11yBox.visibility = vis((!wizardActive || showAll) && Prefs.edgePanel(this) && !a11y)

        val usage = hasUsageAccess(this)
        statusUsage.text = when {
            a11y -> "✅ 判斷是否在桌面：由側邊欄服務處理，不需要另外允許"
            usage -> if (Prefs.homeOnly(this)) "✅ 讀取使用情況：已允許（Peek 條只在桌面出現）" else "✅ 讀取使用情況：已允許"
            Prefs.homeOnly(this) -> "⚠️ 還沒允許讀取使用情況\n所以 Peek 條現在會一直顯示，不會只在桌面出現。"
            else -> "讀取使用情況：未允許（只有「只在桌面顯示 Peek 條」才需要）"
        }

        val overlay = Settings.canDrawOverlays(this)
        statusOverlay.text = when {
            a11y -> "✅ 側邊欄服務已開啟（推薦，不需要備用方式）"
            !overlay -> "⚠️ 請先開側邊欄服務（推薦）。若開不成，才用下面的備用方式。"
            Prefs.edgePanel(this) && EdgePanelService.running -> "✅ 側邊欄運作中（備用方式）"
            Prefs.edgePanel(this) -> "⚠️ 側邊欄服務未開，目前靠備用方式"
            else -> "側邊欄：目前關閉"
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
                    if (ignoring) STEP_DONE else -1
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
        wizardProgress.text = "第 ${wizardStep + 1}／$WIZARD_STEPS 步"
        wizardSecondary.visibility = View.GONE
        wizardSkip.visibility = View.GONE
        wizardImage.visibility = View.GONE
        wizardImage.setImageDrawable(null)
        var video = 0   // screen-recording clip for steps done in system settings

        when (wizardStep) {
            STEP_LISTENER -> {
                wizardTitle.text = "允許讀取通知"
                if (!access) {
                    wizardHint.text = "點下面按鈕，在清單裡打開「Sidepeek」。回來後會自動進入下一步。"
                    wizardPrimary.text = "打開通知存取設定"
                    video = R.raw.guide_step1
                } else {
                    wizardHint.text = "已允許，但還沒連上。按重新連線；若還是不行，到清單把「Sidepeek」關掉再打開。"
                    wizardPrimary.text = "重新連線"
                }
            }
            STEP_POST -> {
                wizardTitle.text = "允許顯示通知"
                wizardHint.text = "讓「Sidepeek」可以發出整理後的通知。點下面允許即可。"
                wizardPrimary.text = "允許顯示通知"
            }
            STEP_EDGE -> {
                wizardTitle.text = "打開側邊欄"
                wizardHint.text = "螢幕邊緣會出現一條半透明的「Peek 條」，從 Peek 條往內滑就能看訊息（用法跟三星的側拉標籤類似）。接下來幾步會幫你把服務打開。"
                wizardPrimary.text = "打開側邊欄"
                wizardImage.visibility = View.VISIBLE
                wizardImage.setImageResource(R.drawable.img_step_sidebar)
            }
            STEP_TRY_A11Y -> {
                wizardTitle.text = "試開側邊欄服務"
                wizardHint.text = "點『已安裝的應用程式』→『Sidepeek 側邊欄』→打開。若跳出拒絕視窗，按關閉，再按下面下一步。"
                wizardPrimary.text = "打開無障礙設定"
                video = R.raw.guide_step4
                if (Prefs.a11yTryOpened(this)) {
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = "我已關閉拒絕視窗，下一步"
                }
            }
            STEP_RESTRICTED -> {
                wizardTitle.text = "允許受限設定"
                wizardHint.text = "現在右上角才會有 ⋮。點 ⋮ → 允許受限設定，再回來。"
                wizardPrimary.text = "打開 App 資訊"
                video = R.raw.guide_step5
                if (Prefs.a11yAppInfoOpened(this)) {
                    wizardSecondary.visibility = View.VISIBLE
                    wizardSecondary.text = "我已允許，下一步"
                }
            }
            STEP_REOPEN_A11Y -> {
                wizardTitle.text = "再開一次側邊欄服務"
                wizardHint.text = "回到無障礙 → 已安裝的應用程式 →『Sidepeek 側邊欄』→ 打開。打開後會自動進入下一步。"
                wizardPrimary.text = "打開無障礙設定"
                video = R.raw.guide_step6
            }
            STEP_BATTERY -> {
                wizardTitle.text = "停用電池最佳化"
                wizardHint.text = "省電功能可能把「Sidepeek」關掉而漏訊息。點下面允許「不受限制」。這步可略過。"
                wizardPrimary.text = "改成不受限制"
                wizardSkip.visibility = View.VISIBLE
                wizardSkip.text = "略過"
            }
            STEP_DONE -> {
                wizardTitle.text = "可以用了"
                wizardHint.text = "必要設定都好了。接下來只用主開關就夠；細節收在「進階」。"
                wizardPrimary.text = "完成，開始使用"
                wizardImage.visibility = View.VISIBLE
                wizardImage.setImageResource(R.drawable.img_steps_overview)
            }
        }
        wizardVideo.setVideo(video)
    }

    private fun onWizardPrimary() {
        when (wizardStep) {
            STEP_LISTENER -> {
                if (!isListenerEnabled(this)) {
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
            STEP_BATTERY -> requestBatteryExemption()
            STEP_DONE -> finishWizard()
        }
    }

    private fun onWizardSecondary() {
        when (wizardStep) {
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
        }
    }

    private fun finishWizard() {
        Prefs.set(this, Prefs.KEY_WIZARD_DONE_V1, true)
        if (!Prefs.edgePanel(this)) {
            Prefs.set(this, Prefs.KEY_EDGE_PANEL, true)
            swEdge.isChecked = true
        }
        if (Prefs.edgePanel(this) && !PanelHost.running()) PanelHost.start(this)
        Toast.makeText(this, "可以用了", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun rebindListener() {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, LineListenerService::class.java)
            )
            Diagnostics.note(this, "LISTENER requestRebind from UI")
            Toast.makeText(this, "正在重新連線…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Diagnostics.fail(this, "LISTENER UI requestRebind", e)
            Toast.makeText(this, "重新連線沒成功。請在接下來的清單裡，把「Sidepeek」關掉再打開", Toast.LENGTH_LONG).show()
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        statusListener.postDelayed({ refresh() }, 800)
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
            Toast.makeText(this, "側邊欄要先開啟「側邊欄服務」。請先試開無障礙；若被拒絕再允許受限設定", Toast.LENGTH_LONG).show()
            openA11yEnablePath(); return
        }
        if (!Prefs.edgePanel(this)) { swEdge.isChecked = true }
        PanelHost.open(this)
    }

    /** Open App Info so user can allow Restricted Settings — only after trying a11y (Samsung). */
    private fun openA11yAppInfo() {
        Prefs.set(this, Prefs.KEY_A11Y_APPINFO_OPENED_V1, true)
        val ok = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        if (!ok) safeStart(Intent(Settings.ACTION_SETTINGS))
        Toast.makeText(this, "請點右上角 ⋮ → 允許受限設定。做完回來再打開側邊欄服務", Toast.LENGTH_LONG).show()
    }

    /** Open Accessibility settings to toggle the sidebar service. */
    private fun openA11ySettings(markTry: Boolean = true) {
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
        bindSeek(R.id.sbPanelW, R.id.lblPanelW, Prefs.KEY_PANEL_WIDTH, Prefs.PW_MIN, Prefs.PW_MAX, Prefs.panelWidth(this), livePreview = true) { "寬度：螢幕的 $it%" }
        bindSeek(R.id.sbPanelH, R.id.lblPanelH, Prefs.KEY_PANEL_HEIGHT, Prefs.PH_MIN, Prefs.PH_MAX, Prefs.panelHeight(this), livePreview = true) { "高度：螢幕的 $it%" }
        bindSeek(R.id.sbPanelML, R.id.lblPanelML, Prefs.KEY_PANEL_MAXLINES, Prefs.ML_MIN, Prefs.ML_MAX, Prefs.panelMaxLines(this), livePreview = true) { "每則訊息最多顯示：$it 行" }
        bindSeek(R.id.sbPanelR, R.id.lblPanelR, Prefs.KEY_PANEL_RADIUS, 0, Prefs.PR_MAX, Prefs.panelRadius(this), livePreview = true) { "圓角：$it dp" }

        val rgPos = findViewById<RadioGroup>(R.id.rgPanelPos)
        rgPos.check(when (Prefs.panelPosition(this)) {
            Prefs.POS_TOP -> R.id.rbPosTop; Prefs.POS_BOTTOM -> R.id.rbPosBottom; else -> R.id.rbPosCenter
        })
        rgPos.setOnCheckedChangeListener { _, id ->
            Prefs.setString(this, Prefs.KEY_PANEL_POS, when (id) {
                R.id.rbPosTop -> Prefs.POS_TOP; R.id.rbPosBottom -> Prefs.POS_BOTTOM; else -> Prefs.POS_CENTER
            })
            previewPanel()
        }
        val rgText = findViewById<RadioGroup>(R.id.rgPanelText)
        rgText.check(when (Prefs.panelTextSize(this)) { 0 -> R.id.rbTextS; 2 -> R.id.rbTextL; else -> R.id.rbTextM })
        rgText.setOnCheckedChangeListener { _, id ->
            Prefs.setInt(this, Prefs.KEY_PANEL_TEXT, when (id) { R.id.rbTextS -> 0; R.id.rbTextL -> 2; else -> 1 })
            previewPanel()
        }
        findViewById<Button>(R.id.btnPanelPreview).setOnClickListener { openPanelNow() }
        findViewById<TextView>(R.id.linkPrivacy).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { startActivity(Intent(this@MainActivity, PrivacyActivity::class.java)) }
        }
        findViewById<SwitchCompat>(R.id.swPanelScrim).apply {
            isChecked = Prefs.panelScrim(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.set(this@MainActivity, Prefs.KEY_PANEL_SCRIM, on); previewPanel() }
        }
        findViewById<Button>(R.id.btnPanelReset).setOnClickListener {
            Prefs.sp(this).edit().apply { Prefs.PANEL_KEYS.forEach { remove(it) } }.apply()
            recreate()
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
            text = "＋ 選擇其他 App"
            setOnClickListener { openAppPicker() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpx(6f) })
    }

    /** Searchable multi-select list of every launchable app on the phone. */
    private fun openAppPicker() {
        Toast.makeText(this, "讀取 App 清單中…", Toast.LENGTH_SHORT).show()
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
            hint = "搜尋 App"
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
            .setTitle("要收哪些 App 的訊息")
            .setView(content)
            .setPositiveButton("完成") { _, _ ->
                Sources.setSelected(this, chosen)
                setupSourceSwitches()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupHandleSettings() {
        val rg = findViewById<RadioGroup>(R.id.rgSide)
        rg.check(if (Prefs.handleSide(this) == Prefs.SIDE_LEFT) R.id.rbLeft else R.id.rbRight)
        rg.setOnCheckedChangeListener { _, id ->
            Prefs.setString(this, Prefs.KEY_HANDLE_SIDE, if (id == R.id.rbLeft) Prefs.SIDE_LEFT else Prefs.SIDE_RIGHT)
        }
        bindSeek(R.id.sbInset, R.id.lblInset, Prefs.KEY_HANDLE_INSET, 0, Prefs.INSET_MAX, Prefs.handleInset(this)) { "距離邊緣：$it dp" }
        bindSeek(R.id.sbWidth, R.id.lblWidth, Prefs.KEY_HANDLE_WIDTH, Prefs.WIDTH_MIN, Prefs.WIDTH_MAX, Prefs.handleWidth(this)) { "Peek 條寬度：$it dp" }
        bindSeek(R.id.sbHeight, R.id.lblHeight, Prefs.KEY_HANDLE_HEIGHT, Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX, Prefs.handleHeight(this)) { "Peek 條高度：$it dp" }
        bindSeek(R.id.sbOpacity, R.id.lblOpacity, Prefs.KEY_HANDLE_OPACITY, Prefs.OPACITY_MIN, Prefs.OPACITY_MAX, Prefs.handleOpacity(this)) { "不透明度：$it%" }

        findViewById<Button>(R.id.btnResetHandle).setOnClickListener {
            Prefs.writeHandleDefaults(this)
            recreate()
        }
        findViewById<Button>(R.id.btnAddTile).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                val sbm = getSystemService(android.app.StatusBarManager::class.java)
                sbm.requestAddTileService(
                    android.content.ComponentName(this, PanelTileService::class.java),
                    "開啟訊息側邊欄",
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_msg),
                    mainExecutor
                ) { result ->
                    val msg = when (result) {
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已加到快速設定"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "快速設定裡已經有這個按鈕了"
                        else -> "沒有加入。也可以下拉快速設定 → 編輯（鉛筆或 ⋮），手動把它拖進去"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "請下拉快速設定 → 編輯，把「開啟訊息側邊欄」拖進去", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val PREVIEW_HOLD_MS = 1500L
    private fun previewPanel(holdMs: Long = PREVIEW_HOLD_MS) { PanelHost.preview(this, holdMs) }

    private fun bindSeek(seekId: Int, labelId: Int, key: String, min: Int, max: Int, value: Int,
                         livePreview: Boolean = false, label: (Int) -> String) {
        val sb = findViewById<SeekBar>(seekId)
        val lbl = findViewById<TextView>(labelId)
        sb.max = max - min
        sb.progress = value - min
        lbl.text = label(value)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                lbl.text = label(p + min)
                if (fromUser) Prefs.setInt(this@MainActivity, key, p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar) { if (livePreview) previewPanel(-1) }
            override fun onStopTrackingTouch(s: SeekBar) { if (livePreview) previewPanel() }
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
                Toast.makeText(this, "把這個類別改成靜音（不響、不跳出）；不要整個關掉", Toast.LENGTH_LONG).show()
                return
            }
        }
        val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, LineListenerService.LINE_PKG)
        if (safeStart(app)) {
            Toast.makeText(this, "把「聲音和震動」關掉；「允許通知」保持開啟", Toast.LENGTH_LONG).show()
            return
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${LineListenerService.LINE_PKG}"))
        if (safeStart(details)) {
            Toast.makeText(this, "把「聲音和震動」關掉；「允許通知」保持開啟", Toast.LENGTH_LONG).show()
            return
        }
        packageManager.getLaunchIntentForPackage(LineListenerService.LINE_PKG)?.let {
            if (safeStart(it)) return
        }
        Toast.makeText(this, "請手動打開：設定 → 應用程式 → LINE → 通知", Toast.LENGTH_LONG).show()
    }

    private fun openNeverSleepSettings() {
        val trials = listOf(
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent().setClassName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        for (i in trials) {
            if (safeStart(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        }
        Toast.makeText(this, "請手動打開：設定 → 電池 → 永不休眠的應用程式", Toast.LENGTH_LONG).show()
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "電池已經是不受限制了", Toast.LENGTH_SHORT).show()
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
                putExtra(Intent.EXTRA_SUBJECT, "Sidepeek 診斷記錄")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "分享診斷記錄"))
        } catch (e: Exception) {
            Toast.makeText(this, "匯出失敗：$e", Toast.LENGTH_LONG).show()
        }
    }

    private fun safeStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val STATE_EXPANDED = "main.expanded"
        private const val STATE_SHOW_ALL = "main.showAll"
        private const val STATE_WIZARD_STEP = "main.wizardStep"

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

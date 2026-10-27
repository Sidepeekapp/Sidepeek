package app.sidepeek

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** 隱私權政策（opened from the link at the bottom of the main screen). */
class PrivacyActivity : AppCompatActivity() {

    companion object {
        const val UPDATED = "最後更新：2026年10月27日"
        /** Public contact for privacy questions and feedback. */
        const val CONTACT = Feedback.CONTACT
    }

    private data class Section(val title: String, val body: List<String>)

    private val intro = listOf(
        "Sidepeek（以下稱「本 App」）是個人開發的非官方 Android App，與 LINE、LY Corporation 無關。本 App 讀取手機上的通知，把 LINE 等聊天 App 的訊息整理成通知和側邊欄。本政策說明本 App 會讀取哪些資料、怎麼使用、存在哪裡。",
        "重點：所有處理都在你的手機上完成。本 App 沒有伺服器、沒有帳號，不會把你的訊息傳給開發者或任何第三方。",
    )

    private val sections = listOf(
        Section("一、讀取與使用的資料", listOf(
            "【通知內容】",
            "• 透過 Android 的「通知存取權」，讀取你選擇的 App（預設為 LINE、Instagram、Threads、Messenger、Teams）的通知，包括聊天室名稱、傳送者名稱、訊息文字、頭像、通知附帶的圖片，以及通知上的回覆／已讀按鈕。",
            "• 用途：重新發出整理後的通知、顯示在側邊欄、把訊息裡的網址變成按鈕。",
            "• 沒有選的 App，本 App 不會讀取或儲存它們的通知內容。",
            "【存在手機裡的資料】",
            "• 最近最多 200 則訊息（文字、聊天室與傳送者名稱、時間、網址），以及頭像和圖片縮圖。",
            "• 診斷記錄：為了排查問題，會在手機裡保留近期收到的通知完整內容（約 600 KB，超過時自動刪除較舊的部分）。只有你按「匯出診斷記錄」並自己選擇分享對象時，才會離開手機。",
            "• 你的設定，例如 Peek 條位置、側邊欄外觀、要收哪些 App。",
            "【你在通知上的操作】",
            "• 在通知上回覆或按已讀時，本 App 是透過原本那個 App（例如 LINE）提供的按鈕送出，內容直接交給該 App，本 App 不另外保存回覆內容。",
        )),
        Section("二、網路連線", listOf(
            "• 本 App 只在下載 LINE 貼圖時連網：依通知裡的貼圖編號，向 LINE 公開的貼圖伺服器（stickershop.line-scdn.net 等 LINE 自己的網域）下載貼圖圖片；不是 LINE 網域的網址一律不連，也不會因為訊息裡出現網址就自動連線。不會傳送你的訊息內容或帳號資料。LINE 的伺服器會看到你的 IP 位址，並依 LINE 的隱私權政策處理。",
            "• 你點側邊欄裡的網址按鈕時，會用手機上的瀏覽器開啟該網址。",
        )),
        Section("三、不收集的資料", listOf(
            "• 不需要註冊，不收集帳號、電話號碼、Email、通訊錄、定位（GPS）。",
            "• 沒有廣告，也沒有分析或追蹤工具。",
            "• 不讀取螢幕上的文字（見下方「協助工具」）。",
            "• 不把任何資料上傳到開發者或第三方的伺服器。",
        )),
        Section("四、App 所需權限", listOf(
            "• 通知存取權：讀取你選擇的 App 的通知，是本 App 的核心功能。",
            "• 顯示通知：發出整理後的通知。",
            "• 協助工具（無障礙）服務「Sidepeek 側邊欄」：在螢幕邊緣顯示 Peek 條和側邊欄，並判斷目前是哪個 App 在前景、是否在桌面、鍵盤有沒有打開，以及在側邊欄打開時接收返回鍵來關閉它。開啟前 App 會先顯示說明並請你同意。不會讀取、儲存或傳送畫面上的文字或內容，也不會代替你操作。",
            "• 顯示在其他 App 上層：沒有開協助工具時的備用方式，用來顯示 Peek 條和側邊欄。",
            "• 使用情況存取權（選用）：判斷目前是哪個 App 在前景，決定 Peek 條要不要顯示。",
            "• 查看已安裝的 App：讓你挑選要收進側邊欄的 App，清單只在手機上使用。",
            "• 停用電池最佳化、開機後自動啟動、前景服務：避免被系統關掉而漏掉訊息。",
            "• 網路：下載 LINE 貼圖圖片。",
            "• 震動：通知提醒。",
        )),
        Section("五、資料保存與刪除", listOf(
            "• 側邊欄裡的訊息可以單則滑掉，或按「清除」全部刪除（幾秒內可以復原）；清除後也會一併刪除頭像、圖片和診斷記錄。",
            "• 「遇到問題」裡可以「清除診斷記錄」。",
            "• 關閉通知存取權或協助工具後，本 App 就不再讀取通知。",
            "• 解除安裝本 App 會刪除所有存在手機裡的資料。",
            "• 本 App 沒有帳號，開發者手上沒有你的資料，因此不需要另外申請刪除。",
        )),
        Section("六、資料安全", listOf(
            "• 資料存在 Android 為每個 App 隔離的私有空間，其他 App 無法讀取。",
            "• 下載貼圖使用 HTTPS 加密連線。",
            "• 匯出的診斷記錄含有訊息內容，分享前請自行確認對象。",
        )),
        Section("七、兒童隱私", listOf(
            "• 本 App 不是為 13 歲以下兒童設計，也不會刻意收集兒童的資料。",
        )),
        Section("八、政策更新", listOf(
            "• 本政策內容有變動時，會更新本頁並修改上方的「最後更新」日期。",
        )),
        Section("九、聯絡我們", listOf(
            "對本政策或資料處理有任何問題，請來信：",
        )),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "隱私權政策"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val dp = resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val accent = ContextCompat.getColor(this, R.color.section_text)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(16f), px(20f), px(40f))
        }
        fun add(tv: TextView, top: Float = 0f) =
            col.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(top) })
        fun body(text: String) = TextView(this).apply {
            textSize = 15f; setLineSpacing(px(4f).toFloat(), 1f); alpha = 0.9f
            // Bullets: wrapped lines line up with the text, not with the bullet.
            this.text = if (text.startsWith("• ")) android.text.SpannableString(text).apply {
                val indent = paint.measureText("• ").toInt()
                setSpan(android.text.style.LeadingMarginSpan.Standard(0, indent), 0, length, 0)
            } else text
        }

        add(TextView(this).apply { text = "Sidepeek 隱私權政策"; textSize = 24f; setTypeface(typeface, Typeface.BOLD) })
        add(TextView(this).apply { text = UPDATED; textSize = 13f; alpha = 0.7f }, 4f)
        intro.forEach { add(body(it), 14f) }

        for (s in sections) {
            add(TextView(this).apply {
                text = s.title; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(accent)
            }, 26f)
            for (line in s.body) {
                if (line.startsWith("【")) {
                    add(TextView(this).apply { text = line.trim('【', '】'); textSize = 15.5f; setTypeface(typeface, Typeface.BOLD) }, 12f)
                } else {
                    add(body(line), 6f)
                }
            }
        }

        // Only the address itself is a link (auto-linking also swallowed the Chinese text before it).
        add(TextView(this).apply {
            text = CONTACT; textSize = 16f; setTextColor(accent)
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            minHeight = px(44f); gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { emailUs() }
        }, 2f)

        setContentView(ScrollView(this).apply { addView(col); padForSystemBars() })
        addStatusBarScrim()
    }

    private fun emailUs() = Feedback.send(this, "相關問題", withInfo = false)

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

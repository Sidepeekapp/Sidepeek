package app.sidepeek

import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Prominent disclosure shown before the user is sent to turn on the accessibility service
 * (Google Play AccessibilityService API policy): what it accesses, what it is used for,
 * what it never does, and an explicit 同意 / 不同意 choice. Consent is remembered.
 */
object A11yDisclosure {

    private val sections = listOf(
        "Sidepeek 會用到 Android 的「協助工具（無障礙）」功能" to listOf(
            "這項服務叫「Sidepeek 側邊欄」，只用在側邊欄，不開也能使用通知整理。",
        ),
        "會讀取的資料" to listOf(
            "目前在前景的 App 是哪一個（App 的套件名稱），以及是否在桌面。",
            "畫面上有沒有鍵盤視窗。",
            "側邊欄打開時，你按下的「返回鍵」。",
        ),
        "用來做什麼" to listOf(
            "在螢幕邊緣顯示 Peek 條，往內滑時打開側邊欄（不需要常駐通知）。",
            "決定 Peek 條要不要顯示：例如「只在桌面顯示」、打字時暫時隱藏。",
            "側邊欄打開時按返回鍵可以關掉它。",
        ),
        "不會做的事" to listOf(
            "不讀取、不儲存、不傳送畫面上的任何文字或內容。",
            "不代替你點擊或操作任何 App。",
            "以上資料只在手機上即時使用，不記錄、不上傳，也不分享給任何人。",
        ),
        "隨時可以關閉" to listOf(
            "到「設定 → 協助工具 → 已安裝的應用程式 → Sidepeek 側邊欄」關掉即可。",
        ),
    )

    private fun body(act: Activity): CharSequence {
        val sb = SpannableStringBuilder()
        val indent = (16 * act.resources.displayMetrics.density).toInt()
        sections.forEachIndexed { i, (title, lines) ->
            if (i > 0) sb.append("\n\n")
            val s = sb.length; sb.append(title)
            sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            for (l in lines) {
                sb.append("\n")
                val ls = sb.length; sb.append("• ").append(l)
                sb.setSpan(LeadingMarginSpan.Standard(0, indent), ls, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    /** Runs [onAccept] right away if the user already agreed; otherwise asks first. */
    fun ensure(act: Activity, onAccept: () -> Unit) {
        if (Prefs.a11yConsent(act)) { onAccept(); return }
        val d = act.resources.displayMetrics.density
        val text = TextView(act).apply {
            text = body(act); textSize = 15f
            setLineSpacing(4 * d, 1f)
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), (8 * d).toInt())
        }
        MaterialAlertDialogBuilder(act)
            .setTitle("開啟側邊欄前，請先看這裡")
            .setView(ScrollView(act).apply { addView(text) })
            .setCancelable(false)
            .setPositiveButton("同意並繼續") { _, _ ->
                Prefs.set(act, Prefs.KEY_A11Y_CONSENT_V1, true)
                onAccept()
            }
            .setNegativeButton("不同意") { _, _ ->
                Toast.makeText(act, "沒關係，通知整理照常可用。側邊欄可以改用「顯示在其他 App 上層」的備用方式。", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}

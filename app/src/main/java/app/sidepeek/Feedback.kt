package app.sidepeek

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/** "寄信給開發者": opens the mail app with the address, a subject and basic phone/app info filled in. */
object Feedback {
    const val CONTACT = "sidepeek.app@gmail.com"

    private fun version(act: Activity): String =
        try { act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

    /** Phone / app facts that help with bug reports. No messages or personal data. */
    fun deviceInfo(act: Activity): String {
        val listener = try { isListenerEnabled(act) } catch (_: Exception) { false }
        val a11y = PanelHost.a11yActive || PanelHost.a11yEnabledInSettings(act)
        return listOf(
            "手機：${Build.MANUFACTURER} ${Build.MODEL}",
            "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            "Sidepeek ${version(act)}",
            "通知存取：${if (listener) "已允許" else "未允許"}",
            "側邊欄服務：${if (a11y) "已開啟" else "未開啟"}",
        ).joinToString("\n")
    }

    /**
     * @param topic subject after the "[Sidepeek x.y]" prefix
     * @param withInfo add the phone/app info block to the body
     */
    fun send(act: Activity, topic: String, withInfo: Boolean = true) {
        val subject = "[Sidepeek ${version(act)}] $topic"
        val body = if (withInfo)
            "請在這裡寫下問題或建議：\n\n\n\n——\n以下資訊有助於排查問題，不想提供可以刪掉：\n${deviceInfo(act)}\n"
        else ""
        // Address/subject/body in the mailto: URI (some mail apps ignore extras), extras too.
        var uri = "mailto:$CONTACT?subject=" + Uri.encode(subject)
        if (body.isNotEmpty()) uri += "&body=" + Uri.encode(body)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body.isNotEmpty()) i.putExtra(Intent.EXTRA_TEXT, body)
        try { act.startActivity(i) } catch (_: Exception) {
            Toast.makeText(act, "找不到可以寄信的 App，請寄到 $CONTACT", Toast.LENGTH_LONG).show()
        }
    }
}

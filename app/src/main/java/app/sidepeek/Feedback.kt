package app.sidepeek

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/** "寄信給開發者": opens the mail app with the address, a subject and basic phone/app info filled in. */
object Feedback {
    const val CONTACT = "sidepeek.app@gmail.com"

    /** "1.0 (3)": the shown version stays 1.0, the build number tells releases apart. */
    private fun version(@Suppress("UNUSED_PARAMETER") act: Activity): String =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** Phone / app facts that help with bug reports. No messages or personal data. */
    fun deviceInfo(act: Activity): String {
        val listener = try { isListenerEnabled(act) } catch (_: Exception) { false }
        val a11y = PanelHost.a11yActive || PanelHost.a11yEnabledInSettings(act)
        return listOf(
            act.getString(R.string.fb_phone, "${Build.MANUFACTURER} ${Build.MODEL}"),
            "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            "Sidepeek ${version(act)}",
            act.getString(R.string.fb_access, act.getString(if (listener) R.string.fb_allowed else R.string.fb_not_allowed)),
            act.getString(R.string.fb_a11y, act.getString(if (a11y) R.string.fb_on else R.string.fb_off)),
        ).joinToString("\n")
    }

    /**
     * @param topic subject after the "[Sidepeek x.y]" prefix
     * @param withInfo add the phone/app info block to the body
     */
    fun send(act: Activity, topic: String, withInfo: Boolean = true) {
        val subject = "[Sidepeek ${version(act)}] $topic"
        val body = if (withInfo)
            act.getString(R.string.fb_body, deviceInfo(act))
        else ""
        // Address/subject/body in the mailto: URI (some mail apps ignore extras), extras too.
        var uri = "mailto:$CONTACT?subject=" + Uri.encode(subject)
        if (body.isNotEmpty()) uri += "&body=" + Uri.encode(body)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body.isNotEmpty()) i.putExtra(Intent.EXTRA_TEXT, body)
        try { act.startActivity(i) } catch (_: Exception) {
            Toast.makeText(act, act.getString(R.string.fb_no_mail, CONTACT), Toast.LENGTH_LONG).show()
        }
    }
}

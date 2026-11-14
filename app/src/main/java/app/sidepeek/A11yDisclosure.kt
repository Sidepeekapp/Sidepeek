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
        R.string.a11y_d_title to listOf(
            R.string.a11y_d_intro,
        ),
        R.string.a11y_d_reads to listOf(
            R.string.a11y_d_read1,
            R.string.a11y_d_read2,
            R.string.a11y_d_read3,
        ),
        R.string.a11y_d_uses to listOf(
            R.string.a11y_d_use1,
            R.string.a11y_d_use2,
            R.string.a11y_d_use3,
            R.string.a11y_d_use4,
        ),
        R.string.a11y_d_never to listOf(
            R.string.a11y_d_never1,
            R.string.a11y_d_never2,
            R.string.a11y_d_never3,
        ),
        R.string.a11y_d_off to listOf(
            R.string.a11y_d_off1,
        ),
    )

    private fun body(act: Activity, ids: List<Pair<Int, List<Int>>> = this.sections): CharSequence {
        val sections = ids.map { (t, l) -> act.getString(t) to l.map { act.getString(it) } }
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
            .setTitle(R.string.a11y_d_dialog)
            .setView(ScrollView(act).apply { addView(text) })
            .setCancelable(false)
            .setPositiveButton(R.string.agree_continue) { _, _ ->
                Prefs.set(act, Prefs.KEY_A11Y_CONSENT_V1, true)
                onAccept()
            }
            .setNegativeButton(R.string.disagree) { _, _ ->
                Toast.makeText(act, act.getString(R.string.a11y_d_declined), Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private val smartSections = listOf(
        R.string.smart_d_title to listOf(
            R.string.smart_d_intro,
        ),
        R.string.a11y_d_reads to listOf(
            R.string.smart_d_read1,
            R.string.smart_d_read2,
        ),
        R.string.a11y_d_uses to listOf(
            R.string.smart_d_use1,
            R.string.smart_d_use2,
        ),
        R.string.a11y_d_never to listOf(
            R.string.smart_d_never1,
            R.string.smart_d_never2,
        ),
        R.string.a11y_d_off to listOf(
            R.string.smart_d_off1,
        ),
    )

    /** Consent for 進階清理, asked every time it is switched on. [onResult] gets true if the user agreed. */
    fun smartClear(act: Activity, onResult: (Boolean) -> Unit) {
        val d = act.resources.displayMetrics.density
        val text = TextView(act).apply {
            text = body(act, smartSections); textSize = 15f
            setLineSpacing(4 * d, 1f)
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), (8 * d).toInt())
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.smart_d_dialog)
            .setView(ScrollView(act).apply { addView(text) })
            .setCancelable(false)
            .setPositiveButton(R.string.agree_enable) { _, _ ->
                Prefs.set(act, Prefs.KEY_SMART_CLEAR_CONSENT_V1, true)
                onResult(true)
            }
            .setNegativeButton(R.string.disagree) { _, _ -> onResult(false) }
            .show()
    }
}

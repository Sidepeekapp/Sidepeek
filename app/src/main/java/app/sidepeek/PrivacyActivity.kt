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
        /** Public contact for privacy questions and feedback. */
        const val CONTACT = Feedback.CONTACT
    }

    private val sectionIds = intArrayOf(
        R.array.privacy_section_1, R.array.privacy_section_2, R.array.privacy_section_3,
        R.array.privacy_section_4, R.array.privacy_section_5, R.array.privacy_section_6,
        R.array.privacy_section_7, R.array.privacy_section_8, R.array.privacy_section_9,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.privacy_title)
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

        add(TextView(this).apply { text = getString(R.string.privacy_heading); textSize = 24f; setTypeface(typeface, Typeface.BOLD) })
        add(TextView(this).apply { text = getString(R.string.privacy_updated); textSize = 13f; alpha = 0.7f }, 4f)
        resources.getStringArray(R.array.privacy_intro).forEach { add(body(it), 14f) }

        for (id in sectionIds) {
            val all = resources.getStringArray(id)
            add(TextView(this).apply {
                text = all[0]; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(accent)
            }, 26f)
            for (line in all.drop(1)) {
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

    private fun emailUs() = Feedback.send(this, getString(R.string.privacy_topic), withInfo = false)

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

package app.sidepeek

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 設定 → 通知：外觀 → 設定群組頭貼: the LINE groups Sidepeek has messages from, each with the
 * picture its notification shows. Tap one to choose a picture or go back to the default.
 */
class GroupPicsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.group_pics_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(12f), px(16f), px(40f))
        }
        setContentView(ScrollView(this).apply { addView(list); padForSystemBars() })
        addStatusBarScrim()
    }

    override fun onResume() {
        super.onResume()
        render()   // also after GroupPicActivity changed a picture
    }

    private fun render() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = getString(R.string.group_pics_intro)
            textSize = 14f; alpha = 0.75f; setLineSpacing(px(3f).toFloat(), 1f)
        })
        val groups = MessageStore.all(this).filter { PanelRows.isLineGroup(it) }
            .sortedByDescending { it.time }.distinctBy { it.chatKey }
        if (groups.isEmpty()) {
            list.addView(TextView(this).apply {
                text = getString(R.string.group_pics_empty)
                textSize = 15f; setPadding(0, px(24f), 0, 0)
            })
        }
        for (m in groups) list.addView(row(m.chatKey, m.chatTitle))
        val anyCustom = groups.any { MessageStore.customGroupPicPath(this, it.chatKey) != null } ||
            (java.io.File(filesDir, "groupav").list()?.isNotEmpty() == true)
        if (anyCustom) list.addView(Button(this).apply {
            text = getString(R.string.group_pics_reset_all)
            isAllCaps = false
            setOnClickListener { confirmResetAll() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(20f)
        })
    }

    private fun row(chatKey: String, title: String): View {
        val custom = MessageStore.customGroupPicPath(this, chatKey) != null
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(64f)
            setPadding(0, px(8f), 0, px(8f))
            background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).run {
                val d = getDrawable(0); recycle(); d
            }
            isClickable = true
            contentDescription = "$title，" + getString(if (custom) R.string.custom_set else R.string.default_)
            setOnClickListener {
                startActivity(Intent(this@GroupPicsActivity, GroupPicActivity::class.java)
                    .putExtra(GroupPicActivity.EXTRA_CHAT_KEY, chatKey)
                    .putExtra(GroupPicActivity.EXTRA_TITLE, title))
            }
            addView(ImageView(this@GroupPicsActivity).apply {
                setImageBitmap(Notifier.groupAvatar(this@GroupPicsActivity, chatKey, title, px(48f)))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(px(48f), px(48f)).apply { rightMargin = px(14f) })
            addView(LinearLayout(this@GroupPicsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@GroupPicsActivity).apply {
                    text = title.ifBlank { getString(R.string.group_unnamed) }; textSize = 16f
                    setTypeface(typeface, Typeface.BOLD); maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@GroupPicsActivity).apply {
                    text = getString(if (custom) R.string.custom_set else R.string.default_); textSize = 13f; alpha = 0.7f
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.group_pics_reset_q))
            .setPositiveButton(getString(R.string.restore_default)) { _, _ ->
                MessageStore.clearCustomGroupPics(this)
                try { Notifier.refreshAllSilently(this) } catch (_: Exception) {}
                Toast.makeText(this, getString(R.string.group_pics_reset_done), Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

package app.sidepeek

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat

/**
 * 設定頁頂端的示意動畫，風格跟功能介紹影片（tools/feature-video）一樣：左邊一支仿真的手機，
 * 右邊三個步驟輪流播放，結果用綠色膠囊點出來。`android:tag` 決定是哪一組：
 *
 * - `clear`：LINE 通知清理（點通知進聊天室／直接打開 LINE／進階清理）
 * - `open`：其他打開側邊欄的方法（快速設定／App 捷徑／桌面小工具）
 * - `when`：Peek 條顯示時機（只在桌面／打字時隱藏／鎖定畫面），照目前的開關演出效果
 *
 * 所有文字（步驟、膠囊、示範的聊天室、App 名稱）都從 strings.xml 讀，翻譯後自動換語言。
 * 手機畫面固定深色（跟影片一樣），步驟文字跟 App 的深淺色走。點步驟直接跳過去，點手機
 * 重播目前這一段。系統關閉動畫時停在每一段的結果畫面，不會自動輪播。
 */
class StepDemoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    private val dp = resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val sp = resources.displayMetrics.scaledDensity
    private fun d(v: Float) = v * dp
    private fun s(id: Int) = context.getString(id)

    private val kind: String = (tag as? String) ?: "clear"

    // ---- demo data -----------------------------------------------------------------------------
    private class Chat(val av: String, val name: String, val line: String, val time: String, val color: Int)
    private val chats = listOf(
        Chat(s(R.string.demo_chat1_av), s(R.string.demo_chat1_name), s(R.string.demo_chat1_line), s(R.string.demo_time1), 0xFF16A34A.toInt()),
        Chat(s(R.string.demo_chat2_av), s(R.string.demo_chat2_name), s(R.string.demo_chat2_line), s(R.string.demo_time2), 0xFF7C3AED.toInt()),
        Chat(s(R.string.demo_chat3_av), s(R.string.demo_chat3_name), s(R.string.demo_chat3_line), s(R.string.demo_time3), 0xFF2563EB.toInt()),
    )

    private val stepTitles: List<String> = when (kind) {
        "how" -> listOf(s(R.string.how_step1_title), s(R.string.how_step2_title), s(R.string.how_step3_title))
        "open" -> listOf(s(R.string.open_step1_title), s(R.string.open_step2_title), s(R.string.open_step3_title))
        "when" -> listOf(s(R.string.when_step1_title), s(R.string.when_step2_title), s(R.string.when_step3_title))
        else -> listOf(s(R.string.clear_step1_title), s(R.string.clear_step2_title), s(R.string.clear_step3_title))
    }
    private val stepSubs: List<String> = when (kind) {
        "how" -> listOf(s(R.string.how_step1_sub), s(R.string.how_step2_sub), s(R.string.how_step3_sub))
        "open" -> listOf(s(R.string.open_step1_sub), s(R.string.open_step2_sub), s(R.string.open_step3_sub))
        "when" -> listOf(s(R.string.when_step1_sub), s(R.string.when_step2_sub), s(R.string.when_step3_sub))
        else -> listOf(s(R.string.clear_step1_sub), s(R.string.clear_step2_sub), s(R.string.clear_step3_sub))
    }
    private val durations: LongArray = when (kind) {
        "how" -> longArrayOf(8000L, 5500L, 6500L)
        "open" -> longArrayOf(7000L, 8000L, 8500L)
        "when" -> longArrayOf(6500L, 6500L, 6500L)
        else -> longArrayOf(6500L, 8000L, 9400L)
    }
    private val pillAt: LongArray = when (kind) {
        "how" -> longArrayOf(5300, 2400, 2200)
        "open" -> longArrayOf(3400, 4300, 4200)
        "when" -> longArrayOf(2300, 2200, 2100)
        else -> longArrayOf(4200, 5100, 6000)
    }

    /** Whether step [i]'s switch is on (null = the step has no switch of its own). */
    private fun switchOn(i: Int): Boolean? = when (kind) {
        "clear" -> when (i) { 1 -> Prefs.clearOnLineOpen(context); 2 -> Prefs.smartClear(context); else -> null }
        "when" -> when (i) { 0 -> Prefs.homeOnly(context); 1 -> Prefs.hideOnIme(context); else -> Prefs.showOnLock(context) }
        else -> null
    }

    private fun pill(i: Int): String = when (kind) {
        "how" -> s(intArrayOf(R.string.how_pill1, R.string.how_pill2, R.string.how_pill3)[i])
        "open" -> s(intArrayOf(R.string.open_step1_pill, R.string.open_step2_pill, R.string.open_step3_pill)[i])
        "when" -> {
            val on = switchOn(i) == true
            s(if (on) intArrayOf(R.string.when_step1_pill_on, R.string.when_step2_pill_on, R.string.when_step3_pill_on)[i]
              else intArrayOf(R.string.when_step1_pill_off, R.string.when_step2_pill_off, R.string.when_step3_pill_off)[i])
        }
        else -> s(intArrayOf(R.string.clear_step1_pill, R.string.clear_step2_pill, R.string.clear_step3_pill)[i])
    }

    private fun title(i: Int): String {
        val on = switchOn(i) ?: return stepTitles[i]
        // Only an off switch is marked; the pill already says what the current setting does.
        return if (on) stepTitles[i] else stepTitles[i] + s(R.string.demo_step_off)
    }

    // ---- state ---------------------------------------------------------------------------------
    private var step = 0
    private var start = 0L
    private val animating get() = ValueAnimator.areAnimatorsEnabled()

    // ---- paints & geometry ---------------------------------------------------------------------
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val r = RectF()
    private val path = Path()
    private val phone = RectF()
    private val scr = RectF()
    private val stepRows = Array(3) { RectF() }

    private val night get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val textMain get() = if (night) 0xFFF4F5F8.toInt() else 0xFF1C1C1E.toInt()
    private val textDim get() = if (night) 0xFFA3A8B8.toInt() else 0xFF6B7280.toInt()
    private val green get() = if (night) 0xFF22C55E.toInt() else ContextCompat.getColor(context, R.color.section_text)

    // Phone screen colours (always dark, as in the videos).
    private val ink = 0xFFF4F5F8.toInt()
    private val dim = 0xFFA3A8B8.toInt()
    private val cardBg = 0xF5262838.toInt()
    private val brand = 0xFF16A34A.toInt()

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = stepTitles.indices.joinToString("\n") { "${it + 1}. ${stepTitles[it]}：${stepSubs[it]}" }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Prefs.sp(context).registerOnSharedPreferenceChangeListener(this)
        start = SystemClock.uptimeMillis()
    }

    override fun onDetachedFromWindow() {
        Prefs.sp(context).unregisterOnSharedPreferenceChangeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) { step = 0; start = SystemClock.uptimeMillis(); invalidate() }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        val mine = when (kind) {
            "clear" -> listOf(Prefs.KEY_CLEAR_ON_LINE_OPEN, Prefs.KEY_SMART_CLEAR, Prefs.KEY_SMART_CLEAR_CONSENT_V1)
            "when" -> listOf(Prefs.KEY_HOME_ONLY, Prefs.KEY_HIDE_ON_IME, Prefs.KEY_SHOW_ON_LOCK, Prefs.KEY_HANDLE_SIDE)
            else -> listOf(Prefs.KEY_HANDLE_SIDE)
        }
        if (key !in mine || !isShown) return
        // A switch changed: play its step from the start so its effect shows right away.
        val i = when (key) {
            Prefs.KEY_CLEAR_ON_LINE_OPEN -> 1; Prefs.KEY_SMART_CLEAR, Prefs.KEY_SMART_CLEAR_CONSENT_V1 -> 2
            Prefs.KEY_HOME_ONLY -> 0; Prefs.KEY_HIDE_ON_IME -> 1; Prefs.KEY_SHOW_ON_LOCK -> 2
            else -> step
        }
        play(i)
    }

    private fun play(i: Int) { step = i; start = SystemClock.uptimeMillis(); invalidate() }

    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragged = false

    /** A tap picks a step (or replays); any drag is left to the page so it scrolls normally. */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragged = false }
            MotionEvent.ACTION_MOVE -> if (!dragged && (Math.abs(e.x - downX) > slop || Math.abs(e.y - downY) > slop)) {
                dragged = true
                parent?.requestDisallowInterceptTouchEvent(false)
                return false
            }
            MotionEvent.ACTION_UP -> if (!dragged) {
                val hit = stepRows.indexOfFirst { it.contains(e.x, e.y) }
                if (hit >= 0) play(hit) else if (phone.contains(e.x, e.y)) play(step)
                performClick()
            }
        }
        return !dragged
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    // ---- layout --------------------------------------------------------------------------------

    private val pillH get() = d(36f)
    /** The home page's copy is a bit smaller so the whole page fits above the tab bar. */
    private fun phoneW(w: Int) = minOf(d(if (kind == "how") 146f else 168f), w * 0.48f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val ph = phoneW(w) * 1.75f
        setMeasuredDimension(w, (ph + d(12f) + pillH + d(4f)).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pw = phoneW(w)
        phone.set(d(2f), d(2f), d(2f) + pw, d(2f) + pw * 1.75f)
        scr.set(phone.left + d(4f), phone.top + d(4f), phone.right - d(4f), phone.bottom - d(4f))
    }

    // ---- timing helpers ------------------------------------------------------------------------

    private fun seg(t: Long, a: Long, b: Long): Float {
        if (t <= a) return 0f
        if (t >= b) return 1f
        return EASE.getInterpolation((t - a).toFloat() / (b - a))
    }
    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
    private fun tap(t: Long, at: Long) = seg(t, at, at + 120) * (1f - seg(t, at + 120, at + 300))

    // ---- drawing -------------------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        var t: Long
        if (animating) {
            t = SystemClock.uptimeMillis() - start
            while (t >= durations[step]) { start += durations[step]; step = (step + 1) % 3; t = SystemClock.uptimeMillis() - start }
            if (t < 0) t = 0
        } else t = durations[step] - 1

        p.shader = null
        p.color = 0xFF05060B.toInt()
        c.drawRoundRect(phone, d(24f), d(24f), p)
        st.color = if (night) 0xFF2E3146.toInt() else 0x33000000; st.strokeWidth = d(1.5f)
        c.drawRoundRect(phone, d(24f), d(24f), st)
        c.save()
        path.reset(); path.addRoundRect(scr, d(20f), d(20f), Path.Direction.CW)
        c.clipPath(path)
        when (kind) {
            "how" -> when (step) { 0 -> sceneMerge(c, t); 1 -> scenePeek(c, t); else -> sceneSwipe(c, t) }
            "open" -> when (step) { 0 -> sceneQs(c, t); 1 -> sceneShortcut(c, t); else -> sceneWidget(c, t) }
            "when" -> when (step) { 0 -> sceneHomeOnly(c, t); 1 -> sceneTyping(c, t); else -> sceneLock(c, t) }
            else -> when (step) { 0 -> sceneTap(c, t); 1 -> sceneOpenLine(c, t); else -> sceneSmart(c, t) }
        }
        navBar(c)
        c.restore()

        drawSteps(c, t)
        drawPill(c, t)
        if (animating && isShown) postInvalidateOnAnimation()
    }

    // Screen coordinates: x, y in dp from the screen's top-left.
    private fun X(x: Float) = scr.left + d(x)
    private fun Y(y: Float) = scr.top + d(y)
    private val SW get() = scr.width() / dp
    private val SH get() = scr.height() / dp

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, b: Boolean = false,
                     maxW: Float = 0f, alpha: Float = 1f, center: Boolean = false) {
        if (alpha <= 0f) return
        tp.textSize = size * sp
        tp.typeface = if (b) bold else Typeface.DEFAULT
        tp.color = color
        tp.alpha = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        val str = if (maxW > 0f) TextUtils.ellipsize(s, tp, d(maxW), TextUtils.TruncateAt.END).toString() else s
        val w = tp.measureText(str)
        c.drawText(str, if (center) X(x) - w / 2 else X(x), Y(y), tp)
    }

    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, color: Int, alpha: Float = 1f) {
        if (alpha <= 0f) return
        p.shader = null
        p.color = color; p.alpha = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        r.set(X(x0), Y(y0), X(x1), Y(y1))
        c.drawRoundRect(r, d(rad), d(rad), p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, color: Int, alpha: Float = 1f) {
        if (alpha <= 0f) return
        p.shader = null
        p.color = color; p.alpha = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        c.drawCircle(X(x), Y(y), d(rad), p)
    }

    private fun avatar(c: Canvas, chat: Chat, x: Float, y: Float, rad: Float, alpha: Float = 1f) {
        if (alpha <= 0f) return
        circle(c, x, y, rad, chat.color, alpha)
        tp.textSize = rad * 0.95f * dp; tp.typeface = bold; tp.color = 0xFFFFFFFF.toInt(); tp.alpha = (255 * alpha).toInt()
        val w = tp.measureText(chat.av)
        c.drawText(chat.av, X(x) - w / 2, Y(y) - (tp.ascent() + tp.descent()) / 2, tp)
    }

    private fun navBar(c: Canvas) = box(c, SW / 2 - 24f, SH - 6f, SW / 2 + 24f, SH - 3f, 1.5f, 0x8CFFFFFF.toInt())

    private fun wallpaper(c: Canvas, dark: Boolean = false) {
        p.shader = LinearGradient(scr.left, scr.top, scr.right, scr.bottom,
            if (dark) intArrayOf(0xFF20223F.toInt(), 0xFF15172B.toInt(), 0xFF0D0E1A.toInt())
            else intArrayOf(0xFF3B3F7A.toInt(), 0xFF262A52.toInt(), 0xFF1A1B31.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        p.alpha = 255
        c.drawRect(scr, p)
        p.shader = null
    }

    /** Home screen: clock, apps (LINE second, Sidepeek fourth on the first row), dock. */
    private fun home(c: Canvas, rows: Int = 2, skip: Set<Int> = emptySet()) {
        wallpaper(c)
        text(c, s(R.string.demo_clock), 14f, 52f, 30f, ink, b = true)
        text(c, s(R.string.demo_date), 15f, 68f, 9f, 0xFFC9CBE0.toInt())
        for (row in 0 until rows) for (col in 0 until 4) {
            if (row * 4 + col in skip) continue
            val (x, y) = appPos(col, row)
            when {
                row == 0 && col == 1 -> {
                    box(c, x - 14f, y - 14f, x + 14f, y + 14f, 8f, 0xFF5A5E73.toInt())
                    text(c, "LINE", x, y + 3f, 7.5f, ink, b = true, center = true)
                    label(c, "LINE", x, y)
                }
                row == 0 && col == 3 -> { spIcon(c, x, y); label(c, "Sidepeek", x, y) }
                row == 0 && col == 0 -> { photosIcon(c, x, y); label(c, s(R.string.demo_app_photos), x, y) }
                else -> box(c, x - 14f, y - 14f, x + 14f, y + 14f, 8f, 0xFF4A4E6B.toInt())
            }
        }
        // Dock: a faint tray with four apps (an empty tray read as a blank white box).
        box(c, 8f, SH - 50f, SW - 8f, SH - 14f, 14f, 0x14FFFFFF)
        val dockColors = intArrayOf(0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFF59E0B.toInt(), 0xFF6B7280.toInt())
        for (i in 0 until 4) {
            val (x, _) = appPos(i, 0)
            val y = SH - 32f
            box(c, x - 12f, y - 12f, x + 12f, y + 12f, 7f, dockColors[i], 0.85f)
            circle(c, x, y, 4.5f, 0xFFFFFFFF.toInt(), 0.85f)
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, alpha: Float = 1f) =
        text(c, s, x, y + 25f, 7.5f, 0xFFD9DBE8.toInt(), center = true, maxW = 44f, alpha = alpha)

    /** The Sidepeek app icon: green square with a side panel mark. */
    private fun spIcon(c: Canvas, x: Float, y: Float, size: Float = 28f, alpha: Float = 1f) {
        val h = size / 2
        box(c, x - h, y - h, x + h, y + h, size * 0.29f, brand, alpha)
        box(c, x + h * 0.07f, y - h * 0.57f, x + h * 0.57f, y + h * 0.57f, size * 0.07f, 0xFFFFFFFF.toInt(), alpha)
    }

    private fun photosIcon(c: Canvas, x: Float, y: Float) {
        box(c, x - 14f, y - 14f, x + 14f, y + 14f, 8f, 0xFFE8ECF4.toInt())
        circle(c, x - 4f, y - 4f, 4f, 0xFFF59E0B.toInt())
        circle(c, x + 4f, y - 4f, 4f, 0xFFEF4444.toInt(), 0.9f)
        circle(c, x - 4f, y + 4f, 4f, 0xFF3B82F6.toInt(), 0.9f)
        circle(c, x + 4f, y + 4f, 4f, 0xFF22C55E.toInt(), 0.9f)
    }

    private fun appPos(col: Int, row: Int): Pair<Float, Float> {
        val gap = (SW - 28f - 28f) / 3f
        return (14f + 14f + col * gap) to (102f + row * 50f)
    }

    /** One of Sidepeek's notifications in the shade. */
    private fun notifCard(c: Canvas, chat: Chat, y: Float, a: Float = 1f, h: Float = 52f) {
        box(c, 6f, y, SW - 6f, y + h, 13f, cardBg, a)
        avatar(c, chat, 21f, y + 17f, 10f, a)
        circle(c, 28f, y + 25f, 4.5f, 0xFF262838.toInt(), a)
        circle(c, 28f, y + 25f, 3.2f, brand, a)
        val tx = 37f; val mw = SW - 6f - tx - 6f
        text(c, "Sidepeek ・ ${chat.time}", tx, y + 13f, 7f, dim, maxW = mw, alpha = a)
        text(c, chat.name, tx, y + 26f, 10f, ink, b = true, maxW = mw, alpha = a)
        text(c, chat.line, tx, y + 39f, 9f, 0xFFD9DBE4.toInt(), maxW = mw, alpha = a)
    }

    /**
     * Notification shade pulled down by [down] (0 = up, 1 = full), with Sidepeek's notification
     * for each chat whose [present] is above 0 (shrinking ones slide the rest up).
     */
    private fun shade(c: Canvas, down: Float, present: FloatArray) {
        if (down <= 0f) return
        c.save(); c.translate(0f, d((down - 1f) * SH))
        box(c, 0f, 0f, SW, SH, 0f, 0xFF0E0F1A.toInt())
        text(c, s(R.string.demo_clock), 12f, 34f, 20f, ink, b = true)
        text(c, s(R.string.demo_date), 13f, 48f, 8.5f, dim)
        var y = 58f
        val h = 52f
        chats.forEachIndexed { i, chat ->
            val pr = present[i]
            if (pr <= 0f) return@forEachIndexed
            c.save()
            r.set(X(6f), Y(y), X(SW - 6f), Y(y + h * pr)); c.clipRect(r)
            notifCard(c, chat, y, pr, h)
            c.restore()
            y += (h + 6f) * pr
        }
        // Nothing left: the "all clear" mark.
        val emptyA = 1f - (present.maxOrNull() ?: 0f).coerceIn(0f, 1f)
        if (emptyA > 0f) {
            val cy = 120f
            circle(c, SW / 2, cy, 18f, 0x2922C55E, emptyA)
            check(c, SW / 2, cy, emptyA)
            text(c, s(R.string.demo_no_notifs), SW / 2, cy + 34f, 10f, ink, b = true, center = true, alpha = emptyA)
        }
        c.restore()
    }

    private fun check(c: Canvas, x: Float, y: Float, a: Float) {
        st.color = 0xFF22C55E.toInt(); st.alpha = (255 * a).toInt(); st.strokeWidth = d(2.6f)
        st.strokeCap = Paint.Cap.ROUND; st.strokeJoin = Paint.Join.ROUND
        path.reset()
        path.moveTo(X(x - 7f), Y(y + 0.5f)); path.lineTo(X(x - 2f), Y(y + 5f)); path.lineTo(X(x + 8f), Y(y - 5f))
        c.drawPath(path, st)
        st.strokeCap = Paint.Cap.BUTT
    }

    /** A dark app bar with an optional small grey app label on the right. */
    private fun bar(c: Canvas, title: String, back: Boolean, alpha: Float, glow: Float = 0f, app: String? = "LINE") {
        box(c, 0f, 0f, SW, 48f, 0f, 0xFF1E2030.toInt(), alpha)
        var x = 12f
        if (back) { text(c, "‹", 10f, 36f, 16f, dim, alpha = alpha); x = 24f }
        text(c, title, x, 34f, 11f, ink, b = true, maxW = SW - x - 44f, alpha = alpha)
        if (app != null) {
            r.set(X(SW - 36f), Y(25f), X(SW - 10f), Y(37f))
            st.color = 0xFF3A3D52.toInt(); st.alpha = (255 * alpha).toInt(); st.strokeWidth = d(1f)
            c.drawRoundRect(r, d(3f), d(3f), st)
            text(c, app, SW - 23f, 34f, 7f, dim, b = true, center = true, alpha = alpha)
        }
        if (glow > 0f) {
            // Advanced clearing reads only this chat name at the top.
            tp.textSize = 11f * sp; tp.typeface = bold
            val w = minOf(tp.measureText(title) / dp, SW - x - 44f)
            r.set(X(x - 4f), Y(21f), X(x + w + 4f), Y(39f))
            st.color = 0xFF22C55E.toInt(); st.alpha = (255 * glow * alpha).toInt(); st.strokeWidth = d(1.6f)
            c.drawRoundRect(r, d(5f), d(5f), st)
        }
    }

    private fun chatList(c: Canvas, alpha: Float) {
        if (alpha <= 0f) return
        box(c, 0f, 0f, SW, SH, 0f, 0xFF15161F.toInt(), alpha)
        bar(c, s(R.string.demo_chats), false, alpha)
        chats.forEachIndexed { i, chat ->
            val y = 70f + i * 44f
            avatar(c, chat, 24f, y, 13f, alpha)
            text(c, chat.name, 44f, y - 2f, 10f, ink, b = true, maxW = SW - 44f - 30f, alpha = alpha)
            text(c, chat.line, 44f, y + 11f, 8.5f, dim, maxW = SW - 44f - 30f, alpha = alpha)
            circle(c, SW - 16f, y, 7f, 0xFF5A5E73.toInt(), alpha)
            text(c, "${i + 1}", SW - 16f, y + 3f, 8f, ink, b = true, center = true, alpha = alpha)
        }
    }

    private fun chatRoom(c: Canvas, alpha: Float, glow: Float = 0f, kb: Float = 0f, typed: Float = 0f) {
        if (alpha <= 0f) return
        val chat = chats[0]
        box(c, 0f, 0f, SW, SH, 0f, 0xFF15161F.toInt(), alpha)
        bar(c, chat.name, true, alpha, glow)
        // When the keyboard is up the messages scroll up a little, staying below the bar.
        val lift = kb * 112f
        c.save()
        r.set(X(0f), Y(48f), X(SW), Y(SH)); c.clipRect(r)
        c.translate(0f, -d(kb * 60f))
        avatar(c, chat, 20f, 74f, 9f, alpha)
        text(c, s(R.string.demo_bubble_sender), 34f, 68f, 7.5f, dim, alpha = alpha)
        bubble(c, s(R.string.demo_bubble1), 34f, 72f, false, alpha)
        bubble(c, chat.line.substringAfter("：").substringAfter(": "), 34f, 102f, false, alpha)
        bubble(c, s(R.string.demo_bubble_me), 0f, 136f, true, alpha)
        c.restore()
        // Input field (rises with the keyboard), with text being typed.
        val iy = SH - 44f - lift
        box(c, 10f, iy, SW - 10f, iy + 26f, 13f, 0xFF23253A.toInt(), alpha)
        if (typed > 0f) {
            val msg = s(R.string.demo_typing)
            val n = (msg.length * typed).toInt().coerceIn(0, msg.length)
            text(c, msg.substring(0, n), 20f, iy + 17f, 9.5f, ink, alpha = alpha)
        }
        if (kb > 0f) keyboard(c, SH - 112f * kb + 0f, alpha)
    }

    private fun keyboard(c: Canvas, top: Float, alpha: Float) {
        box(c, 0f, top, SW, SH, 0f, 0xFF2A2C3A.toInt(), alpha)
        val keyW = (SW - 8f) / 10f
        for (row in 0 until 3) {
            val n = 10 - row
            val x0 = 4f + (10 - n) * keyW / 2
            for (k in 0 until n) {
                val x = x0 + k * keyW
                box(c, x + 1.5f, top + 8f + row * 22f, x + keyW - 1.5f, top + 26f + row * 22f, 3f, 0xFF474A5E.toInt(), alpha)
            }
        }
        box(c, SW * 0.25f, top + 74f, SW * 0.75f, top + 92f, 3f, 0xFF474A5E.toInt(), alpha)
    }

    private fun bubble(c: Canvas, s: String, x: Float, y: Float, me: Boolean, alpha: Float) {
        tp.textSize = 9.5f * sp; tp.typeface = Typeface.DEFAULT
        val maxW = SW - 60f
        val w = minOf(tp.measureText(s) / dp, maxW) + 16f
        val x0 = if (me) SW - 10f - w else x
        box(c, x0, y, x0 + w, y + 24f, 11f, if (me) 0xFF1F5135.toInt() else 0xFF2D3042.toInt(), alpha)
        text(c, s, x0 + 8f, y + 16f, 9.5f, ink, maxW = maxW, alpha = alpha)
    }

    /** A photo gallery app (an ordinary app that isn't the home screen). */
    private fun photosApp(c: Canvas, alpha: Float) {
        if (alpha <= 0f) return
        box(c, 0f, 0f, SW, SH, 0f, 0xFF15161F.toInt(), alpha)
        bar(c, s(R.string.demo_app_photos), false, alpha, app = null)
        val colors = intArrayOf(0xFF8B5CF6.toInt(), 0xFFF59E0B.toInt(), 0xFF0EA5E9.toInt(), 0xFFEF4444.toInt(),
            0xFF22C55E.toInt(), 0xFFEC4899.toInt(), 0xFF64748B.toInt(), 0xFF14B8A6.toInt(), 0xFFF97316.toInt())
        val cell = (SW - 12f) / 3f
        for (i in 0 until 12) {
            val x = 6f + (i % 3) * cell; val y = 54f + (i / 3) * cell
            box(c, x + 1f, y + 1f, x + cell - 1f, y + cell - 1f, 3f, colors[i % colors.size], alpha * 0.75f)
        }
    }

    private val handleLeft get() = Prefs.handleSide(context) == Prefs.SIDE_LEFT

    /** The Peek 條 on the user's side. */
    private fun peekBar(c: Canvas, alpha: Float) {
        if (alpha <= 0.01f) return
        val y0 = SH * 0.36f; val y1 = SH * 0.36f + 46f
        if (handleLeft) box(c, 2f, y0, 6f, y1, 2f, 0xFFFFFFFF.toInt(), alpha * 0.9f)
        else box(c, SW - 6f, y0, SW - 2f, y1, 2f, 0xFFFFFFFF.toInt(), alpha * 0.9f)
    }

    /** The side panel sliding in from the Peek 條 side ([p] 0 = hidden, 1 = open). */
    private fun panel(c: Canvas, p: Float) {
        if (p <= 0f) return
        val w = SW * 0.72f
        val x0 = if (handleLeft) lerp(-w, 4f, p) else lerp(SW, SW - 4f - w, p)
        val top = SH * 0.16f; val bottom = SH * 0.84f
        box(c, 0f, 0f, SW, SH, 0f, 0x66000000, p)
        box(c, x0, top, x0 + w, bottom, 16f, 0xF71C1D2A.toInt())
        chats.forEachIndexed { i, chat ->
            val y = top + 16f + i * 46f
            box(c, x0 + 6f, y - 6f, x0 + w - 6f, y + 34f, 10f, 0xFF2A2C3C.toInt())
            avatar(c, chat, x0 + 19f, y + 8f, 9f)
            text(c, chat.name, x0 + 33f, y + 8f, 9f, ink, b = true, maxW = w - 42f)
            text(c, chat.line, x0 + 33f, y + 22f, 8.5f, 0xFFD9DBE4.toInt(), maxW = w - 42f)
        }
    }

    private fun finger(c: Canvas, x: Float, y: Float, alpha: Float, press: Float = 0f, hold: Float = 0f) {
        if (alpha <= 0f) return
        val rad = 11f * (1f - press * 0.18f)
        if (hold > 0f) circle(c, x, y, rad + 12f * hold, 0x33FFFFFF, alpha)
        circle(c, x, y, rad, 0x4DFFFFFF, alpha)
        st.color = 0xD9FFFFFF.toInt(); st.alpha = (0xD9 * alpha).toInt(); st.strokeWidth = d(2f)
        c.drawCircle(X(x), Y(y), d(rad), st)
    }

    private val cardY = { i: Int -> 58f + i * 58f + 26f }

    // ---- LINE 通知清理 ---------------------------------------------------------------------------

    /** 點通知進聊天室：點週六聚餐的通知 → 進聊天室 → 回來只剩另外兩則。 */
    private fun sceneTap(c: Canvas, t: Long) {
        home(c)
        val gone = seg(t, 3500, 4100)
        shade(c, 1f, floatArrayOf(1f - gone, 1f, 1f))
        chatRoom(c, seg(t, 1500, 1800) * (1f - seg(t, 3000, 3300)))
        finger(c, SW * 0.6f, cardY(0), seg(t, 500, 800) * (1f - seg(t, 1400, 1600)), tap(t, 1150))
    }

    /** 直接打開 LINE：收起通知欄 → 點 LINE → 看完回桌面 → 通知欄全部清空。 */
    private fun sceneOpenLine(c: Canvas, t: Long) {
        home(c)
        val (lx, ly) = appPos(1, 0)
        chatList(c, seg(t, 2100, 2400) * (1f - seg(t, 3700, 4000)))
        val back = t >= 3000
        val down = (1f - seg(t, 900, 1350)) + if (back) seg(t, 4300, 4900) else 0f
        val left = if (back && switchOn(1) == false) 1f else if (back) 0f else 1f
        shade(c, down, floatArrayOf(left, left, left))
        finger(c, lx, ly, seg(t, 1400, 1600) * (1f - seg(t, 2000, 2200)), tap(t, 1850))
        finger(c, SW / 2, lerp(16f, SH * 0.55f, seg(t, 4300, 4900)), seg(t, 4100, 4300) * (1f - seg(t, 4900, 5100)))
    }

    /** 進階清理：打開 LINE → 進週六聚餐 → 讀到聊天室名稱 → 回來只有它被收掉。 */
    private fun sceneSmart(c: Canvas, t: Long) {
        home(c)
        val (lx, ly) = appPos(1, 0)
        val out = 1f - seg(t, 4700, 5000)
        chatList(c, seg(t, 2100, 2400) * out)
        chatRoom(c, seg(t, 3000, 3300) * out, glow = seg(t, 3500, 3800) * (1f - seg(t, 4400, 4600)))
        val back = t >= 3000
        val down = (1f - seg(t, 900, 1350)) + if (back) seg(t, 5200, 5800) else 0f
        shade(c, down, floatArrayOf(if (back) 0f else 1f, 1f, 1f))
        finger(c, lx, ly, seg(t, 1400, 1600) * (1f - seg(t, 2000, 2200)), tap(t, 1850))
        finger(c, SW * 0.55f, 70f, seg(t, 2500, 2700) * (1f - seg(t, 3000, 3150)), tap(t, 2850))
        finger(c, SW / 2, lerp(16f, SH * 0.55f, seg(t, 5200, 5800)), seg(t, 5000, 5200) * (1f - seg(t, 5800, 6000)))
    }

    // ---- 首頁「怎麼用」 ---------------------------------------------------------------------------

    private val howChat get() = Chat(s(R.string.how_group_av), s(R.string.how_group), "", s(R.string.how_time), 0xFF3B82F6.toInt())
    private val howLines get() = listOf(R.string.how_m1_sender to R.string.how_m1, R.string.how_m2_sender to R.string.how_m2,
        R.string.how_m3_sender to R.string.how_m3).map { (a, b) -> s(a) to s(b) }

    /**
     * 整理成對話：拉下通知欄 → LINE 的通知一則一則蓋在前一則上面（前面的被壓在底下，只露出邊，
     * 內容看不到）→ 那一疊變成一張 Sidepeek 對話通知，往下展開，三則訊息一行一行出現。
     * 下面另一個聊天室的通知也一起換成 Sidepeek 的。
     */
    private fun sceneMerge(c: Canvas, t: Long) {
        home(c)
        val down = seg(t, 300, 900)
        val chat = howChat
        val lines = howLines
        val other = chats[1]
        val sep = s(R.string.name_sep)
        val arrive = longArrayOf(1200L, 2000L, 2800L)
        val merge = seg(t, 3700, 4200)          // LINE → Sidepeek
        val grow = seg(t, 4100, 4600)           // the card opens up to three lines
        if (down > 0f) {
            c.save(); c.translate(0f, d((down - 1f) * SH))
            box(c, 0f, 0f, SW, SH, 0f, 0xFF0E0F1A.toInt())
            text(c, s(R.string.demo_clock), 12f, 30f, 16f, ink, b = true)
            text(c, s(R.string.demo_date), 13f, 43f, 8f, dim)
            val top = 54f
            val lineH = 13f
            val cardH = lerp(42f, 30f + 3 * lineH, grow)
            // How many LINE notifications have arrived so far (the newest is on top).
            val n = arrive.count { t >= it }
            // Older ones squashed underneath, only their edges showing above the newest.
            val stackA = 1f - merge
            for (depth in (n - 1) downTo 1) {
                val inset = 7f * depth
                val y = top - 6f * depth
                box(c, 6f + inset, y, SW - 6f - inset, y + 40f, 11f,
                    if (depth == 1) 0xFF3A3D4C.toInt() else 0xFF2E3040.toInt(), stackA)
            }
            if (n > 0) {
                val newest = n - 1
                val drop = seg(t, arrive[newest], arrive[newest] + 350)
                val y = top - (1f - drop) * 16f
                // The newest slides in on top of the previous one (the first one also fades in).
                box(c, 6f, y, SW - 6f, y + cardH, 12f, cardBg, if (newest == 0) maxOf(drop, 0.0001f) else 1f)
                // LINE's own notification: only the latest message.
                val la = drop * (1f - merge)
                if (la > 0f) {
                    box(c, 12f, y + 7f, 21f, y + 16f, 2.5f, 0xFF5A5E73.toInt(), la)
                    text(c, "LINE ・ " + chat.time, 25f, y + 14f, 7f, dim, alpha = la)
                    text(c, chat.name, 12f, y + 26f, 9.5f, ink, b = true, maxW = SW - 24f, alpha = la)
                    val (who, what) = lines[newest]
                    text(c, who + sep + what, 12f, y + 37f, 8.5f, 0xFFD9DBE4.toInt(), maxW = SW - 24f, alpha = la)
                }
                // Sidepeek's conversation notification: every message.
                if (merge > 0f) {
                    avatar(c, chat, 20f, y + 15f, 8.5f, merge)
                    circle(c, 26f, y + 21f, 3.8f, 0xFF262838.toInt(), merge)
                    circle(c, 26f, y + 21f, 2.6f, brand, merge)
                    text(c, "Sidepeek ・ " + chat.time, 33f, y + 12f, 7f, 0xFF4ADE80.toInt(), maxW = SW - 43f, alpha = merge)
                    text(c, chat.name, 33f, y + 24f, 9.5f, ink, b = true, maxW = SW - 43f, alpha = merge)
                    lines.forEachIndexed { i, (who, what) ->
                        val la2 = seg(t, 4300L + i * 300L, 4600L + i * 300L)
                        if (la2 <= 0f) return@forEachIndexed
                        val ly = y + 38f + i * lineH
                        tp.textSize = 8.5f * sp; tp.typeface = bold
                        val nw = tp.measureText(who + sep) / dp
                        text(c, who + sep, 12f, ly, 8.5f, ink, b = true, alpha = la2)
                        text(c, what, 12f + nw, ly, 8.5f, 0xFFD9DBE4.toInt(), maxW = SW - 24f - nw, alpha = la2)
                    }
                }
            }
            // Another chat's notification below, also taken over by Sidepeek.
            val oy = top + cardH + 8f
            box(c, 6f, oy, SW - 6f, oy + 40f, 12f, cardBg)
            val oa = 1f - merge
            box(c, 12f, oy + 7f, 21f, oy + 16f, 2.5f, 0xFF5A5E73.toInt(), oa)
            text(c, "LINE ・ " + other.time, 25f, oy + 14f, 7f, dim, alpha = oa)
            if (merge > 0f) {
                avatar(c, other, 20f, oy + 15f, 8.5f, merge)
                text(c, "Sidepeek ・ " + other.time, 33f, oy + 12f, 7f, 0xFF4ADE80.toInt(), maxW = SW - 43f, alpha = merge)
            }
            val nx = lerp(12f, 33f, merge)
            text(c, other.name, nx, oy + 26f, 9.5f, ink, b = true, maxW = SW - 12f - nx)
            text(c, other.line, nx, oy + 37f, 8.5f, 0xFFD9DBE4.toInt(), maxW = SW - 12f - nx)
            c.restore()
        }
        // Finger pulling the shade down
        finger(c, SW / 2, lerp(6f, SH * 0.45f, down), seg(t, 100, 300) * (1f - seg(t, 900, 1100)))
    }

    /** Peek 條：桌面邊緣，Peek 條從螢幕外滑進來，再閃兩圈綠光，讓人看到它在哪。 */
    private fun scenePeek(c: Canvas, t: Long) {
        home(c)
        val a = seg(t, 500, 1100)
        val out = if (handleLeft) -1f else 1f
        c.save(); c.translate(d(out * 10f * (1f - a)), 0f)
        peekBar(c, a)
        c.restore()
        val x = if (handleLeft) 4f else SW - 4f
        val y = SH * 0.36f + 23f
        for (k in 0 until 2) {
            val f = seg(t, 1300L + k * 900L, 2200L + k * 900L)
            if (f <= 0f || f >= 1f) continue
            st.color = 0xFF4ADE80.toInt(); st.alpha = ((1f - f) * 200).toInt(); st.strokeWidth = d(1.6f)
            c.drawCircle(X(x), Y(y), d(10f + 22f * f), st)
        }
    }

    /** 滑開看訊息：手指從 Peek 條往內滑，側邊欄跟著滑出來。 */
    private fun sceneSwipe(c: Canvas, t: Long) {
        home(c)
        peekBar(c, 1f - seg(t, 1300, 1500) + seg(t, 6000, 6400))
        val ey = SH * 0.36f + 23f
        val edgeX = if (handleLeft) 4f else SW - 4f
        val dir = if (handleLeft) 1f else -1f
        val sw = seg(t, 800, 1500)
        panel(c, seg(t, 1200, 1800) * (1f - seg(t, 5900, 6400)))
        finger(c, edgeX + dir * 60f * sw, ey, seg(t, 500, 750) * (1f - seg(t, 1500, 1750)))
    }

    // ---- 其他打開側邊欄的方法 ------------------------------------------------------------------

    /** 快速設定：在相簿裡拉下快速設定 → 點 Sidepeek 方塊 → 側邊欄滑出來，相簿還在後面。 */
    private fun sceneQs(c: Canvas, t: Long) {
        photosApp(c, 1f)
        val down = seg(t, 800, 1400) * (1f - seg(t, 2400, 2800))
        if (down > 0f) {
            c.save(); c.translate(0f, d((down - 1f) * 190f))
            box(c, 0f, 0f, SW, 190f, 18f, 0xFF0E0F1A.toInt())
            text(c, s(R.string.demo_clock), 12f, 30f, 16f, ink, b = true)
            val cell = (SW - 16f) / 4f
            for (i in 0 until 8) {
                val cx = 8f + cell * (i % 4) + cell / 2; val cy = 58f + (i / 4) * 54f
                val mine = i == 6
                box(c, cx - 15f, cy - 15f, cx + 15f, cy + 15f, 10f, if (mine) brand else 0xFF2E3144.toInt())
                if (mine) box(c, cx + 1f, cy - 8f, cx + 8f, cy + 8f, 2f, 0xFFFFFFFF.toInt())
                else circle(c, cx, cy, 5f, 0xFF5A5E73.toInt())
                text(c, if (mine) "Sidepeek" else "", cx, cy + 26f, 7.5f, if (mine) ink else dim, center = true, maxW = cell)
                if (!mine) box(c, cx - 10f, cy + 22f, cx + 10f, cy + 25f, 1.5f, 0xFF3A3D52.toInt())
            }
            box(c, SW / 2 - 14f, 176f, SW / 2 + 14f, 179f, 1.5f, 0xFF5A5E73.toInt())
            c.restore()
        }
        panel(c, seg(t, 2900, 3400) * (1f - seg(t, 6500, 6950)))
        val cell = (SW - 16f) / 4f
        val tx = 8f + cell * 2 + cell / 2; val ty = 58f + 54f
        val fa = seg(t, 600, 800) * (1f - seg(t, 2300, 2500))
        val fx = if (t < 1500) SW / 2 else lerp(SW / 2, tx, seg(t, 1500, 1900))
        val fy = if (t < 1500) lerp(8f, 150f, seg(t, 800, 1400)) else lerp(150f, ty, seg(t, 1500, 1900))
        finger(c, fx, fy, fa, tap(t, 2050))
    }

    /** App 捷徑：長按 Sidepeek 圖示 → 把「開啟側邊欄」拖到桌面 → 點它，側邊欄在桌面上打開。 */
    private fun sceneShortcut(c: Canvas, t: Long) {
        val (ix, iy) = appPos(3, 0)
        val (tx, ty) = appPos(1, 1)
        home(c, skip = setOf(5))
        val menu = seg(t, 1300, 1550) * (1f - seg(t, 2100, 2350))
        if (menu > 0f) {
            val mx1 = SW - 6f; val mx0 = mx1 - 112f
            box(c, mx0, iy + 20f, mx1, iy + 76f, 12f, 0xFF2A2C3C.toInt(), menu)
            spIcon(c, mx0 + 14f, iy + 34f, 14f, menu)
            text(c, s(R.string.shortcut_open_panel), mx0 + 26f, iy + 37.5f, 9f, ink, b = true, maxW = 82f, alpha = menu)
            box(c, mx0 + 8f, iy + 48f, mx1 - 8f, iy + 48.6f, 0f, 0x33FFFFFF, menu)
            circle(c, mx0 + 14f, iy + 62f, 5f, 0xFF5A5E73.toInt(), menu)
            text(c, s(R.string.demo_app_info), mx0 + 26f, iy + 65.5f, 9f, dim, maxW = 82f, alpha = menu)
        }
        val mx0 = SW - 6f - 112f
        val sx0 = mx0 + 14f; val sy0 = iy + 34f
        val drag = seg(t, 2100, 3000)
        val sx = lerp(sx0, tx, drag); val sy = lerp(sy0, ty, drag)
        if (t in 2100..2999) spIcon(c, sx, sy, lerp(14f, 28f, drag), 0.95f)
        if (t >= 3000) {
            spIcon(c, tx, ty)
            circle(c, tx + 10f, ty + 10f, 6f, 0xFFFFFFFF.toInt())
            circle(c, tx + 10f, ty + 10f, 3f, brand)
            label(c, s(R.string.shortcut_open_panel), tx, ty)
        }
        panel(c, seg(t, 3800, 4300) * (1f - seg(t, 7500, 7950)))
        val fa = seg(t, 300, 600) * (1f - seg(t, 3700, 3900))
        val (fx, fy) = when {
            t < 1650 -> lerp(SW / 2, ix, seg(t, 300, 700)) to lerp(SH * 0.7f, iy, seg(t, 300, 700))
            t < 2100 -> lerp(ix, sx0, seg(t, 1650, 1950)) to lerp(iy, sy0, seg(t, 1650, 1950))
            t < 3100 -> sx to sy
            else -> tx to ty
        }
        finger(c, fx, fy, fa, tap(t, 3500), hold = seg(t, 700, 1300) * (1f - seg(t, 1300, 1500)))
    }

    /** 桌面小工具：長按桌面空白處 → 小工具 → 選 Sidepeek「最近訊息」→ 出現在桌面。 */
    private fun sceneWidget(c: Canvas, t: Long) {
        home(c, rows = 1)
        // The widget on the home screen.
        val w = seg(t, 3600, 4100) * (1f - seg(t, 8000, 8450))
        if (w > 0f) {
            val sc = lerp(0.85f, 1f, w)
            // Between the app row and the dock: as many message rows as fit (the dock starts at SH - 52).
            val top = 136f
            val fit = ((SH - 60f - top - 26f) / 26f).toInt().coerceIn(1, 3)
            c.save(); c.scale(sc, sc, X(SW / 2), Y(top + (26f + fit * 26f) / 2))
            widgetCard(c, 8f, top, SW - 8f, w, rows = fit)
            c.restore()
        }
        // Bottom sheet: three buttons, then the widget list.
        val up = seg(t, 1300, 1600) * (1f - seg(t, 3200, 3600))
        val list = seg(t, 2200, 2500)
        if (up > 0f) {
            val hgt = lerp(64f, 150f, list)
            val top = SH - hgt * up
            box(c, 0f, top, SW, SH + 20f, 18f, 0xFF23253A.toInt())
            val names = listOf(s(R.string.demo_wallpaper), s(R.string.demo_widgets), s(R.string.demo_home_settings))
            val ba = 1f - list
            for (i in 0 until 3) {
                val x = SW / 6f * (1 + 2 * i)
                circle(c, x, top + 22f, 11f, if (i == 1) brand else 0xFF3A3D52.toInt(), ba)
                text(c, names[i], x, top + 46f, 8f, ink, center = true, maxW = SW / 3f - 4f, alpha = ba)
            }
            if (list > 0f) {
                text(c, s(R.string.demo_widgets), 14f, top + 22f, 11f, ink, b = true, alpha = list)
                text(c, "Sidepeek", 14f, top + 40f, 8.5f, dim, alpha = list)
                widgetCard(c, 12f, top + 48f, SW - 12f, list, mini = true)
            }
        }
        val (ex, ey) = SW / 2 to 200f
        val btnX = SW / 2; val btnY = SH - 64f + 22f
        val pickY = SH - 150f + 48f + 30f
        val fa = seg(t, 300, 600) * (1f - seg(t, 3100, 3300))
        val (fx, fy) = when {
            t < 1700 -> ex to lerp(SH - 30f, ey, seg(t, 300, 700))
            t < 2200 -> lerp(ex, btnX, seg(t, 1700, 1950)) to lerp(ey, btnY, seg(t, 1700, 1950))
            else -> btnX to lerp(btnY, pickY, seg(t, 2500, 2800))
        }
        finger(c, fx, fy, fa, maxOf(tap(t, 2050), tap(t, 2950)), hold = seg(t, 700, 1300) * (1f - seg(t, 1300, 1500)))
    }

    /** Sidepeek's「最近訊息」widget. */
    private fun widgetCard(c: Canvas, x0: Float, y0: Float, x1: Float, a: Float, mini: Boolean = false, rows: Int = 3) {
        val rows = if (mini) 2 else rows
        val h = 26f + rows * 26f
        box(c, x0, y0, x1, y0 + h, 14f, 0xF21C1C1E.toInt(), a)
        text(c, s(R.string.widget_name), x0 + 10f, y0 + 17f, 9f, ink, b = true, alpha = a)
        for (i in 0 until rows) {
            val chat = chats[i]
            val y = y0 + 26f + i * 26f
            avatar(c, chat, x0 + 18f, y + 10f, 8f, a)
            text(c, chat.name, x0 + 31f, y + 7f, 8f, 0xFF4ADE80.toInt(), b = true, maxW = x1 - x0 - 40f, alpha = a)
            text(c, chat.line, x0 + 31f, y + 18f, 8.5f, ink, maxW = x1 - x0 - 40f, alpha = a)
        }
    }

    // ---- Peek 條顯示時機 ------------------------------------------------------------------------

    /** 只在桌面顯示：桌面有 Peek 條 → 打開相簿 → 開關開著時 Peek 條藏起來。 */
    private fun sceneHomeOnly(c: Canvas, t: Long) {
        home(c)
        val (ax, ay) = appPos(0, 0)
        val app = seg(t, 1400, 1800) * (1f - seg(t, 5600, 6000))
        if (app > 0f) {
            val sc = lerp(0.2f, 1f, app)
            c.save(); c.scale(sc, sc, X(ax), Y(ay)); photosApp(c, app); c.restore()
        }
        peekBar(c, if (switchOn(0) == true) 1f - app else 1f)
        finger(c, ax, lerp(ay + 60f, ay, seg(t, 700, 1100)), seg(t, 700, 900) * (1f - seg(t, 1400, 1550)), tap(t, 1200))
    }

    /** 打字時隱藏：聊天室裡點輸入框 → 鍵盤出來 → 開關開著時 Peek 條淡出。 */
    private fun sceneTyping(c: Canvas, t: Long) {
        val kb = seg(t, 1300, 1700) * (1f - seg(t, 5600, 6000))
        chatRoom(c, 1f, kb = kb, typed = seg(t, 1900, 3600) * (1f - seg(t, 5600, 5700)))
        peekBar(c, if (switchOn(1) == true) 1f - kb else 1f)
        finger(c, SW / 2, lerp(SH - 10f, SH - 31f, seg(t, 700, 1000)), seg(t, 700, 900) * (1f - seg(t, 1250, 1400)), tap(t, 1100))
    }

    /** 鎖定畫面也顯示：開著時鎖定畫面有 Peek 條，往內滑會先要求解鎖；關著時沒有 Peek 條。 */
    private fun sceneLock(c: Canvas, t: Long) {
        wallpaper(c, dark = true)
        // Lock icon, big clock and date.
        val lx = SW / 2; val ly = 26f
        box(c, lx - 5f, ly - 1f, lx + 5f, ly + 7f, 1.5f, ink)
        st.color = ink; st.alpha = 255; st.strokeWidth = d(1.6f)
        r.set(X(lx - 3.5f), Y(ly - 6f), X(lx + 3.5f), Y(ly + 2f)); c.drawArc(r, 180f, 180f, false, st)
        text(c, s(R.string.demo_clock), SW / 2, 82f, 40f, ink, b = true, center = true)
        text(c, s(R.string.demo_date), SW / 2, 98f, 9f, 0xFFC9CBE0.toInt(), center = true)
        val on = switchOn(2) == true
        peekBar(c, if (on) 1f - seg(t, 1100, 1300) * (1f - seg(t, 5600, 5900)) else 0f)
        // Swipe in from the edge.
        val ey = SH * 0.36f + 23f
        val edgeX = if (handleLeft) 4f else SW - 4f
        val dir = if (handleLeft) 1f else -1f
        val sw = seg(t, 900, 1400)
        finger(c, edgeX + dir * 46f * sw, ey, seg(t, 700, 900) * (1f - seg(t, 1400, 1600)))
        // With the switch on: the panel asks to unlock first (no messages shown).
        val ask = if (on) seg(t, 1400, 1700) * (1f - seg(t, 5600, 5900)) else 0f
        if (ask > 0f) {
            val cy = SH - 70f
            circle(c, SW / 2, cy, 18f, 0x33FFFFFF, ask)
            st.color = ink; st.alpha = (230 * ask).toInt(); st.strokeWidth = d(1.6f)
            for (k in 0 until 3) {
                val rad = 4f + k * 3.5f
                r.set(X(SW / 2 - rad), Y(cy - rad), X(SW / 2 + rad), Y(cy + rad))
                c.drawArc(r, 200f + k * 10f, 220f - k * 20f, false, st)
            }
            text(c, s(R.string.demo_unlock), SW / 2, cy + 34f, 10f, ink, b = true, center = true, alpha = ask)
        }
    }

    // ---- right side: the three steps ---------------------------------------------------------

    private fun drawSteps(c: Canvas, t: Long) {
        val x0 = phone.right + d(16f)
        val w = (width - x0 - d(2f)).toInt().coerceAtLeast(d(60f).toInt())
        var y = phone.top + d(10f)
        for (i in 0 until 3) {
            val active = i == step
            val top = y
            val cx = x0 + d(11f); val cy = y + d(11f)
            p.shader = null
            if (active) { p.color = green; c.drawCircle(cx, cy, d(11f), p) }
            else { st.color = textDim; st.alpha = 110; st.strokeWidth = d(1.4f); c.drawCircle(cx, cy, d(10.3f), st) }
            tp.textSize = 12f * sp; tp.typeface = bold
            tp.color = if (active) 0xFFFFFFFF.toInt() else textDim
            val n = "${i + 1}"
            c.drawText(n, cx - tp.measureText(n) / 2, cy - (tp.ascent() + tp.descent()) / 2, tp)

            val tx = x0 + d(30f)
            val tw = (w - d(30f)).toInt().coerceAtLeast(1)
            tp.textSize = 15f * sp; tp.typeface = bold
            tp.color = if (active) textMain else textDim
            val tl = layout(title(i), tw)
            c.save(); c.translate(tx, y + d(1f)); tl.draw(c); c.restore()
            y += tl.height + d(1f)
            if (active) {
                tp.textSize = 13f * sp; tp.typeface = Typeface.DEFAULT; tp.color = textDim
                val sl = layout(stepSubs[i], tw)
                c.save(); c.translate(tx, y + d(4f)); sl.draw(c); c.restore()
                y += sl.height + d(4f)
                val f = (t.toFloat() / durations[step]).coerceIn(0f, 1f)
                p.color = textDim; p.alpha = 50
                r.set(tx, y + d(8f), tx + tw, y + d(11f)); c.drawRoundRect(r, d(1.5f), d(1.5f), p)
                p.color = green
                r.set(tx, y + d(8f), tx + tw * (if (animating) f else 1f), y + d(11f)); c.drawRoundRect(r, d(1.5f), d(1.5f), p)
                y += d(11f)
            }
            stepRows[i].set(x0 - d(6f), top - d(6f), width.toFloat(), y + d(8f))
            y += d(18f)
        }
    }

    private fun layout(s: String, w: Int): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, TextPaint(tp), w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(d(1.5f), 1f).setIncludePad(false).build()

    // ---- the result pill under the phone ---------------------------------------------------------

    private fun drawPill(c: Canvas, t: Long) {
        val a = seg(t, pillAt[step], pillAt[step] + 300) * (1f - seg(t, durations[step] - 300, durations[step]))
        if (a <= 0f) return
        tp.textSize = 13.5f * sp; tp.typeface = bold
        val str = TextUtils.ellipsize(pill(step), tp, width - d(52f), TextUtils.TruncateAt.END).toString()
        val w = tp.measureText(str) + d(40f)
        val top = phone.bottom + d(12f)
        val cx = width / 2f
        val lift = (1f - a) * d(6f)
        r.set(cx - w / 2, top + lift, cx + w / 2, top + pillH + lift)
        val g = green
        p.shader = null
        p.color = g; p.alpha = (0x1F * a).toInt()
        c.drawRoundRect(r, pillH / 2, pillH / 2, p)
        st.color = g; st.alpha = (0x73 * a).toInt(); st.strokeWidth = d(1f)
        c.drawRoundRect(r, pillH / 2, pillH / 2, st)
        p.color = g; p.alpha = (255 * a).toInt()
        c.drawCircle(r.left + d(17f), r.centerY(), d(4f), p)
        tp.color = g; tp.alpha = (255 * a).toInt()
        c.drawText(str, r.left + d(28f), r.centerY() - (tp.ascent() + tp.descent()) / 2, tp)
    }

    private companion object {
        val EASE = PathInterpolator(0.33f, 0f, 0.2f, 1f)
    }
}

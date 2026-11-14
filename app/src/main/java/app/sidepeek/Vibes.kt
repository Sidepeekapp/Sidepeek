package app.sidepeek

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * 「震動」for the LINE notifications Sidepeek posts: a few ready-made patterns, or a rhythm the
 * user records. Android freezes a channel's vibration once created, so a change moves to a new
 * channel ([Notifier.setMessageVibration]), the same way as the sound.
 */
object Vibes {
    private const val KEY = "msg_vibration"            // a preset id, or CUSTOM
    private const val KEY_CUSTOM = "msg_vibration_custom" // the last recorded rhythm, "0,200,100,…"
    const val CUSTOM = "custom"
    const val DEFAULT_ID = "lls"
    /** A recording lasts at most this long from the first press. */
    const val MAX_MS = 3000L
    /** Shorter presses are stretched to this, or the phone barely moves. */
    const val MIN_ON_MS = 40L

    /** [pattern] null = the phone's own default vibration; empty = no vibration. */
    class Preset(val id: String, val label: Int, val pattern: LongArray?)

    val PRESETS = listOf(
        // LINE's own channel pattern (one long, two short; read from LINE 26.16). Used since v1.1.0.
        Preset(DEFAULT_ID, R.string.vib_lls, longArrayOf(0, 330, 160, 100, 80, 140)),
        Preset("short1", R.string.vib_short1, longArrayOf(0, 120)),
        Preset("short2", R.string.vib_short2, longArrayOf(0, 120, 140, 120)),
        Preset("long", R.string.vib_long, longArrayOf(0, 800)),
        Preset("default", R.string.vib_default, null),
        Preset("off", R.string.vib_off, LongArray(0)),
    )
    /** What 「跟手機預設一樣」 plays as a preview (AOSP's default; the real one may differ by brand). */
    private val DEFAULT_PREVIEW = longArrayOf(0, 250, 250, 250)

    /** The choice saved in the app (a preset id or [CUSTOM]). */
    fun choice(ctx: Context): String {
        val id = Prefs.sp(ctx).getString(KEY, DEFAULT_ID) ?: DEFAULT_ID
        return when {
            id == CUSTOM && custom(ctx) != null -> id
            PRESETS.any { it.id == id } -> id
            else -> DEFAULT_ID
        }
    }

    /** What the button shows: 「不震動」 also when vibration was switched off in the system settings. */
    fun current(ctx: Context): String {
        val ch = try {
            ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(Notifier.msgChannel(ctx))
        } catch (_: Exception) { null }
        return if (ch != null && !ch.shouldVibrate()) "off" else choice(ctx)
    }

    fun label(ctx: Context, id: String): String =
        if (id == CUSTOM) ctx.getString(R.string.vib_custom)
        else ctx.getString((PRESETS.firstOrNull { it.id == id } ?: PRESETS[0]).label)

    fun custom(ctx: Context): LongArray? = parse(Prefs.sp(ctx).getString(KEY_CUSTOM, null))

    /** The channel pattern for [id]: null = phone default, empty = off. */
    fun pattern(ctx: Context, id: String = choice(ctx)): LongArray? =
        if (id == CUSTOM) custom(ctx) ?: PRESETS[0].pattern
        else (PRESETS.firstOrNull { it.id == id } ?: PRESETS[0]).pattern

    /** Saves the choice only; [Notifier.setMessageVibration] also moves the channel. */
    internal fun save(ctx: Context, id: String, recorded: LongArray? = null) {
        val e = Prefs.sp(ctx).edit().putString(KEY, id)
        if (recorded != null) e.putString(KEY_CUSTOM, encode(recorded))
        e.commit()
    }

    fun encode(p: LongArray) = p.joinToString(",")

    /** A saved rhythm, or null when it is missing or broken (needs at least one buzz). */
    fun parse(s: String?): LongArray? {
        if (s.isNullOrBlank()) return null
        val p = s.split(',').map { it.trim().toLongOrNull() ?: return null }
        if (p.size < 2 || p.any { it < 0 }) return null
        return p.toLongArray()
    }

    /**
     * Presses recorded as (start, end) in ms from the first press → an Android pattern
     * [wait, on, off, on, …]. Presses are cut at [MAX_MS] and stretched to [MIN_ON_MS].
     */
    fun toPattern(presses: List<Pair<Long, Long>>): LongArray {
        val out = ArrayList<Long>()
        var t = 0L
        for ((s0, e0) in presses.sortedBy { it.first }) {
            val s = maxOf(s0, t)
            if (s >= MAX_MS) break
            val e = minOf(maxOf(e0, s + MIN_ON_MS), MAX_MS)
            if (e <= s) continue
            out += s - t
            out += e - s
            t = e
        }
        return out.toLongArray()
    }

    fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)

    /** Plays [pattern] once (null = a stand-in for the phone default; empty = nothing). */
    fun preview(ctx: Context, pattern: LongArray?) {
        val v = vibrator(ctx) ?: return
        v.cancel()
        val p = pattern ?: DEFAULT_PREVIEW
        if (p.size < 2 || p.drop(1).all { it == 0L }) return
        try { v.vibrate(VibrationEffect.createWaveform(p, -1)) } catch (_: Exception) {}
    }

    fun stop(ctx: Context) { try { vibrator(ctx)?.cancel() } catch (_: Exception) {} }
}

/**
 * 「自己錄」: press and hold to buzz, let go to stop; up to [Vibes.MAX_MS] from the first press.
 * The bars along the bottom show the rhythm. Once done, a tap plays it back.
 */
@SuppressLint("ViewConstructor")
class RhythmPad(context: Context, private val onChange: () -> Unit) : View(context) {
    private val presses = ArrayList<Pair<Long, Long>>()
    private var t0 = 0L
    private var pressStart = -1L
    var recording = false; private set
    var done = false; private set

    private val dp = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.brand_tint) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.brand_accent) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.brand_accent); alpha = 50 }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        color = a.getColor(0, 0xFF222222.toInt()); a.recycle()
    }
    private val r = RectF()
    private val finish = Runnable { stopRecording() }
    private val tick = object : Runnable {
        override fun run() { if (recording) { invalidate(); postOnAnimation(this) } }
    }

    fun pattern(): LongArray = Vibes.toPattern(allPresses())

    fun reset() {
        removeCallbacks(finish)
        Vibes.stop(context)
        presses.clear(); recording = false; done = false; pressStart = -1L
        invalidate(); onChange()
    }

    /** Ends the recording now (also when the dialog closes). */
    fun stopRecording() {
        if (!recording) return
        removeCallbacks(finish)
        if (pressStart >= 0) { presses += pressStart to minOf(now(), Vibes.MAX_MS); pressStart = -1L }
        Vibes.stop(context)
        recording = false
        done = presses.isNotEmpty()
        invalidate(); onChange()
    }

    private fun now() = SystemClock.uptimeMillis() - t0
    private fun allPresses() = if (pressStart >= 0) presses + (pressStart to minOf(now(), Vibes.MAX_MS)) else presses

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (done) { Vibes.preview(context, pattern()); return true }
                if (!recording) {
                    t0 = SystemClock.uptimeMillis(); recording = true
                    postDelayed(finish, Vibes.MAX_MS); postOnAnimation(tick)
                }
                pressStart = now()
                val left = Vibes.MAX_MS - pressStart
                if (left > 0) try {
                    Vibes.vibrator(context)?.vibrate(VibrationEffect.createOneShot(left, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {}
                onChange()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (recording && pressStart >= 0) {
                    presses += pressStart to minOf(now(), Vibes.MAX_MS); pressStart = -1L
                    Vibes.stop(context)
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDetachedFromWindow() { stopRecording(); super.onDetachedFromWindow() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (170 * dp).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h); c.drawRoundRect(r, 18 * dp, 18 * dp, bg)
        // Rhythm along the bottom: the whole strip is MAX_MS.
        val pad = 16 * dp; val top = h - 40 * dp; val bottom = h - 18 * dp
        r.set(pad, top, w - pad, bottom); c.drawRoundRect(r, 6 * dp, 6 * dp, track)
        val span = w - 2 * pad
        for ((s, e) in allPresses()) {
            r.set(pad + span * s / Vibes.MAX_MS, top, pad + span * e / Vibes.MAX_MS, bottom)
            if (r.width() < 3 * dp) r.right = r.left + 3 * dp
            c.drawRoundRect(r, 4 * dp, 4 * dp, bar)
        }
        if (recording) {
            val x = pad + span * minOf(now(), Vibes.MAX_MS) / Vibes.MAX_MS
            c.drawRect(x - 1 * dp, top - 6 * dp, x + 1 * dp, bottom + 6 * dp, bar)
        }
        val label = context.getString(when {
            done -> R.string.vib_rec_play
            recording -> R.string.vib_rec_recording
            else -> R.string.vib_rec_pad
        })
        c.drawText(label, w / 2, (top - 4 * dp) / 2 + text.textSize / 3, text)
    }
}

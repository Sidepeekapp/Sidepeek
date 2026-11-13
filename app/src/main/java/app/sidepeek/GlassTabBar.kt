package app.sidepeek

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Floating "liquid glass" tab bar: a frosted pill with a light edge, and an active pill that
 * springs to the tapped tab and stretches on the way. Replaces the bottom navigation in
 * [MainActivity].
 *
 * Android 13+ ("liquid"): the backdrop is drawn through an AGSL shader that bends it at the
 * rounded edge (refraction, with a touch of colour fringing), only lightly blurred, and the
 * selected tab is a clear lens that magnifies what is behind it. The lens can be dragged between
 * tabs. Android 12 keeps the frosted blur below; older versions show plain tinted glass.
 *
 * Settings (defaults match the reference design):
 *  - [blur] 20 dp (4–40) with saturate(180 %). Android has no backdrop-filter, so on Android 12+
 *    the views listed in [backdrop] are redrawn into a RenderNode with a blur RenderEffect and
 *    shown through the glass. Older versions show the plain tinted glass.
 *  - [glassOpacity] 12 % (4–40) white fill. Light mode uses min(60 %, opacity + 23 %) and dark text.
 *  - [shine] 25 % (0–60): a soft highlight fixed at the top centre (phones have no hover pointer
 *    for it to follow).
 *
 * Motion: the pill moves with a spring (stiffness 400, damping 30, mass 1) and stretches
 * scaleX 1 → 1.15 → 1 over 450 ms with cubic-bezier(.25,.1,.25,1); a pressed tab scales to 0.92
 * with the same spring. With animations turned off in system settings, everything jumps to the
 * end state.
 */
class GlassTabBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    data class Tab(val iconRes: Int, val label: String)

    var blur: Float = 20f
        set(v) { field = v.coerceIn(4f, 40f); effect = null; invalidate() }

    /** One layer of what is behind the bar: [view] drawn where it is on screen, clipped to [clip]. */
    data class Layer(val view: View, val clip: View? = null)

    /** What the glass blurs (Android 12+), back to front, over [backdropColor]. */
    var backdrop: List<Layer> = emptyList()
    var backdropColor: Int = Color.BLACK
    var glassOpacity: Int = 12
        set(v) { field = v.coerceIn(4, 40); invalidate() }
    var shine: Int = 25
        set(v) { field = v.coerceIn(0, 60); invalidate() }

    /** Called with the tapped tab's index when it is not the selected one. */
    var onTabSelected: ((Int) -> Unit)? = null
    /** Called when the selected tab is tapped again. */
    var onTabReselected: ((Int) -> Unit)? = null

    var selectedIndex = 0
        private set

    private val dp = resources.displayMetrics.density
    private fun px(v: Float) = v * dp

    // Reference sizes: buttons 74×54, bar padding 4 (p-1), fully rounded.
    private val itemW = px(74f).toInt()
    private val itemH = px(54f).toInt()
    private val inner = px(4f).toInt()
    /** Room around the glass for its shadow (0 8 32), so it is not clipped. */
    private val shadowRoom = px(SHADOW_ROOM_DP).toInt()

    private val items = ArrayList<View>()
    private val glass = RectF()
    private val glassPath = Path()
    private val pill = RectF()
    private val pillPath = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px(1f) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px(1f) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    // Backdrop blur (API 31+).
    private var node: Any? = null          // android.graphics.RenderNode
    private var effect: Any? = null        // android.graphics.RenderEffect for the current blur
    private val myLoc = IntArray(2)
    private val loc = IntArray(2)

    /** Pill position in tab units (0 = first tab), and its horizontal stretch. */
    private var pillPos = 0f
    private var pillScaleX = 1f
    private var moveAnim: ValueAnimator? = null
    /** 0 → 1 while the lens is held and dragged: it grows slightly. */
    private var lift = 0f
    private var liftAnim: ValueAnimator? = null
    private var dragging = false
    private var downX = 0f
    private var lastX = 0f
    private var lastT = 0L
    private var dragTab = 0
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var shader: Any? = null        // android.graphics.RuntimeShader (API 33+)
    /** Set if the shader could not be used; the bar then keeps the Android 12 frosted blur. */
    private var liquidFailed = false
    private val liquid get() = Build.VERSION.SDK_INT >= 33 && !liquidFailed
    private var stretchAnim: ValueAnimator? = null

    private val light get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
    private val textColor get() = if (light) 0xFF1D1D1F.toInt() else Color.WHITE
    /** Selected tab: Sidepeek green (lighter in dark mode); the others at 60 %. */
    private val activeColor get() = androidx.core.content.ContextCompat.getColor(context, R.color.section_text)
    private val inactiveColor get() = (textColor and 0x00FFFFFF) or (0x99 shl 24)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setPadding(shadowRoom + inner, shadowRoom + inner, shadowRoom + inner, shadowRoom + inner)
        // Shadow layers on shapes need software drawing before Android 9.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(1, items.size, false))
            }
        })
    }

    fun setTabs(tabs: List<Tab>) {
        removeAllViews(); items.clear()
        tabs.forEachIndexed { i, t -> items += makeItem(i, t).also { addView(it, LayoutParams(itemW, itemH)) } }
        applySelectedState()
    }

    /** Selects a tab from code (no listener call). Animates only when [animate] and motion is on. */
    fun select(index: Int, animate: Boolean = false) {
        if (index !in items.indices) return
        val from = pillPos
        selectedIndex = index
        applySelectedState()
        if (animate && motionOn()) {
            moveAnim?.cancel()
            moveAnim = springAnimator { p -> pillPos = from + (index - from) * p; invalidate() }.also { it.start() }
            stretchAnim?.cancel()
            stretchAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 450
                interpolator = null
                addUpdateListener {
                    val f = it.animatedFraction
                    pillScaleX = if (f < 0.5f) 1f + 0.15f * EASE.getInterpolation(f / 0.5f)
                        else 1f + 0.15f * (1f - EASE.getInterpolation((f - 0.5f) / 0.5f))
                    invalidate()
                }
                start()
            }
        } else {
            moveAnim?.cancel(); stretchAnim?.cancel()
            pillPos = index.toFloat(); pillScaleX = 1f
            invalidate()
        }
    }

    private fun makeItem(index: Int, tab: Tab): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = tab.label
        // Visible keyboard focus: a ring inside the tab.
        foreground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                cornerRadius = px(27f); setStroke(px(2f).toInt(), if (light) 0xFF1D1D1F.toInt() else Color.WHITE)
            })
        }
        addView(ImageView(context).apply {
            setImageResource(tab.iconRes)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(px(22f).toInt(), px(22f).toInt()))
        addView(TextView(context).apply {
            text = tab.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(2f).toInt() })
        setOnClickListener {
            if (index == selectedIndex) onTabReselected?.invoke(index)
            else { select(index, animate = true); onTabSelected?.invoke(index) }
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressScale(v, 0.92f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressScale(v, 1f)
            }
            false
        }
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.roleDescription = context.getString(R.string.tab_role)
                info.isSelected = index == selectedIndex
                info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(0, 1, index, 1, false, index == selectedIndex))
            }
        })
    }

    private fun applySelectedState() {
        items.forEachIndexed { i, v ->
            v.isSelected = i == selectedIndex
            val c = if (i == selectedIndex) activeColor else inactiveColor
            val g = v as LinearLayout
            (g.getChildAt(0) as ImageView).apply {
                setColorFilter(c)
                // Seen through the lens, the selected icon looks a little bigger.
                val s = if (liquid && i == selectedIndex) 1.12f else 1f
                if (motionOn() && isLaidOut) animate().scaleX(s).scaleY(s).setDuration(250).start() else { scaleX = s; scaleY = s }
            }
            (g.getChildAt(1) as TextView).apply {
                setTextColor(c)
                // text-shadow 0 1px 2px rgba(0,0,0,.18) on dark; none on light.
                if (light) setShadowLayer(0f, 0f, 0f, 0) else setShadowLayer(px(1f), 0f, px(1f), 0x2E000000)
            }
        }
    }

    private fun pressScale(v: View, to: Float) {
        if (!motionOn()) { v.scaleX = to; v.scaleY = to; return }
        val from = v.scaleX
        (v.getTag(R.id.glassTabPress) as? ValueAnimator)?.cancel()
        v.setTag(R.id.glassTabPress, springAnimator { p -> val s = from + (to - from) * p; v.scaleX = s; v.scaleY = s }.also { it.start() })
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        applySelectedState(); invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        glass.set(shadowRoom.toFloat(), shadowRoom.toFloat(), (width - shadowRoom).toFloat(), (height - shadowRoom).toFloat())
        val r = glass.height() / 2f
        glassPath.reset(); glassPath.addRoundRect(glass, r, r, Path.Direction.CW)

        // Shadow 0 8px 32px rgba(0,0,0,.18), only outside the glass (like a CSS box-shadow).
        canvas.save()
        canvas.clipOutPath(glassPath)
        shadow.color = Color.BLACK
        shadow.setShadowLayer(px(16f), 0f, px(8f), 0x2E000000)
        canvas.drawRoundRect(glass, r, r, shadow)
        canvas.restore()

        canvas.save()
        canvas.clipPath(glassPath)
        computePill()
        val drewBackdrop = Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && backdrop.isNotEmpty() && drawBlurredBackdrop(canvas)
        // Glass fill: much thinner when the liquid backdrop shows through, so the glass stays clear.
        val clear = drewBackdrop && liquid
        val a = when {
            // Clear liquid glass: almost no frosting, only a hint of white in light mode.
            clear -> if (light) 0.05f else 0f
            light -> minOf(0.6f, glassOpacity / 100f + 0.23f)
            else -> glassOpacity / 100f
        }
        fill.shader = null
        fill.color = Color.argb((a * 255).toInt(), 255, 255, 255)
        canvas.drawRect(glass, fill)
        // The clear glass keeps only its refraction and the angle-lit rim; the glows and lines
        // below are for the frosted fallback.
        if (clear) { drawPill(canvas); canvas.restore(); super.dispatchDraw(canvas); drawRim(canvas, r); return }
        // Inner glow (inset 0 0 20px rgba(255,255,255,.08)), approximated at the top and bottom.
        glow.shader = LinearGradient(0f, glass.top, 0f, glass.bottom,
            intArrayOf(0x14FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x14FFFFFF), floatArrayOf(0f, 0.37f, 0.63f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(glass, glow)
        // Shine: radial 140px at the top centre, rgba(255,255,255,shine) → transparent at 70 %.
        if (shine > 0) {
            val sr = px(140f)
            glow.shader = RadialGradient(glass.centerX(), glass.top, sr,
                intArrayOf(Color.argb((shine / 100f * 255).toInt(), 255, 255, 255), 0x00FFFFFF), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
            canvas.drawRect(glass, glow)
        }
        // inset 0 1px 0 rgba(255,255,255,.5) and inset 0 -1px 0 rgba(255,255,255,.1).
        line.color = 0x80FFFFFF.toInt()
        canvas.drawLine(glass.left, glass.top + px(0.5f), glass.right, glass.top + px(0.5f), line)
        line.color = 0x1AFFFFFF
        canvas.drawLine(glass.left, glass.bottom - px(0.5f), glass.right, glass.bottom - px(0.5f), line)
        if (liquid) {
            // Thickness: a bright band inside the upper edge, a soft dark one inside the lower edge.
            glow.shader = LinearGradient(0f, glass.top, 0f, glass.bottom,
                intArrayOf(0x33FFFFFF, 0x00FFFFFF, 0x00000000, 0x24000000), floatArrayOf(0f, 0.25f, 0.7f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(glass, glow)
        }

        drawPill(canvas)
        canvas.restore()

        super.dispatchDraw(canvas)   // tab icons and labels above the pill
        drawRim(canvas, r)
    }

    /** The glass edge: angle-lit on Android 13+, else a 135° gradient line. */
    private fun drawRim(canvas: Canvas, r: Float) {
        // Light edge (::before): 1px, 135°, .7 → .05 (40 %) → .05 (60 %) → .4.
        val half = px(0.5f)
        if (liquid && rimHighlight(canvas, glass, r, 0xF2FFFFFF.toInt(), px(1.2f))) return
        // 135° across the whole bar: spread the gradient over its full diagonal.
        edge.shader = LinearGradient(glass.left, glass.top, glass.right, glass.bottom,
            if (liquid) intArrayOf(0xE6FFFFFF.toInt(), 0x14FFFFFF, 0x14FFFFFF, 0x99FFFFFF.toInt())
            else intArrayOf(0xB3FFFFFF.toInt(), 0x0DFFFFFF, 0x0DFFFFFF, 0x66FFFFFF), floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(glass.left + half, glass.top + half, glass.right - half, glass.bottom - half, r - half, r - half, edge)
    }

    private var rimShader: Any? = null        // android.graphics.RuntimeShader (API 33+)
    private var rimFailed = false
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Angle-lit rim around [rect] (Android 13+). Returns false if it could not be drawn. */
    private fun rimHighlight(canvas: Canvas, rect: RectF, radius: Float, color: Int, width: Float): Boolean {
        if (Build.VERSION.SDK_INT < 33 || rimFailed) return false
        return try {
            val sh = (rimShader as? android.graphics.RuntimeShader) ?: android.graphics.RuntimeShader(RIM_AGSL).also { rimShader = it }
            val h = width / 2
            sh.setFloatUniform("rect", rect.left, rect.top, rect.right, rect.bottom)
            sh.setFloatUniform("radius", radius)
            val rad = Math.toRadians(LIGHT_DEG.toDouble())
            sh.setFloatUniform("light", kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
            sh.setFloatUniform("falloff", 2f)
            sh.setColorUniform("tint", color)
            rimPaint.shader = sh; rimPaint.strokeWidth = width
            canvas.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, radius - h, radius - h, rimPaint)
            true
        } catch (e: Exception) { Diagnostics.note(context, "GLASS rim shader failed: $e"); rimShader = null; rimFailed = true; false }
    }

    /** The selected-tab pill (the lens on Android 13+): position, stretch and lift. */
    private fun computePill() {
        val left = paddingLeft + pillPos * itemW
        val cx = left + itemW / 2f
        val hw = itemW / 2f * pillScaleX + px(4f) * lift
        val grow = px(3f) * lift
        pill.set(cx - hw, paddingTop - grow, cx + hw, paddingTop + itemH + grow)
    }

    private fun drawPill(canvas: Canvas) {
        if (items.isEmpty()) return
        val r = pill.height() / 2f
        pillPath.reset(); pillPath.addRoundRect(pill, r, r, Path.Direction.CW)
        // 0 2px 8px rgba(0,0,0,.08), outside the pill.
        canvas.save()
        canvas.clipOutPath(pillPath)
        shadow.setShadowLayer(px(4f), 0f, px(2f), 0x14000000)
        canvas.drawRoundRect(pill, r, r, shadow)
        canvas.restore()
        if (liquid && shader != null) {
            // A clear lens: almost no fill, a faint green tint, a bright rim fading to green below.
            fill.color = 0x0AFFFFFF
            canvas.drawRoundRect(pill, r, r, fill)
            fill.color = 0x1416A34A
            canvas.drawRoundRect(pill, r, r, fill)
            val h = px(0.75f)
            // A soft green rim below, then the angle-lit highlight on top of it.
            edge.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
                intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x994ADE80.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(pill.left + h, pill.top + h, pill.right - h, pill.bottom - h, r - h, r - h, edge)
            if (!rimHighlight(canvas, pill, r, 0xE6FFFFFF.toInt(), px(1.1f))) {
                edge.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
                    intArrayOf(0xCCFFFFFF.toInt(), 0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(pill.left + h, pill.top + h, pill.right - h, pill.bottom - h, r - h, r - h, edge)
            }
            return
        }
        // Fill rgba(255,255,255,.22) with a Sidepeek-green tint, and inset 0 1px 0 rgba(255,255,255,.55).
        fill.color = 0x38FFFFFF
        canvas.drawRoundRect(pill, r, r, fill)
        fill.color = androidx.core.content.ContextCompat.getColor(context, R.color.brand_tint)
        canvas.drawRoundRect(pill, r, r, fill)
        canvas.save()
        canvas.clipPath(pillPath)
        line.color = 0x8CFFFFFF.toInt()
        canvas.drawLine(pill.left, pill.top + px(0.5f), pill.right, pill.top + px(0.5f), line)
        canvas.restore()
    }

    /**
     * Draws the [backdrop] layers that are behind the glass into a RenderNode with a blur +
     * saturate RenderEffect, then shows it clipped to the glass. Returns false if it could not.
     */
    @android.annotation.TargetApi(31)
    private fun drawBlurredBackdrop(canvas: Canvas): Boolean = try {
        val radius = if (liquid) px(LIQUID_BLUR_DP) else px(blur)
        // Record a margin around the glass so its edges blur (and bend in) real content.
        val m = maxOf(radius * 2, px(REFRACT_DP) + px(4f))
        val ox = glass.left - m; val oy = glass.top - m
        val fx = if (liquid) liquidEffect(radius, -ox, -oy) else (effect as? android.graphics.RenderEffect) ?: run {
            val sat = android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(sat),
                android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }.also { effect = it }
        val rn = (node as? android.graphics.RenderNode) ?: android.graphics.RenderNode("glassBackdrop").also { node = it }
        rn.setPosition(0, 0, (glass.width() + 2 * m).toInt(), (glass.height() + 2 * m).toInt())
        rn.setRenderEffect(fx)
        val rc = rn.beginRecording()
        try {
            rc.translate(-ox, -oy)
            rc.drawColor(backdropColor)
            getLocationInWindow(myLoc)
            for (layer in backdrop) {
                val v = layer.view
                if (v.visibility != VISIBLE || v.width == 0 || v.height == 0) continue
                rc.save()
                layer.clip?.let { c ->
                    c.getLocationInWindow(loc)
                    val cx = (loc[0] - myLoc[0]).toFloat(); val cy = (loc[1] - myLoc[1]).toFloat()
                    rc.clipRect(cx, cy, cx + c.width, cy + c.height)
                }
                v.getLocationInWindow(loc)
                rc.translate((loc[0] - myLoc[0]).toFloat(), (loc[1] - myLoc[1]).toFloat())
                v.draw(rc)
                rc.restore()
            }
        } finally { rn.endRecording() }
        canvas.save()
        canvas.translate(ox, oy)
        canvas.drawRenderNode(rn)
        canvas.restore()
        true
    } catch (e: Exception) {
        if (liquid) { Diagnostics.note(context, "GLASS liquid shader failed: $e"); liquidFailed = true; shader = null; post { invalidate() } }
        false
    }

    /**
     * Light blur, then the refraction shader. Uniforms are in the RenderNode's coordinates, which
     * are this view's shifted by ([dx], [dy]). Built every frame because the lens moves.
     */
    @android.annotation.TargetApi(33)
    private fun liquidEffect(radius: Float, dx: Float, dy: Float): android.graphics.RenderEffect {
        val sh = (shader as? android.graphics.RuntimeShader) ?: android.graphics.RuntimeShader(LIQUID_AGSL).also { shader = it }
        sh.setFloatUniform("glass", glass.left + dx, glass.top + dy, glass.right + dx, glass.bottom + dy)
        sh.setFloatUniform("gRadius", glass.height() / 2f)
        sh.setFloatUniform("lens", pill.left + dx, pill.top + dy, pill.right + dx, pill.bottom + dy)
        sh.setFloatUniform("lRadius", pill.height() / 2f)
        sh.setFloatUniform("bezel", px(BEZEL_DP))
        sh.setFloatUniform("bend", px(REFRACT_DP))
        sh.setFloatUniform("lensMag", 1.22f + 0.08f * lift)
        sh.setFloatUniform("lensOn", if (items.isEmpty()) 0f else 1f)
        sh.setFloatUniform("depth", DOME)
        sh.setFloatUniform("spread", SPREAD)
        sh.setFloatUniform("saturation", if (light) 1.35f else 1.5f)
        sh.setFloatUniform("lift", lift)
        val bend = android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content")
        return if (radius <= 0f) bend
        else android.graphics.RenderEffect.createChainEffect(bend,
            android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    // ---- Drag the lens between tabs -------------------------------------------------------

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; dragging = false }
            MotionEvent.ACTION_MOVE -> if (!dragging && items.size > 1 && abs(e.x - downX) > slop) {
                startDrag(e); return true
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> dragTo(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun startDrag(e: MotionEvent) {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        moveAnim?.cancel(); stretchAnim?.cancel()
        lastX = e.x; lastT = e.eventTime
        dragTab = selectedIndex
        animateLift(1f)
        dragTo(e)
    }

    private fun dragTo(e: MotionEvent) {
        pillPos = ((e.x - paddingLeft - itemW / 2f) / itemW).coerceIn(0f, (items.size - 1).toFloat())
        // Stretch with speed, like a drop of liquid being pulled.
        val dt = (e.eventTime - lastT).coerceAtLeast(1L)
        val v = abs(e.x - lastX) / dt / dp          // dp per ms
        pillScaleX = 1f + minOf(0.22f, v * 0.12f)
        lastX = e.x; lastT = e.eventTime
        val near = pillPos.roundToInt()
        if (near != dragTab) { dragTab = near; performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
        invalidate()
    }

    private fun endDrag() {
        dragging = false
        animateLift(0f)
        val idx = pillPos.roundToInt().coerceIn(0, items.size - 1)
        val changed = idx != selectedIndex
        select(idx, animate = true)
        if (changed) onTabSelected?.invoke(idx)
    }

    private fun animateLift(to: Float) {
        liftAnim?.cancel()
        if (!motionOn()) { lift = to; invalidate(); return }
        liftAnim = ValueAnimator.ofFloat(lift, to).apply {
            duration = 180
            addUpdateListener { lift = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun motionOn() = ValueAnimator.areAnimatorsEnabled()

    /**
     * Spring with stiffness 400, damping 30, mass 1 (framer-motion's spring), from 0 to 1:
     * ω0 = 20 rad/s, ζ = 0.75, so it overshoots slightly and settles within ~0.4 s.
     */
    private fun springAnimator(update: (Float) -> Unit) = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SPRING_MS
        interpolator = null
        addUpdateListener { update(springAt(it.animatedFraction * SPRING_MS / 1000f)) }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { update(1f) }
        })
    }

    companion object {
        /** Room around the glass for its shadow; the glass is inset by this much in the view. */
        const val SHADOW_ROOM_DP = 20f
        private const val LIQUID_BLUR_DP = 0f
        /** Wider, deeper edge refraction for the clear look (was 14 / 16). */
        private const val REFRACT_DP = 22f
        private const val BEZEL_DP = 24f
        /** How much the edge normal leans outward from the centre (0 = flat slab). */
        private const val DOME = 0.35f
        /** Colour fringe width, as a share of the bend. */
        private const val SPREAD = 0.22f
        /** Light direction for the rim highlight: from the upper left. */
        private const val LIGHT_DEG = -135f

        /**
         * Rim highlight: brightest where the edge faces the light (and the opposite edge, as light
         * passing through the glass), fading to nothing where the edge runs along the light.
         */
        private const val RIM_AGSL = """
            uniform float4 rect;
            uniform float radius;
            uniform float2 light;
            uniform float falloff;
            layout(color) uniform half4 tint;

            float sdRR(float2 p, float4 r, float rad) {
                float2 c = (r.xy + r.zw) * 0.5;
                float2 h = (r.zw - r.xy) * 0.5;
                float2 q = abs(p - c) - h + rad;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
            }
            half4 main(float2 p) {
                float2 g = float2(sdRR(p + float2(1.0, 0.0), rect, radius) - sdRR(p - float2(1.0, 0.0), rect, radius),
                                  sdRR(p + float2(0.0, 1.0), rect, radius) - sdRR(p - float2(0.0, 1.0), rect, radius));
                float l = length(g);
                float2 n = l > 0.0001 ? g / l : float2(0.0);
                float k = pow(abs(dot(n, light)), falloff);
                return tint * half(0.18 + 0.82 * k);
            }
        """

        /**
         * Refraction through a rounded slab of glass. Inside the glass, within bezel of its edge,
         * each pixel samples the content further out along the edge normal (a circular bevel
         * profile), so what is around the bar bends into its rim. Inside the lens the content is
         * magnified about its centre and bent at its own rim. Red and blue are sampled slightly
         * apart along the bend for a little colour fringing, and colours are saturated.
         */
        private const val LIQUID_AGSL = """
            uniform shader content;
            uniform float4 glass;
            uniform float gRadius;
            uniform float4 lens;
            uniform float lRadius;
            uniform float bezel;
            uniform float bend;
            uniform float lensMag;
            uniform float lensOn;
            uniform float depth;
            uniform float spread;
            uniform float saturation;
            uniform float lift;

            float sdRR(float2 p, float4 r, float rad) {
                float2 c = (r.xy + r.zw) * 0.5;
                float2 h = (r.zw - r.xy) * 0.5;
                float2 q = abs(p - c) - h + rad;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
            }
            float2 nrm(float2 p, float4 r, float rad) {
                float2 g = float2(sdRR(p + float2(1.0, 0.0), r, rad) - sdRR(p - float2(1.0, 0.0), r, rad),
                                  sdRR(p + float2(0.0, 1.0), r, rad) - sdRR(p - float2(0.0, 1.0), r, rad));
                float l = length(g);
                return l > 0.0001 ? g / l : float2(0.0);
            }
            // Edge normal tilted towards the centre's outward direction: a slightly domed slab.
            float2 domed(float2 p, float4 r, float rad, float k) {
                float2 c = (r.xy + r.zw) * 0.5;
                float2 h = (r.zw - r.xy) * 0.5;
                float2 o = (p - c) / max(h, float2(1.0));
                float2 n = nrm(p, r, rad) + k * o;
                float l = length(n);
                return l > 0.0001 ? n / l : float2(0.0);
            }
            float bevel(float d, float w) {
                float t = clamp(1.0 + d / w, 0.0, 1.0);
                return 1.0 - sqrt(1.0 - t * t);
            }
            // One step of a seven-colour spectrum, i from 0 (red) to 6 (violet).
            float3 hue(int i) {
                if (i == 0) return float3(1.0, 0.0, 0.0);
                if (i == 1) return float3(1.0, 0.5, 0.0);
                if (i == 2) return float3(1.0, 1.0, 0.0);
                if (i == 3) return float3(0.0, 1.0, 0.0);
                if (i == 4) return float3(0.0, 1.0, 1.0);
                if (i == 5) return float3(0.0, 0.0, 1.0);
                return float3(0.5, 0.0, 1.0);
            }
            half4 main(float2 p) {
                float2 q = p;
                float d = sdRR(p, glass, gRadius);
                if (d < 0.0) q += domed(p, glass, gRadius, depth) * bend * bevel(d, bezel);
                float dl = sdRR(p, lens, lRadius);
                if (lensOn > 0.5 && dl < 0.0) {
                    float2 lc = (lens.xy + lens.zw) * 0.5;
                    q = lc + (q - lc) / lensMag;
                    q += domed(p, lens, lRadius, depth) * bend * (0.7 + 0.25 * lift) * bevel(dl, bezel * 0.8);
                }
                // Dispersion: the bent sample is split into seven colours fanned out along the bend.
                float2 fan = (q - p) * spread;
                float3 acc = float3(0.0);
                float3 wsum = float3(0.0);
                float a = 0.0;
                for (int i = 0; i < 7; i++) {
                    float t = 1.0 - float(i) / 3.0;
                    float4 s = float4(content.eval(q + fan * t));
                    float3 w = hue(i);
                    acc += s.rgb * w;
                    wsum += w;
                    a += s.a;
                }
                float3 rgb = acc / wsum;
                float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
                rgb = clamp(mix(float3(l), rgb, saturation), 0.0, 1.0);
                return half4(half3(rgb), half(a / 7.0));
            }
        """
        private const val SPRING_MS = 600L
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        private const val W0 = 20.0                 // sqrt(400 / 1)
        private const val ZETA = 30.0 / (2 * W0)    // 0.75
        private val WD = W0 * sqrt(1 - ZETA * ZETA)

        private fun springAt(t: Float): Float {
            val e = exp(-ZETA * W0 * t)
            return (1 - e * (cos(WD * t) + ZETA * W0 / WD * sin(WD * t))).toFloat()
        }
    }
}

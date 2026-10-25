package app.sidepeek

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A short, silent, looping how-to clip (res/raw) with rounded corners.
 * Width follows the clip's aspect ratio from the given height. Tap opens it full screen.
 */
class GuideVideoView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null,
) : FrameLayout(ctx, attrs), TextureView.SurfaceTextureListener {

    /** Clip size (all guide clips are 480 x 1084). */
    private var aspect = 480f / 1084f
    private var rawRes = 0
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private val texture = TextureView(ctx)
    private val zoomTag: TextView
    /** Full-screen copy: no "tap to enlarge" label, tap closes. */
    var fullScreen = false
        set(v) { field = v; zoomTag.visibility = if (v) View.GONE else View.VISIBLE }

    init {
        val d = resources.displayMetrics.density
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        texture.surfaceTextureListener = this
        zoomTag = TextView(ctx).apply {
            text = "點一下放大"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xB3000000.toInt()); cornerRadius = 99f * d }
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
        }
        addView(zoomTag, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, (8 * d).toInt(), (8 * d).toInt()) })
        setBackgroundColor(0xFF0F1713.toInt())
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, 18f * d)
        }
        clipToOutline = true
        contentDescription = "操作示範影片，點一下放大"
        setOnClickListener { if (fullScreen) (parent as? View)?.performClick() else openFullScreen() }
    }

    /** Show [res] (a raw mp4), or hide the view with 0. */
    fun setVideo(res: Int) {
        if (res == rawRes) { visibility = if (res == 0) View.GONE else View.VISIBLE; return }
        rawRes = res
        releasePlayer()
        visibility = if (res == 0) View.GONE else View.VISIBLE
        if (res != 0) startIfReady()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        var w = (h * aspect).toInt()
        var hh = h
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && w > maxW) { w = maxW; hh = (w / aspect).toInt() }
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY))
    }

    private fun startIfReady() {
        val s = surface ?: return
        if (rawRes == 0 || player != null || !isAttachedToWindow) return
        try {
            player = MediaPlayer().apply {
                setDataSource(context, Uri.parse("android.resource://${context.packageName}/$rawRes"))
                setSurface(s)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener { mp ->
                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                        val a = mp.videoWidth.toFloat() / mp.videoHeight
                        if (a != aspect) { aspect = a; requestLayout() }
                    }
                    mp.start()
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) { releasePlayer() }
    }

    private fun releasePlayer() {
        player?.let { try { it.release() } catch (_: Exception) {} }
        player = null
    }

    fun pause() { try { player?.pause() } catch (_: Exception) {} }
    fun resume() { try { player?.let { if (!it.isPlaying) it.start() } ?: startIfReady() } catch (_: Exception) {} }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startIfReady() }
    override fun onDetachedFromWindow() { releasePlayer(); super.onDetachedFromWindow() }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st); startIfReady() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        releasePlayer(); surface?.release(); surface = null; return true
    }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    /**
     * Full-screen view as an overlay inside the activity's own window. (A Dialog window is
     * not hardware-accelerated on some devices, so its TextureView never got a surface and
     * the enlarged clip stayed black.)
     */
    private fun openFullScreen() {
        val act = context as? androidx.activity.ComponentActivity ?: return
        val root = act.window.decorView as? ViewGroup ?: return
        val d = resources.displayMetrics.density
        val box = FrameLayout(context).apply {
            setBackgroundColor(0xF2000000.toInt())
            isClickable = true
            elevation = 100f * d
        }
        // Capture first: inside apply{} "rawRes" would be the NEW view's own (still 0) field,
        // which hid the enlarged view and left a black screen.
        val clip = rawRes
        val big = GuideVideoView(context).apply { fullScreen = true; setVideo(clip) }
        box.addView(big, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER).apply {
            setMargins((16 * d).toInt(), (64 * d).toInt(), (16 * d).toInt(), (40 * d).toInt())
        })
        box.addView(TextView(context).apply {
            text = "點任意處關閉"; setTextColor(0xCCFFFFFF.toInt()); textSize = 14f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (32 * d).toInt() })
        var back: androidx.activity.OnBackPressedCallback? = null
        fun close() {
            back?.remove(); back = null
            root.removeView(box)
            resume()
        }
        box.setOnClickListener { close() }
        back = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = close()
        }.also { act.onBackPressedDispatcher.addCallback(it) }
        pause()
        root.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.alpha = 0f
        box.animate().alpha(1f).setDuration(150).start()
    }
}

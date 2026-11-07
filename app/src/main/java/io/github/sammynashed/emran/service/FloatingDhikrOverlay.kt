package io.github.sammynashed.emran.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.sammynashed.emran.ui.screens.CardMotion
import io.github.sammynashed.emran.ui.screens.FloatingDhikrCard
import io.github.sammynashed.emran.ui.screens.FloatingDismissTarget
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The card itself, drawn over other apps in its own small window (so the rest of the screen keeps
 * every touch). Drag it anywhere; while it is held an X rises at the bottom, and letting go on the
 * X dismisses it. Left alone, it leaves by itself after the chosen number of seconds.
 *
 * A window the user can see keeps the process at "perceptible" priority, so no service is needed
 * for the few seconds it is up.
 */
object FloatingDhikrOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var session: Session? = null

    val isShowing get() = session != null

    fun show(context: Context, dhikr: FloatingDhikr.Dhikr) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { show(context, dhikr) }; return }
        if (session != null || !FloatingDhikr.canDraw(context)) return
        session = runCatching { Session(context.applicationContext, dhikr).also { it.open() } }
            .onFailure { android.util.Log.e("FloatingDhikr", "overlay failed", it) }.getOrNull()
    }

    fun hide() { session?.close(animated = true) }

    /** A minimal owner so Compose can run in a window that belongs to no activity. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        fun start() {
            saved.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
        }
        fun attach(v: View) {
            v.setViewTreeLifecycleOwner(this); v.setViewTreeSavedStateRegistryOwner(this); v.setViewTreeViewModelStoreOwner(this)
        }
    }

    private class Session(val ctx: Context, val dhikr: FloatingDhikr.Dhikr) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = ctx.resources.displayMetrics
        fun dp(v: Float) = (v * dm.density).roundToInt()
        val owner = Owner()
        val lang = FloatingDhikr.language(ctx)

        // Shared with the Compose card: entrance/exit progress, how "held" it is, how close to the X.
        val motion = CardMotion(
            appear = mutableFloatStateOf(0f), held = mutableFloatStateOf(0f), toX = mutableFloatStateOf(0f),
            remaining = mutableFloatStateOf(1f), leaving = mutableStateOf(false),
        )
        val targetShown = mutableStateOf(false)
        val targetHot = mutableFloatStateOf(0f)

        val pad = dp(14f)                                   // room for the card's soft shadow inside its window
        val cardW = minOf(dm.widthPixels - dp(28f), dp(400f))
        val winW = cardW + 2 * pad

        lateinit var frame: DragFrame
        lateinit var cardParams: WindowManager.LayoutParams
        var target: ComposeView? = null
        var closed = false
        val showMs = FloatingDhikr.showSeconds(ctx) * 1000L
        var shownAt = 0L
        var pausedFor = 0L
        var holdStart = 0L

        private val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        fun open() {
            owner.start()
            val compose = ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    FloatingDhikrCard(dhikr = dhikr, lang = lang, motion = motion, widthPx = cardW, onTap = { openApp() })
                }
            }
            frame = DragFrame(ctx).apply { addView(compose) }
            owner.attach(frame)
            val (sx, sy) = FloatingDhikr.savedPosition(ctx) ?: ((dm.widthPixels - winW) / 2 to statusBar() + dp(10f))
            cardParams = WindowManager.LayoutParams(
                winW, WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = sx.coerceIn(0, maxOf(0, dm.widthPixels - winW)); y = sy.coerceIn(0, dm.heightPixels - dp(160f))
                windowAnimations = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm.addView(frame, cardParams)
            shownAt = System.currentTimeMillis()
            animate(motion.appear.floatValue, 1f, 900) { motion.appear.floatValue = it }
            main.post(tick)
        }

        private val tick = object : Runnable {
            override fun run() {
                if (closed) return
                val held = frame.dragging || frame.touching
                if (held) { if (holdStart == 0L) holdStart = System.currentTimeMillis() }
                else if (holdStart != 0L) { pausedFor += System.currentTimeMillis() - holdStart; holdStart = 0L }
                val elapsed = System.currentTimeMillis() - shownAt - pausedFor - (if (holdStart != 0L) System.currentTimeMillis() - holdStart else 0L)
                motion.remaining.floatValue = (1f - (elapsed - 900f) / showMs).coerceIn(0f, 1f)
                if (!held && elapsed > showMs + 900) close(animated = true) else main.postDelayed(this, 32)
            }
        }

        fun statusBar(): Int {
            val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(28f)
        }

        fun openApp() {
            close(animated = true)
            ctx.startActivity(
                Intent(ctx, io.github.sammynashed.emran.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ROUTE", "dhikr"),
            )
        }

        // ── the X ────────────────────────────────────────────────────────
        val targetSize get() = dp(150f)
        fun targetCenter(): Pair<Float, Float> = dm.widthPixels / 2f to (dm.heightPixels - dp(110f)).toFloat()

        fun showTarget() {
            if (target != null) return
            val v = ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { FloatingDismissTarget(shown = targetShown.value, hot = targetHot.floatValue) }
            }
            owner.attach(v)
            val (cx, cy) = targetCenter()
            val p = WindowManager.LayoutParams(
                targetSize, targetSize, overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (cx - targetSize / 2f).roundToInt(); y = (cy - targetSize / 2f).roundToInt(); windowAnimations = 0
            }
            runCatching { wm.addView(v, p); target = v; targetShown.value = true }
        }

        fun hideTarget() {
            val v = target ?: return
            targetShown.value = false
            main.postDelayed({ runCatching { wm.removeView(v) }; if (target === v) target = null }, 260)
        }

        // ── dragging ────────────────────────────────────────────────────
        fun cardCenter(): Pair<Float, Float> = cardParams.x + winW / 2f to cardParams.y + frame.height / 2f

        fun onDragStart() {
            frame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate(motion.held.floatValue, 1f, 220) { motion.held.floatValue = it }
            showTarget()
        }

        fun onDragMove(x: Int, y: Int) {
            cardParams.x = x.coerceIn(-winW / 2, dm.widthPixels - winW / 2)
            cardParams.y = y.coerceIn(0, dm.heightPixels - dp(80f))
            runCatching { wm.updateViewLayout(frame, cardParams) }
            val (cx, cy) = cardCenter(); val (tx, ty) = targetCenter()
            val d = hypot(cx - tx, cy - ty)
            val hot = (1f - (d - dp(60f)) / dp(150f)).coerceIn(0f, 1f)
            if (hot >= 0.99f && targetHot.floatValue < 0.99f) frame.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            targetHot.floatValue = hot
            motion.toX.floatValue = hot
        }

        fun onDragEnd() {
            animate(motion.held.floatValue, 0f, 260) { motion.held.floatValue = it }
            if (targetHot.floatValue >= 0.99f) {
                // Into the X: the card slides onto it and is gone.
                frame.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf { Build.VERSION.SDK_INT >= 30 } ?: HapticFeedbackConstants.VIRTUAL_KEY)
                val (tx, ty) = targetCenter()
                val sx = cardParams.x; val sy = cardParams.y
                val ex = (tx - winW / 2f).roundToInt(); val ey = (ty - frame.height / 2f).roundToInt()
                animate(0f, 1f, 260) { f ->
                    cardParams.x = (sx + (ex - sx) * f).roundToInt(); cardParams.y = (sy + (ey - sy) * f).roundToInt()
                    runCatching { wm.updateViewLayout(frame, cardParams) }
                    motion.toX.floatValue = 1f
                    motion.appear.floatValue = 1f - f
                }
                main.postDelayed({ hideTarget(); close(animated = false) }, 280)
            } else {
                hideTarget()
                targetHot.floatValue = 0f; motion.toX.floatValue = 0f
                // Keep it on screen, then remember the spot for next time.
                val x = cardParams.x.coerceIn(0, maxOf(0, dm.widthPixels - winW))
                val y = cardParams.y.coerceIn(statusBar(), dm.heightPixels - frame.height - dp(24f))
                val sx = cardParams.x; val sy = cardParams.y
                animate(0f, 1f, 360, OvershootInterpolator(1.1f)) { f ->
                    cardParams.x = (sx + (x - sx) * f).roundToInt(); cardParams.y = (sy + (y - sy) * f).roundToInt()
                    runCatching { wm.updateViewLayout(frame, cardParams) }
                }
                FloatingDhikr.savePosition(ctx, x, y)
            }
        }

        fun close(animated: Boolean) {
            if (closed) return
            closed = true
            main.removeCallbacks(tick)
            val finish = {
                hideTarget()
                runCatching { wm.removeView(frame) }
                owner.stop()
                if (session === this) session = null
            }
            if (animated) {
                motion.leaving.value = true
                animate(motion.appear.floatValue, 0f, 520) { motion.appear.floatValue = it }
                main.postDelayed(finish, 560)
            } else finish()
        }

        fun animate(from: Float, to: Float, ms: Long, interp: android.animation.TimeInterpolator? = null, onFrame: (Float) -> Unit) {
            ValueAnimator.ofFloat(from, to).apply {
                duration = ms
                interp?.let { interpolator = it }
                addUpdateListener { onFrame(it.animatedValue as Float) }
                start()
            }
        }

        /** Passes taps to the card; once a finger really moves, takes over and drags the whole window by raw screen coordinates. */
        inner class DragFrame(c: Context) : FrameLayout(c) {
            var dragging = false
            var touching = false
            private val slop = ViewConfiguration.get(c).scaledTouchSlop
            private var downRawX = 0f; private var downRawY = 0f
            private var startX = 0; private var startY = 0
            private val longPress = Runnable { if (touching && !dragging) { dragging = true; onDragStart() } }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touching = true; dragging = false
                        downRawX = ev.rawX; downRawY = ev.rawY; startX = cardParams.x; startY = cardParams.y
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> if (!dragging && hypot(ev.rawX - downRawX, ev.rawY - downRawY) > slop) {
                        removeCallbacks(longPress); dragging = true; onDragStart(); return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPress); touching = false
                        // A long press that never moved: the release still has to end the drag.
                        if (dragging) { dragging = false; onDragEnd(); return true }
                    }
                }
                return dragging
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> if (dragging) onDragMove(startX + (ev.rawX - downRawX).roundToInt(), startY + (ev.rawY - downRawY).roundToInt())
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPress)
                        touching = false
                        if (dragging) { dragging = false; onDragEnd() }
                    }
                }
                return true
            }
        }
    }
}

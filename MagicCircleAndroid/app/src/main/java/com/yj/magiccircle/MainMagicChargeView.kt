package com.yj.magiccircle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowInsets

/** The same native scene is used by the gallery preview and charging overlay. */
class MainMagicChargeView @JvmOverloads constructor(
    context: Context,
    val themeId: String = "native-N01"
) : View(context) {
    init { require(ThemeSelection.isNative(themeId)) { "Unsupported native theme" } }
    private val n01 = themeId == "native-N01"
    private val background = if (n01) CosmicBackgroundRenderer() else null
    private val circle = if (n01) MagicCircleRenderer() else null
    private val panel = if (n01) ChargeStatusPanelRenderer() else null
    private var artwork = if (n01) null else WallpaperArtwork(themeId)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xb8050a10.toInt() }
    private val chargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xfff5e4bc.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 20f * resources.displayMetrics.density * resources.configuration.fontScale
    }
    private var chargeText = themeId.removePrefix("ref-") + " · —"
    private var started = false
    private var receiverRegistered = false
    private var startedAt = 0L
    private var animate = true
    private var leftInset = 0
    private var topInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var frame = SanctuaryLayout.fit(0, 0)
    private val nextFrame = Runnable { invalidate() }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    fun start() {
        if (started) return
        started = true
        startedAt = SystemClock.elapsedRealtime()
        animate = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (_: RuntimeException) { true }
        try {
            if (!n01) {
                if (artwork == null) artwork = WallpaperArtwork(themeId)
                if (width > 0 && height > 0) artwork?.prepare(width, height)
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val battery = if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else context.registerReceiver(batteryReceiver, filter)
            receiverRegistered = true
            updateBattery(battery)
            invalidate()
        } catch (error: RuntimeException) {
            stop()
            throw error
        }
    }

    fun stop() {
        started = false
        removeCallbacks(nextFrame)
        if (receiverRegistered) {
            receiverRegistered = false
            try { context.unregisterReceiver(batteryReceiver) } catch (_: IllegalArgumentException) { }
        }
        if (!n01) {
            artwork?.close()
            if (themeId == "ref-W03") artwork = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (started) invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && started) invalidate() else removeCallbacks(nextFrame)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && started) invalidate() else removeCallbacks(nextFrame)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            leftInset = safe.left; topInset = safe.top; rightInset = safe.right; bottomInset = safe.bottom
        } else {
            @Suppress("DEPRECATION")
            leftInset = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            rightInset = insets.systemWindowInsetRight
            @Suppress("DEPRECATION")
            bottomInset = insets.systemWindowInsetBottom
        }
        updateFrame()
        invalidate()
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (n01) {
            background?.prepare(w, h)
            updateFrame()
        } else if (started) artwork?.prepare(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = if (started && animate) SystemClock.elapsedRealtime() - startedAt else 0L
        if (n01) {
            background?.draw(canvas, elapsed, false)
            val save = canvas.save()
            canvas.translate(leftInset + frame.left, topInset + frame.top)
            canvas.scale(frame.scale, frame.scale)
            circle?.draw(canvas, SanctuaryLayout.goldAngle(elapsed), SanctuaryLayout.blueAngle(elapsed))
            panel?.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            artwork?.draw(canvas, elapsed, animate)
            val density = resources.displayMetrics.density
            val center = (leftInset + width - rightInset) / 2f
            val bottom = height - bottomInset - 20f * density
            val top = bottom - 48f * density
            canvas.drawRoundRect(center - 104f * density, top, center + 104f * density,
                bottom + 8f * density, 18f * density, 18f * density, badgePaint)
            canvas.drawText(chargeText, center, bottom - 10f * density, chargePaint)
        }
        if (started && animate && isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE) {
            removeCallbacks(nextFrame)
            postOnAnimation(nextFrame)
        }
    }

    private fun updateBattery(battery: Intent?) {
        fun value(key: String) = if (battery?.hasExtra(key) == true) battery.getIntExtra(key, 0) else null
        val snapshot = ChargeSnapshot.fromRaw(
            value(BatteryManager.EXTRA_LEVEL), value(BatteryManager.EXTRA_SCALE),
            value(BatteryManager.EXTRA_TEMPERATURE), value(BatteryManager.EXTRA_HEALTH),
            value(BatteryManager.EXTRA_STATUS), value(BatteryManager.EXTRA_PLUGGED))
        if (n01) {
            panel?.update(snapshot, WebViews.selectedLanguage(context), resources.configuration.fontScale)
            contentDescription = panel?.description()
        } else {
            chargeText = themeId.removePrefix("ref-") + " · " + (snapshot.percent?.let { "$it%" } ?: "—")
            contentDescription = chargeText
        }
        invalidate()
    }

    private fun updateFrame() {
        frame = SanctuaryLayout.fit(width - leftInset - rightInset, height - topInset - bottomInset)
    }
}

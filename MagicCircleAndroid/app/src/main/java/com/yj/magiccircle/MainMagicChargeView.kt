package com.yj.magiccircle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowInsets

/** The same native scene is used by the gallery preview and charging overlay. */
class MainMagicChargeView(context: Context) : View(context) {
    private val background = CosmicBackgroundRenderer()
    private val circle = MagicCircleRenderer()
    private val panel = ChargeStatusPanelRenderer()
    private var started = false
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
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else context.registerReceiver(batteryReceiver, filter)
        updateBattery(battery)
        invalidate()
    }

    fun stop() {
        if (!started) return
        started = false
        removeCallbacks(nextFrame)
        try { context.unregisterReceiver(batteryReceiver) } catch (_: IllegalArgumentException) { }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (started) invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nextFrame)
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
        background.prepare(w, h)
        updateFrame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = if (started && animate) SystemClock.elapsedRealtime() - startedAt else 0L
        background.draw(canvas, elapsed, false)
        val save = canvas.save()
        canvas.translate(leftInset + frame.left, topInset + frame.top)
        canvas.scale(frame.scale, frame.scale)
        circle.draw(canvas, SanctuaryLayout.goldAngle(elapsed), SanctuaryLayout.blueAngle(elapsed))
        panel.draw(canvas)
        canvas.restoreToCount(save)
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
        panel.update(snapshot, WebViews.selectedLanguage(context), resources.configuration.fontScale)
        contentDescription = panel.description()
        invalidate()
    }

    private fun updateFrame() {
        frame = SanctuaryLayout.fit(width - leftInset - rightInset, height - topInset - bottomInset)
    }
}

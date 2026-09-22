package com.yj.magiccircle

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout

/** Internal debug controls only. Never changes the battery service or saved selection. */
class SanctuaryReviewActivity : Activity() {
    private var language = 0
    private var state = 0
    private var format = 0
    private var font = 1f
    private var forceCanvas = false
    private val languages = arrayOf("ko", "ja", "en")
    private val snapshots = arrayOf(
        ChargeSnapshot(0, 0f, 7, 3, 0), ChargeSnapshot(9, 32.5f, 2, 4, 0),
        ChargeSnapshot(69, 32.5f, 2, 4, 0), ChargeSnapshot(100, 0f, 2, 5, 2),
        ChargeSnapshot(null, null, null, null, null))
    private lateinit var scene: Scene
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsets.CONSUMED
        } else root.fitsSystemWindows = true
        val controls = LinearLayout(this)
        fun button(label: String, action: () -> Unit) {
            controls.addView(Button(this).apply { text = label; textSize = 10f; setPadding(0, 0, 0, 0); setOnClickListener { action(); scene.refresh() } }, LinearLayout.LayoutParams(0, 48 * resources.displayMetrics.density.toInt(), 1f))
        }
        button("KO/JA/EN") { language = (language + 1) % 3 }
        button("0/9/69/100/?") { state = (state + 1) % snapshots.size }
        button("Canvas") { forceCanvas = !forceCanvas }
        button("크기") { format = (format + 1) % 3 }
        button("글자") { font = when (font) { 1f -> 1.3f; 1.3f -> 2f; else -> 1f } }
        root.addView(controls)
        scene = Scene()
        root.addView(scene, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        scene.refresh()
    }
    private inner class Scene : View(this@SanctuaryReviewActivity) {
        private val started = SystemClock.elapsedRealtime()
        private val background = CosmicBackgroundRenderer()
        private val circle = MagicCircleRenderer()
        private val panel = ChargeStatusPanelRenderer()
        private val glyphBounds = Rect()
        private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffff4050.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff40ffdf.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        private var first = true
        private var mode = ""
        private var designWidth = 864
        private var designHeight = 1536
        fun refresh() {
            mode = ""
            designWidth = if (format == 2) 2560 else if (format == 1) 1600 else 864
            designHeight = if (format == 2) 1600 else if (format == 1) 2560 else 1536
            if (width > 0 && height > 0) background.prepare(designWidth, designHeight)
            panel.update(snapshots[state], languages[language], font)
            contentDescription = "${if(forceCanvas) "Canvas" else "AGSL eligible"}; ${languages[language]}; state=$state; format=$format; font=$font; ${panel.description()}"
            Log.i("SanctuaryReview", contentDescription.toString())
            invalidate()
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (w > 0 && h > 0) background.prepare(designWidth, designHeight)
        }
        override fun onDraw(canvas: Canvas) {
            val scale = minOf(width.toFloat() / designWidth, height.toFloat() / designHeight)
            canvas.drawColor(0xff101010.toInt())
            canvas.save()
            canvas.translate((width - designWidth * scale) / 2, (height - designHeight * scale) / 2)
            canvas.scale(scale, scale)
            canvas.clipRect(0, 0, designWidth, designHeight)
            val elapsed = SystemClock.elapsedRealtime() - started
            background.draw(canvas, elapsed, forceCanvas)
            // Debug-only observation: do not add synthetic-state or shader-control APIs to production.
            if (mode.isEmpty()) {
                mode = if (forceCanvas || !canvas.isHardwareAccelerated) "Canvas" else {
                    val shader = background.javaClass.getDeclaredField("texture").apply { isAccessible = true }.get(background)
                    if (shader == null) "Canvas fallback" else "AGSL active"
                }
                Log.i("SanctuaryReview", "renderer=$mode")
            }
            val frame = SanctuaryLayout.fit(designWidth, designHeight)
            canvas.translate(frame.left, frame.top)
            canvas.scale(frame.scale, frame.scale)
            circle.draw(canvas, SanctuaryLayout.goldAngle(elapsed), SanctuaryLayout.blueAngle(elapsed))
            panel.draw(canvas)
            drawGlyphReview(canvas)
            canvas.restore()
            if (first) { Log.i("SanctuaryReview", "firstDrawMs=${SystemClock.elapsedRealtime()-started} hardware=${canvas.isHardwareAccelerated}"); first = false }
            if (isAttachedToWindow && windowVisibility == VISIBLE) postInvalidateOnAnimation()
        }

        private fun drawGlyphReview(canvas: Canvas) {
            val percent = snapshots[state].percent
            val number = percent?.toString() ?: "—"
            val suffix = if (percent == null) "" else "%"
            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 104f * font; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
            val suffixPaint = Paint(numberPaint).apply { textSize = 42f * font }
            val gap = if (suffix.isEmpty()) 0f else 8f
            val groupWidth = numberPaint.measureText(number) + suffixPaint.measureText(suffix) + gap
            val factor = minOf(1f, 214f / groupWidth, 111f / (numberPaint.fontMetrics.descent - numberPaint.fontMetrics.ascent))
            numberPaint.textSize *= factor
            suffixPaint.textSize *= factor
            val numberWidth = numberPaint.measureText(number)
            val width = numberWidth + suffixPaint.measureText(suffix) + gap * factor
            val x = SanctuaryLayout.centeredLeft(width, SanctuaryLayout.CENTER_X)
            numberPaint.getTextBounds(number, 0, number.length, glyphBounds)
            val baseline = SanctuaryLayout.centeredBaseline(glyphBounds.top.toFloat(), glyphBounds.bottom.toFloat(), SanctuaryLayout.CENTER_Y)
            canvas.drawRect(x + glyphBounds.left, baseline + glyphBounds.top, x + glyphBounds.right, baseline + glyphBounds.bottom, boundsPaint)
            if (suffix.isNotEmpty()) {
                suffixPaint.getTextBounds(suffix, 0, suffix.length, glyphBounds)
                val suffixX = x + numberWidth + gap * factor
                canvas.drawRect(suffixX + glyphBounds.left, baseline + glyphBounds.top, suffixX + glyphBounds.right, baseline + glyphBounds.bottom, boundsPaint)
            }
            canvas.drawLine(SanctuaryLayout.CENTER_X - SanctuaryLayout.CORE_RADIUS, SanctuaryLayout.CENTER_Y, SanctuaryLayout.CENTER_X + SanctuaryLayout.CORE_RADIUS, SanctuaryLayout.CENTER_Y, centerPaint)
            canvas.drawLine(SanctuaryLayout.CENTER_X, SanctuaryLayout.CENTER_Y - SanctuaryLayout.CORE_RADIUS, SanctuaryLayout.CENTER_X, SanctuaryLayout.CENTER_Y + SanctuaryLayout.CORE_RADIUS, centerPaint)
        }
    }
}

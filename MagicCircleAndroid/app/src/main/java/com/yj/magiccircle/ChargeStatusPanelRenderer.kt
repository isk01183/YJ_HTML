package com.yj.magiccircle

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.Typeface
import java.util.Locale

/** Text fitting, localization and accessibility strings are rebuilt only with a new snapshot/settings. */
class ChargeStatusPanelRenderer {
    private data class TextRun(val text: String, val x: Float, val baseline: Float, val paint: Paint)
    private val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val runs = ArrayList<TextRun>(12)
    private val decoration = Picture()
    private val batteryDecoration = Picture()
    private var spoken = ""

    init {
        val c = decoration.beginRecording(864, 1536)
        val ink = SanctuaryInk()
        val lines = Path().apply {
            moveTo(194f, 229f); lineTo(670f, 229f)
            moveTo(171f, 1215f); lineTo(693f, 1215f)
            moveTo(306f, 1307f); lineTo(306f, 1430f)
            moveTo(558f, 1307f); lineTo(558f, 1430f)
        }
        ink.glow(c, lines, SanctuaryInk.GOLD, .7f)
        val topHalo = Path().apply { addCircle(432f, 77f, 36f, Path.Direction.CW) }
        ink.stroke(c, topHalo, SanctuaryInk.GOLD, .6f, 175)
        ink.flare(c, 432f, 77f, 35f)
        ink.flare(c, 432f, 229f, 8f)
        ink.flare(c, 432f, 1215f, 9f)
        ink.flare(c, 306f, 1365f, 7f)
        ink.flare(c, 558f, 1365f, 7f)
        for (x in floatArrayOf(194f, 670f)) ink.flare(c, x, 229f, 3f)
        for (x in floatArrayOf(171f, 693f)) ink.flare(c, x, 1215f, 3f)
        val phases = Path().apply { addCircle(432f, 1458f, 13f, Path.Direction.CW) }
        for (i in 0..1) {
            val x = 396f - i * 29f
            phases.addPath(SanctuaryInk.crescent(x, 1458f, 11f - i * 3f))
            val right = SanctuaryInk.crescent(0f, 0f, 11f - i * 3f)
            val matrix = android.graphics.Matrix().apply { setScale(-1f, 1f); postTranslate(864f - x, 1458f) }
            phases.addPath(right, matrix)
        }
        for (i in 0 until 15) {
            phases.addCircle(281f + i * 5f, 1458f, .8f, Path.Direction.CW)
            phases.addCircle(583f - i * 5f, 1458f, .8f, Path.Direction.CW)
        }
        ink.fill(c, phases, SanctuaryInk.GOLD)
        decoration.endRecording()
        update(ChargeSnapshot(null, null, null, null, null), "ko", 1f)
    }

    fun update(snapshot: ChargeSnapshot, language: String, fontScale: Float) {
        val languageIndex = when (language) { "ko" -> 0; "ja" -> 1; else -> 2 }
        val locale = when (languageIndex) { 0 -> Locale.KOREAN; 1 -> Locale.JAPANESE; else -> Locale.ENGLISH }
        val scale = if (fontScale.isFinite()) fontScale.coerceIn(.8f, 3f) else 1f
        fun words(ko: String, ja: String, en: String) = when (languageIndex) { 0 -> ko; 1 -> ja; else -> en }
        val unknown = words("알 수 없음", "不明", "Unknown")
        val title = words("별을 읽는 성역", "星を読む聖域", "Sanctuary of Stars")
        val subtitle = words("지혜는 내일을 비춘다.", "知恵は明日を照らす。", "Wisdom illuminates tomorrow.")
        val footer = words("지혜는 더 밝은 내일을 비춘다.", "知恵はより明るい明日を照らす。", "Wisdom lights a brighter tomorrow.")
        val status = when (snapshot.status) {
            2 -> words("충전 중", "充電中", "Charging")
            3 -> words("방전 중", "放電中", "Discharging")
            4 -> words("충전하지 않음", "充電していません", "Not charging")
            5 -> words("충전 완료", "充電完了", "Fully charged")
            else -> unknown
        }
        val health = when (snapshot.health) {
            2 -> words("양호", "良好", "Good")
            3 -> words("과열", "過熱", "Overheated")
            4 -> words("수명 저하", "劣化", "Dead")
            5 -> words("과전압", "過電圧", "Overvoltage")
            6 -> words("오류", "異常", "Failure")
            7 -> words("저온", "低温", "Cold")
            else -> unknown
        }
        val connection = when (snapshot.plugged) {
            0 -> words("연결되지 않음", "未接続", "Disconnected")
            1 -> words("AC 연결", "AC接続", "AC connected")
            2 -> words("USB 연결", "USB接続", "USB connected")
            4 -> words("무선 충전", "ワイヤレス", "Wireless")
            8 -> words("도크 연결", "ドック接続", "Dock connected")
            else -> unknown
        }
        val temperature = snapshot.temperatureC?.let { String.format(locale, "%.1f°C", it) } ?: "—"
        val tempLabel = words("배터리 온도", "バッテリー温度", "Temperature")
        val healthLabel = words("배터리 상태", "バッテリー状態", "Battery health")
        val connectionLabel = words("연결 방식", "接続方式", "Connection")
        val percentageLabel = words("배터리 잔량", "バッテリー残量", "Battery level")
        runs.clear()
        addText(title, 432f, 145f, 575f, 51f * scale, 57f)
        addText(subtitle, 432f, 252f, 530f, 25f * scale, 33f)
        addText(footer, 432f, 1244f, 590f, 27f * scale, 36f)

        addBattery(snapshot.percent, if (snapshot.plugged == 0) connection else status, scale)
        addText(temperature, 174f, 1327f, 224f, 40f * scale, 49f)
        addText(health, 432f, 1327f, 220f, 38f * scale, 49f)
        addText(connection, 690f, 1327f, 224f, 34f * scale, 49f)
        addText(tempLabel, 174f, 1384f, 224f, 23f * scale, 34f, false)
        addText(healthLabel, 432f, 1384f, 220f, 23f * scale, 34f, false)
        addText(connectionLabel, 690f, 1384f, 224f, 23f * scale, 34f, false)
        spoken = "$title. $percentageLabel: ${snapshot.percent?.let { "$it%" } ?: unknown}. $status. $tempLabel: ${if (snapshot.temperatureC == null) unknown else temperature}. $healthLabel: $health. $connectionLabel: $connection."
    }

    fun draw(canvas: Canvas) {
        canvas.drawPicture(decoration)
        canvas.drawPicture(batteryDecoration)
        for (i in runs.indices) {
            val run = runs[i]
            canvas.drawText(run.text, run.x, run.baseline, run.paint)
        }
    }

    fun description(): String = spoken

    private fun textPaint(size: Float, useSerif: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (useSerif) 0xfffff4d8.toInt() else 0xffded8c5.toInt()
        textSize = size
        typeface = if (useSerif) serif else sans
    }

    private fun addBattery(percent: Int?, status: String, scale: Float) {
        val number = percent?.toString() ?: "—"
        val suffix = if (percent == null) "" else "%"
        val numberPaint = textPaint(104f * scale, true)
        val suffixPaint = textPaint(42f * scale, true)
        val gap = if (suffix.isEmpty()) 0f else 8f
        val groupWidth = numberPaint.measureText(number) + suffixPaint.measureText(suffix) + gap
        val factor = minOf(1f, 214f / groupWidth, 111f / (numberPaint.fontMetrics.descent - numberPaint.fontMetrics.ascent))
        numberPaint.textSize *= factor
        suffixPaint.textSize *= factor
        val numberWidth = numberPaint.measureText(number)
        val width = numberWidth + suffixPaint.measureText(suffix) + gap * factor
        val x = SanctuaryLayout.centeredLeft(width, SanctuaryLayout.CENTER_X)
        val bounds = Rect().also { numberPaint.getTextBounds(number, 0, number.length, it) }
        val baseline = SanctuaryLayout.centeredBaseline(bounds.top.toFloat(), bounds.bottom.toFloat(), SanctuaryLayout.CENTER_Y)
        runs.add(TextRun(number, x, baseline, numberPaint))
        runs.add(TextRun(suffix, x + numberWidth + gap * factor, baseline, suffixPaint))

        val dividerY = maxOf(776f, baseline + bounds.bottom + 8f)
        val canvas = batteryDecoration.beginRecording(864, 1536)
        SanctuaryInk().glow(canvas, Path().apply { moveTo(346f, dividerY); lineTo(518f, dividerY) }, SanctuaryInk.GOLD, .7f)
        batteryDecoration.endRecording()
        addText(status, SanctuaryLayout.CENTER_X, dividerY + 19f, 214f, 23f * scale, 31f)
    }

    private fun addText(text: String, center: Float, top: Float, width: Float, size: Float, height: Float, useSerif: Boolean = true) {
        val paint = textPaint(size, useSerif)
        val metrics = paint.fontMetrics
        val factor = minOf(1f, width / paint.measureText(text).coerceAtLeast(1f), height / (metrics.descent - metrics.ascent))
        paint.textSize *= factor
        runs.add(TextRun(text, center - paint.measureText(text) / 2f, top - paint.fontMetrics.ascent, paint))
    }
}

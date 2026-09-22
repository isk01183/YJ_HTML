package com.yj.magiccircle

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.round

/** Android-free numbers shared by the native artwork and its geometry checks. */
object ArtworkGeometry {
    data class Frame(val scale: Float, val left: Float, val top: Float)
    data class Node(val angle: Float, val innerX: Float, val innerY: Float, val diamond: Boolean)
    data class Rune(val x: Float, val y: Float, val rotation: Float, val glyph: Int)
    fun fit(width: Int, height: Int, designWidth: Float, designHeight: Float): Frame {
        require(designWidth.isFinite() && designWidth > 0 && designHeight.isFinite() && designHeight > 0)
        if (width <= 0 || height <= 0) return Frame(0f, 0f, 0f)
        val scale = minOf(width / designWidth, height / designHeight)
        return Frame(scale, (width - designWidth * scale) / 2f, (height - designHeight * scale) / 2f)
    }
    const val R01_SIZE = 1024f
    const val R01_CENTER_X = 512f
    const val R01_CENTER_Y = 512f
    const val R01_NODE_X = 255f
    val r01Angles = listOf(0f, 60f, 120f, 180f, 240f, 300f)
    val r01OuterRadii = listOf(440f, 420f, 390f, 372f)
    val r01Nodes = r01Angles.mapIndexed { i, angle ->
        Node(angle, if (i % 2 == 0) 273f else 246f, if (i % 2 == 0) -23f else 21f, i % 2 != 0)
    }
    val r01NodeOuterRadii = listOf(80f, 72f)
    val r01NodeInnerRadii = listOf(49f, 42f)
    val r01NodeDiamonds = listOf(31f, 22f, 10f)
    private fun rune(radius: Double, angle: Double, glyph: Int): Rune {
        val radians = Math.toRadians(angle)
        return Rune((round(radius * cos(radians) * 1000) / 1000).toFloat(),
            (round(radius * sin(radians) * 1000) / 1000).toFloat(), (angle + 90).toFloat(), glyph)
    }
    fun r01OuterRunes() = List(76) { i -> rune(405.0, i * 360.0 / 76 - 90, (i * 3 + i / 7) % 16) }
    fun r01InnerRunes() = List(30) { i ->
        val offset = 14 + i % 5 * 8
        val radius = (317 / cos(Math.toRadians((offset - 30).toDouble())) + 372) / 2
        rune(radius, (i / 5 * 60 + offset).toDouble(), i * 3 % 8)
    }
    // One revolution per two hours. Only the outer rune band consumes this angle.
    fun r01RuneAngle(elapsedMs: Long, animated: Boolean): Float =
        if (animated) (elapsedMs.coerceAtLeast(0) % 7200000L) * (360f / 7200000f) else 0f
}

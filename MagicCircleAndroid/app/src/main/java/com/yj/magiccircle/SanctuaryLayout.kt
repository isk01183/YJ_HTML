package com.yj.magiccircle

import kotlin.math.cos
import kotlin.math.sin

object SanctuaryLayout {
    const val DESIGN_WIDTH = 864f
    const val DESIGN_HEIGHT = 1536f
    const val CENTER_X = 432f
    const val CENTER_Y = 718f
    const val OUTER_RADIUS = 402f
    const val CORE_RADIUS = 142f

    data class Frame(val scale: Float, val left: Float, val top: Float)

    fun fit(width: Int, height: Int): Frame {
        val scale = minOf(width.coerceAtLeast(0) / DESIGN_WIDTH, height.coerceAtLeast(0) / DESIGN_HEIGHT)
        return Frame(scale, (width - DESIGN_WIDTH * scale) / 2f, (height - DESIGN_HEIGHT * scale) / 2f)
    }

    fun centeredBaseline(ascent: Float, descent: Float, centerY: Float): Float = centerY - (ascent + descent) / 2f
    fun centeredLeft(width: Float, centerX: Float): Float = centerX - width / 2f

    data class BatteryPlacement(
        val numberX: Float,
        val suffixX: Float,
        val baseline: Float,
        val dividerY: Float,
        val statusTop: Float,
    )

    fun batteryPlacement(numberWidth: Float, suffixWidth: Float, gap: Float, glyphTop: Float, glyphBottom: Float): BatteryPlacement {
        val numberX = centeredLeft(numberWidth + suffixWidth + gap, CENTER_X)
        val baseline = centeredBaseline(glyphTop, glyphBottom, CENTER_Y)
        val dividerY = maxOf(776f, baseline + glyphBottom + 8f)
        return BatteryPlacement(numberX, numberX + numberWidth + gap, baseline, dividerY, dividerY + 19f)
    }

    fun goldAngle(elapsed: Long): Float = (elapsed.coerceAtLeast(0) % 180000L) * (360f / 180000f)
    fun blueAngle(elapsed: Long): Float = -(elapsed.coerceAtLeast(0) % 240000L) * (360f / 240000f)

    data class Orbit(val radiusX: Float, val radiusY: Float, val rotation: Float)
    fun polygon(radius: Float, vertices: Int, step: Int, phase: Float): FloatArray {
        require(vertices >= 3 && step in 1 until vertices && radius.isFinite() && radius > 0 && phase.isFinite())
        return FloatArray((vertices + 1) * 2).also { points ->
            for (i in 0 until vertices) {
                val angle = Math.toRadians((phase + (i * step % vertices) * 360f / vertices).toDouble())
                points[i * 2] = CENTER_X + radius * cos(angle).toFloat()
                points[i * 2 + 1] = CENTER_Y + radius * sin(angle).toFloat()
            }
            points[vertices * 2] = points[0]
            points[vertices * 2 + 1] = points[1]
        }
    }
    fun starPolygons(): Array<FloatArray> = arrayOf(
        polygon(300f, 3, 1, -90f), polygon(300f, 3, 1, 90f),
        polygon(228f, 4, 1, -90f), polygon(228f, 4, 1, -45f),
    )
    // Triples: center x/y, then baseline rotation. Local +x is tangent; glyph up (-y) points outward.
    private fun runes(radius: Float, count: Int): FloatArray = FloatArray(count * 3).also { points ->
        for (i in 0 until count) {
            val angle = i * 360f / count
            val radians = Math.toRadians((angle - 90f).toDouble())
            points[i * 3] = CENTER_X + radius * cos(radians).toFloat()
            points[i * 3 + 1] = CENTER_Y + radius * sin(radians).toFloat()
            points[i * 3 + 2] = angle
        }
    }
    fun goldRunes(): FloatArray = runes(365f, 48)
    fun blueRunes(): FloatArray = runes(281f, 54)
    fun orbits(): Array<Orbit> = Array(4) { Orbit(255f, 103f, it * 45f) }
    fun orbitNodes(orbits: Array<Orbit>): FloatArray = FloatArray(orbits.size * 4).also { points ->
        for (i in orbits.indices) {
            val angle = Math.toRadians(orbits[i].rotation.toDouble())
            val x = orbits[i].radiusX * cos(angle).toFloat()
            val y = orbits[i].radiusX * sin(angle).toFloat()
            points[i * 4] = CENTER_X + x
            points[i * 4 + 1] = CENTER_Y + y
            points[i * 4 + 2] = CENTER_X - x
            points[i * 4 + 3] = CENTER_Y - y
        }
    }
}

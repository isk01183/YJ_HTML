package com.yj.magiccircle

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

    fun goldAngle(elapsed: Long): Float = (elapsed.coerceAtLeast(0) % 180000L) * (360f / 180000f)
    fun blueAngle(elapsed: Long): Float = -(elapsed.coerceAtLeast(0) % 240000L) * (360f / 240000f)
}

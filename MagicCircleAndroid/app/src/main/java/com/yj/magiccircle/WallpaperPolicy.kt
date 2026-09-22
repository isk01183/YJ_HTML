package com.yj.magiccircle

object WallpaperPolicy {
    @JvmStatic fun supportsStill(sdk: Int, target: String): Boolean =
        sdk >= 23 && (target == "home" || (target == "lock" && sdk >= 24))
    @JvmStatic fun allowedTheme(id: String?): Boolean = id == "ref-W03" || id == "ref-R01"
    @JvmStatic fun shouldRender(visible: Boolean, surfaceReady: Boolean, destroyed: Boolean): Boolean =
        visible && surfaceReady && !destroyed
    @JvmStatic fun confirmed(requested: String, actual: String?): Boolean =
        requested in setOf("W03", "R01") && requested == actual

    fun bitmapSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, kotlin.math.sqrt(6_000_000.0 / (width.toDouble() * height)),
            6_000_000.0 / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    fun liveResult(requested: String, actual: String?, before: String?, resultOk: Boolean): String = when {
        !resultOk && before != null && before == actual -> "retained"
        resultOk && confirmed(requested, actual) -> "confirmed"
        else -> "unconfirmed"
    }
}

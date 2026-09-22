package com.yj.magiccircle

import android.graphics.Canvas

/** Shared static/live entry point. Call prepare on size change and close when released. */
class WallpaperArtwork(val themeId: String) : AutoCloseable {
    init { ArtworkGeometry.designSize(themeId) }
    private val r01 = if (themeId == "ref-R01") R01Renderer() else null
    private val w03 = if (themeId == "ref-W03") W03Renderer() else null

    fun prepare(width: Int, height: Int) = when (themeId) {
        "ref-R01" -> r01!!.prepare(width, height)
        "ref-W03" -> w03!!.prepare(width, height)
        else -> error("Unsupported artwork")
    }

    /** Static callers use elapsedMs=0 and animated=false; live frame zero is identical. */
    fun draw(canvas: Canvas, elapsedMs: Long, animated: Boolean) = when (themeId) {
        "ref-R01" -> r01!!.draw(canvas, elapsedMs, animated)
        "ref-W03" -> w03!!.draw(canvas, elapsedMs, animated)
        else -> error("Unsupported artwork")
    }

    override fun close() = when (themeId) {
        "ref-R01" -> r01!!.close()
        "ref-W03" -> w03!!.close()
        else -> error("Unsupported artwork")
    }
}

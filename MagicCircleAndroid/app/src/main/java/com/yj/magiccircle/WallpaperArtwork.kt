package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream

/** Shared static/live entry point. Call prepare on size change and close when released. */
class WallpaperArtwork(val themeId: String) : AutoCloseable {
    companion object {
        @JvmStatic fun thumbnail(themeId: String): ByteArray {
            val (width, height) = when (themeId) {
                "ref-W03" -> 512 to 910
                "ref-R01" -> 512 to 512
                else -> error("Unsupported artwork")
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val artwork = WallpaperArtwork(themeId)
            return try {
                artwork.prepare(width, height)
                artwork.draw(Canvas(bitmap), 0L, false)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally {
                artwork.close()
                bitmap.recycle()
            }
        }
    }

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

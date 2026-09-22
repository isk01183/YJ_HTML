package com.yj.magiccircle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/** Real Bitmap checks; does not set a wallpaper or open a system picker. */
object WallpaperChecks {
    fun run(context: Context) {
        check(context.packageName == "com.yj.magiccircle")
        check(!WallpaperPolicy.allowedTheme("native-N01"))
        check(!WallpaperPolicy.supportsStill(23, "lock"))
        check(WallpaperPolicy.supportsStill(24, "lock"))
        check(!WallpaperPolicy.confirmed("W03", null))
        check(!WallpaperPolicy.confirmed("R01", "W03"))
        for (theme in listOf("ref-W03", "ref-R01")) {
            val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val artwork = WallpaperArtwork(theme)
            try {
                repeat(20) { iteration ->
                    artwork.prepare(if (iteration % 2 == 0) 640 else 360,
                        if (iteration % 2 == 0) 360 else 640)
                    artwork.draw(canvas, iteration * 34L, true)
                }
                artwork.prepare(360, 640)
                bitmap.eraseColor(Color.TRANSPARENT)
                artwork.draw(canvas, 0, false)
                val first = IntArray(360 * 640)
                bitmap.getPixels(first, 0, 360, 0, 0, 360, 640)
                check(first.all { Color.alpha(it) == 255 }) { "$theme left transparent pixels" }
                check(first.toSet().size > 100) { "$theme produced empty or flat output" }
                artwork.prepare(360, 640)
                bitmap.eraseColor(Color.TRANSPARENT)
                artwork.draw(canvas, 0, true)
                val repeated = IntArray(first.size)
                bitmap.getPixels(repeated, 0, 360, 0, 0, 360, 640)
                check(first.contentEquals(repeated)) { "$theme repeated prepare changed frame zero" }
                artwork.close()
                artwork.close()
                bitmap.eraseColor(Color.MAGENTA)
                artwork.draw(canvas, 0, false)
                check(bitmap.getPixel(180, 320) == Color.MAGENTA) { "$theme drew after terminal close" }
            } finally { artwork.close(); bitmap.recycle() }
        }
    }
}

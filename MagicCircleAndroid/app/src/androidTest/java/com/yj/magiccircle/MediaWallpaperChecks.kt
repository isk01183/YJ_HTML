package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

/** Uses real platform decoders without changing the device's wallpaper. */
object MediaWallpaperChecks {
    fun run() {
        var readBytes=0
        val bounded=MediaWallpaperRenderer(4096) { object: java.io.InputStream() {
            override fun read(): Int {readBytes++;return if(readBytes<=100_000) 0 else -1}
        } }
        check(runCatching {bounded.prepare(100,100)}.isFailure)
        bounded.close()
        check(readBytes<=16384) { "Read $readBytes bytes before enforcing a 4 KiB budget" }
        for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG)) {
            val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.GREEN)
            val bytes = ByteArrayOutputStream().use { output ->
                check(source.compress(format, 100, output)); output.toByteArray()
            }
            source.recycle()
            MediaWallpaperRenderer { bytes.inputStream() }.use { renderer ->
                renderer.prepare(30, 60)
                val target = Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888)
                try {
                    renderer.draw(Canvas(target), 0, false)
                    for ((x, y) in listOf(0 to 0, 15 to 30, 29 to 59)) {
                        check(Color.green(target.getPixel(x, y)) > 240) { "$format did not fill the viewport" }
                    }
                    check(!renderer.animated)
                } finally { target.recycle() }
            }
        }
        // Two one-second frames, red then blue; verifies genuine GIF animation and repeat.
        val hex = "47494638396101000100800000ff00000000ff21ff0b4e45545343415045322e30030100000021f90400640000002c000000000100010000020244010021f90400640000002c00000000010001000002024c01003b"
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        MediaWallpaperRenderer { bytes.inputStream() }.use { renderer ->
            renderer.prepare(32, 64)
            check(renderer.animated)
            val target = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(target)
                renderer.draw(canvas, 100, true)
                check(target.getPixel(16, 32) == Color.RED)
                renderer.draw(canvas, 1100, true)
                check(target.getPixel(16, 32) == Color.BLUE)
                renderer.draw(canvas, 2100, true)
                check(target.getPixel(16, 32) == Color.RED)
                renderer.draw(canvas, 1100, false)
                check(target.getPixel(16, 32) == Color.RED)
                renderer.close()
                target.eraseColor(Color.MAGENTA)
                renderer.draw(canvas, 100, true)
                check(target.getPixel(16, 32) == Color.MAGENTA)
            } finally { target.recycle() }
        }
    }
}

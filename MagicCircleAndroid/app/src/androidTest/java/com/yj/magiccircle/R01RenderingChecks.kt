package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Debug
import android.util.Log

/** Device-only checks: the host JVM cannot exercise Android Path/shader rendering. */
object R01RenderingChecks {
    fun run() {
        check(runCatching { WallpaperArtwork("ref-W03") }.exceptionOrNull() is IllegalArgumentException)
        val art = WallpaperArtwork("ref-R01")
        fun render(width: Int = 1024, height: Int = 1024, elapsed: Long = 0, animated: Boolean = false): Bitmap {
            art.prepare(width, height)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { art.draw(Canvas(it), elapsed, animated) }
        }
        val still = render()
        try {
            val zero = render(animated = true)
            try { check(still.sameAs(zero)) { "Static and live frame zero differ" } } finally { zero.recycle() }
            val lateStatic = render(elapsed = 1800000)
            try { check(still.sameAs(lateStatic)) { "Static output depends on elapsed time" } } finally { lateStatic.recycle() }
            check(still.getPixel(512, 512) != still.getPixel(0, 0)) { "Center light missing" }
            check(still.getPixel(952, 512) != still.getPixel(0, 0)) { "Outer ring missing" }
            val late = render(elapsed = 1800000, animated = true)
            try {
                check(!still.sameAs(late)) { "Outer runes did not animate" }
                for (y in 256 until 768) for (x in 256 until 768) {
                    check(still.getPixel(x, y) == late.getPixel(x, y)) { "Fixed core/petal/node moved" }
                }
            } finally { late.recycle() }
            val before = Debug.getNativeHeapAllocatedSize()
            repeat(20) { i ->
                val resized = if (i % 2 == 0) render(1080, 2400) else render(2560, 1600)
                try { check(resized.getPixel(resized.width / 2, resized.height / 2) != resized.getPixel(0, 0)) }
                finally { resized.recycle() }
            }
            Log.i("SanctuaryReview", "R01 prepare20 heapBefore=$before heapAfter=${Debug.getNativeHeapAllocatedSize()}")
            val again = render()
            try { check(still.sameAs(again)) { "Repeated prepare changed geometry" } } finally { again.recycle() }
            val empty = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                art.prepare(0, 1024)
                art.draw(Canvas(empty), 0, false)
                check(empty.getPixel(0, 0) == 0) { "Zero-size renderer drew pixels" }
                art.prepare(1024, 1024)
                art.close()
                art.draw(Canvas(empty), 0, false)
                check(empty.getPixel(0, 0) == 0) { "Closed renderer drew pixels" }
            } finally { empty.recycle() }
        } finally { still.recycle(); art.close() }
    }
}

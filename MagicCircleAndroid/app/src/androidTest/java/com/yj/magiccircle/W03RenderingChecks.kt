package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Debug
import android.util.Log

/** Real Bitmap Canvas checks. These require an Android runtime, not mocked JVM graphics. */
object W03RenderingChecks {
    fun run() {
        val art = WallpaperArtwork("ref-W03")
        val renderer = art.javaClass.getDeclaredField("w03").apply { isAccessible = true }.get(art)!!
        val cacheField = renderer.javaClass.getDeclaredField("atmosphere").apply { isAccessible = true }
        fun render(w: Int = 1000, h: Int = 1778, elapsed: Long = 0, animated: Boolean = false): Bitmap {
            art.prepare(w, h)
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { art.draw(Canvas(it), elapsed, animated) }
        }
        val still = render()
        try {
            val zero = render(animated = true)
            try { check(still.sameAs(zero)) { "W03 static/live frame zero differ" } } finally { zero.recycle() }
            val lateStatic = render(elapsed = 1800000)
            try { check(still.sameAs(lateStatic)) { "W03 static changes over time" } } finally { lateStatic.recycle() }
            val late = render(elapsed = 1800000, animated = true)
            try {
                check(!still.sameAs(late)) { "W03 light ring did not animate" }
                // The complete tree and roots lie inside this disc; the radius-433 light is outside it.
                for (y in 421..1241) for (x in 90..910) {
                    if ((x-500)*(x-500)+(y-831)*(y-831) < 410*410)
                        check(still.getPixel(x,y) == late.getPixel(x,y)) { "W03 tree/core/root structure moved" }
                }
                for (y in listOf(170,472,864,1083,1224,1360)) {
                    check(still.getPixel(500,y) == late.getPixel(500,y)) { "W03 fixed axis moved at $y" }
                    check(still.getPixel(500,y) != still.getPixel(500,1777)) { "W03 axis motif missing at $y" }
                }
            } finally { late.recycle() }
            // Source endpoints and their shared sprigs must retain visible authored marks.
            for ((x,y) in listOf(224 to 829,455 to 541,777 to 831,542 to 538,282 to 1009,718 to 1006)) {
                val color=still.getPixel(x,y)
                check(android.graphics.Color.red(color)>70 || android.graphics.Color.green(color)>90) {
                    "W03 branch/root endpoint missing at $x,$y"
                }
            }
            val initialCache=cacheField.get(renderer) as Bitmap
            art.prepare(1000,1778)
            check(cacheField.get(renderer) === initialCache) { "Same-size prepare replaced W03 cache" }
            val before=Debug.getNativeHeapAllocatedSize()
            repeat(20) { i ->
                val old=cacheField.get(renderer) as Bitmap
                val resized=if(i%2==0)render(1080,2400) else render(2560,1600)
                try {
                    val next=cacheField.get(renderer) as Bitmap
                    check(old.isRecycled && old !== next) { "W03 old atmosphere cache retained" }
                    check(maxOf(next.width,next.height)<=1024) { "W03 atmosphere cache exceeded cap" }
                    // No transparent/black letterbox at viewport corners or edges.
                    for ((x,y) in listOf(0 to 0, resized.width-1 to 0, 0 to resized.height-1,
                        resized.width-1 to resized.height-1,0 to resized.height/2)) {
                        val pixel=resized.getPixel(x,y)
                        check(android.graphics.Color.alpha(pixel)==255 && pixel != android.graphics.Color.BLACK)
                    }
                } finally { resized.recycle() }
            }
            Log.i("SanctuaryReview","W03 prepare20 heapBefore=$before heapAfter=${Debug.getNativeHeapAllocatedSize()}")
            val again=render()
            try { check(still.sameAs(again)) { "W03 resize changed deterministic output" } } finally { again.recycle() }
            val old=cacheField.get(renderer) as Bitmap
            val empty=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            try {
                art.prepare(0,1778);art.draw(Canvas(empty),0,false)
                check(old.isRecycled && cacheField.get(renderer)==null)
                check(empty.getPixel(0,0)==0) { "Zero-size W03 drew" }
                art.prepare(1000,1778)
                val last=cacheField.get(renderer) as Bitmap
                art.close();art.draw(Canvas(empty),0,false)
                check(last.isRecycled && cacheField.get(renderer)==null)
                check(empty.getPixel(0,0)==0) { "Closed W03 drew" }
            } finally { empty.recycle() }
        } finally { still.recycle();art.close() }
    }
}

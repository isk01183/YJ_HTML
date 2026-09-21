package com.yj.magiccircle

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/** All geometry is recorded once in the 864 × 1536 design space. Only rune bands rotate. */
class MagicCircleRenderer {
    private val ink = SanctuaryInk()
    private val goldRunes = runeBand(SanctuaryLayout.goldRunes(), 1f)
    private val blueRunes = runeBand(SanctuaryLayout.blueRunes(), .8f)
    private val staticArt = Picture()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private lateinit var fixedPixels: Bitmap
    private lateinit var goldPixels: Bitmap
    private lateinit var bluePixels: Bitmap

    init {
        val canvas = staticArt.beginRecording(864, 1536)
        val cx = SanctuaryLayout.CENTER_X
        val cy = SanctuaryLayout.CENTER_Y
        val rings = Path()
        for (radius in floatArrayOf(402f, 396f, 388f)) rings.addCircle(cx, cy, radius, Path.Direction.CW)
        ink.glow(canvas, rings, SanctuaryInk.GOLD, 1.5f)
        val blue = Path()
        for (radius in floatArrayOf(302f, 320f)) blue.addCircle(cx, cy, radius, Path.Direction.CW)
        ink.glow(canvas, blue, SanctuaryInk.CYAN, 2f)
        val filigree = Path()
        for (radius in floatArrayOf(340f, 329f, 261f, 240f)) filigree.addCircle(cx, cy, radius, Path.Direction.CW)
        ink.stroke(canvas, filigree, 0xff8ac9de.toInt(), .6f, 145)
        val ticks = Path()
        val goldDots = Path()
        for (i in 0 until 360) {
            val a = Math.toRadians(i.toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val outer = if (i % 5 == 0) 326f else 323f
            ticks.moveTo(cx + c * 317f, cy + s * 317f)
            ticks.lineTo(cx + c * outer, cy + s * outer)
            goldDots.addCircle(cx + c * 409f, cy + s * 409f, if (i % 5 == 0) 1.2f else .65f, Path.Direction.CW)
        }
        ink.stroke(canvas, ticks, SanctuaryInk.CYAN, .65f, 175)
        ink.fill(canvas, goldDots, SanctuaryInk.GOLD, 190)
        for (points in SanctuaryLayout.starPolygons()) ink.glow(canvas, linePath(points), SanctuaryInk.GOLD, 1.1f)
        val mesh = Path()
        val vertices = SanctuaryLayout.polygon(228f, 12, 5, -90f)
        mesh.addPath(linePath(vertices))
        ink.stroke(canvas, mesh, SanctuaryInk.GOLD, .7f, 105)
        val orbits = SanctuaryLayout.orbits()
        val ellipse = RectF(cx - orbits[0].radiusX, cy - orbits[0].radiusY, cx + orbits[0].radiusX, cy + orbits[0].radiusY)
        for (orbit in orbits) {
            val path = Path().apply { addOval(ellipse, Path.Direction.CW) }
            val matrix = Matrix().apply { setRotate(orbit.rotation, cx, cy) }
            path.transform(matrix)
            ink.glow(canvas, path, SanctuaryInk.CYAN, 3.2f)
        }
        val nodes = SanctuaryLayout.orbitNodes(orbits)
        for (i in nodes.indices step 2) ink.flare(canvas, nodes[i], nodes[i + 1], 10f, true)
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            ink.flare(canvas, cx + cos(a).toFloat() * 199f, cy + sin(a).toFloat() * 199f, if (i % 3 == 0) 9f else 6f)
        }
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            // A separate lane keeps fixed lunar marks clear of the rotating gold glyphs.
            val x = cx + cos(a).toFloat() * 342f
            val y = cy + sin(a).toFloat() * 342f
            if (i % 2 == 0) {
                val p = Path().apply { addCircle(x, y, 11f, Path.Direction.CW) }
                ink.glow(canvas, p, SanctuaryInk.GOLD, 1.6f)
            } else {
                canvas.save()
                canvas.rotate(i * 45f, x, y)
                ink.fill(canvas, SanctuaryInk.crescent(x, y, 11f), SanctuaryInk.GOLD)
                canvas.restore()
            }
        }
        for (i in 0 until 4) {
            val a = Math.toRadians((i * 90).toDouble())
            ink.flare(canvas, cx + cos(a).toFloat() * 394f, cy + sin(a).toFloat() * 394f, 23f)
        }
        ink.fill(canvas, SanctuaryInk.crescent(cx, cy - 220f, 17f), SanctuaryInk.GOLD)
        ink.fill(canvas, SanctuaryInk.crescent(cx, cy + 222f, 18f), SanctuaryInk.GOLD)
        drawCore(canvas)
        staticArt.endRecording()
        // Three bounded, code-painted design-space caches (~15.2MiB total); never reference artwork.
        fun pixels(paint: (Canvas) -> Unit): Bitmap = Bitmap.createBitmap(864, 1536, Bitmap.Config.ARGB_8888).also { paint(Canvas(it)) }
        fixedPixels = pixels { it.drawPicture(staticArt) }
        goldPixels = pixels { ink.glow(it, goldRunes, SanctuaryInk.GOLD, 1.45f) }
        bluePixels = pixels { ink.glow(it, blueRunes, SanctuaryInk.CYAN, 1.05f) }
    }

    fun draw(canvas: Canvas, goldAngle: Float, blueAngle: Float) {
        canvas.drawBitmap(fixedPixels, 0f, 0f, bitmapPaint)
        canvas.save()
        canvas.rotate(goldAngle, SanctuaryLayout.CENTER_X, SanctuaryLayout.CENTER_Y)
        canvas.drawBitmap(goldPixels, 0f, 0f, bitmapPaint)
        canvas.restore()
        canvas.save()
        canvas.rotate(blueAngle, SanctuaryLayout.CENTER_X, SanctuaryLayout.CENTER_Y)
        canvas.drawBitmap(bluePixels, 0f, 0f, bitmapPaint)
        canvas.restore()
    }

    private fun drawCore(canvas: Canvas) {
        val cx = SanctuaryLayout.CENTER_X
        val cy = SanctuaryLayout.CENTER_Y
        val radius = SanctuaryLayout.CORE_RADIUS
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - 25f, cy - 35f, 180f, intArrayOf(0xff040811.toInt(), 0xff02050a.toInt(), 0xff063a58.toInt()), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, radius, paint)
        val random = Random(901)
        val dust = Path()
        val mist = Path()
        for (i in 0 until 2600) {
            val angle = random.nextFloat() * Math.PI * 2
            val r = radius * kotlin.math.sqrt(random.nextFloat())
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (r > 100f) mist.addCircle(x, y, random.nextFloat() * 1.2f + .3f, Path.Direction.CW)
            if (r > 82f || i % 9 == 0) dust.addCircle(x, y, random.nextFloat() * .5f + .15f, Path.Direction.CW)
        }
        ink.fill(canvas, mist, 0xff168dc9.toInt(), 36)
        ink.fill(canvas, dust, 0xff90dfff.toInt(), 130)
        val ring = Path().apply { addCircle(cx, cy, radius + 1f, Path.Direction.CW) }
        ink.glow(canvas, ring, SanctuaryInk.CYAN, 3f)
        val gold = Path().apply { addCircle(cx, cy, radius - 3f, Path.Direction.CW) }
        ink.stroke(canvas, gold, SanctuaryInk.GOLD, .75f, 200)
        ink.flare(canvas, cx, cy + 58f, 8f)
    }

    private fun linePath(points: FloatArray) = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }

    private fun runeBand(placements: FloatArray, scale: Float): Path {
        // 24 distinct vector glyphs, never font fallback characters.
        val glyphs = arrayOf(
            "0,-10 0,10;0,-9 7,-5 0,0;0,-2 6,2", "-5,10 -5,-10 6,-4 -5,1 7,10",
            "6,-10 -6,0 6,10", "-6,-10 6,10;-6,10 6,-10", "-5,10 -5,-10 6,-3 -5,3",
            "-6,-10 -6,10;6,-10 6,10;-6,-3 6,3", "0,-10 0,10;-6,-4 6,4", "0,-10 0,10;-4,-7 4,-7;-4,7 4,7",
            "6,-9 -4,-3 3,2 -6,9", "-5,-10 4,-4 -4,4 5,10;0,-8 0,8", "-6,-10 -6,10 6,4 -6,-3 6,-9 6,10",
            "0,10 0,-10;-7,-7 0,0 7,-7", "5,-10 -5,-3 5,3 -5,10", "0,10 0,-10;-7,-3 0,-10 7,-3",
            "-5,10 -5,-10 6,-5 -5,0 6,5 -5,10", "-7,10 -7,-10 0,0 7,-10 7,10",
            "-7,10 -7,-10 7,3;7,10 7,-10 -7,3", "-4,10 -4,-10 7,-3", "-7,-5 7,5 7,-5 -7,5 -7,-5",
            "0,-10 7,0 0,7 -7,0 0,-10;-5,10 0,7 5,10", "0,-10 7,8 -7,8 0,-10;0,-3 0,3",
            "0,-10 7,0 0,10 -7,0 0,-10;0,-5 0,5", "0,-10 0,10;-7,0 7,0;-5,-6 5,6;-5,6 5,-6",
            "-6,-8 6,-8 -6,8 6,8;0,-11 0,11",
        ).map { specification ->
            Path().apply {
                for (segment in specification.split(';')) {
                    for ((index, pair) in segment.split(' ').withIndex()) {
                        val xy = pair.split(',')
                        val x = xy[0].toFloat() * scale
                        val y = xy[1].toFloat() * scale
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }
        }
        return Path().apply {
            for (i in placements.indices step 3) {
                val matrix = Matrix().apply {
                    setRotate(placements[i + 2])
                    postTranslate(placements[i], placements[i + 1])
                }
                addPath(glyphs[(i / 3) % glyphs.size], matrix)
            }
        }
    }
}

/** Shared cached light brushes; no blur filters or software layers. */
internal class SanctuaryInk {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val widths = floatArrayOf(12f, 7f, 3.5f, 1f, .4f)
    private val alphas = intArrayOf(8, 15, 35, 225, 250)
    private val star = Path().apply {
        moveTo(0f, -1.8f); cubicTo(.08f, -.12f, .1f, -.1f, 1f, 0f)
        cubicTo(.1f, .1f, .08f, .12f, 0f, 1.8f)
        cubicTo(-.08f, .12f, -.1f, .1f, -1f, 0f)
        cubicTo(-.1f, -.1f, -.08f, -.12f, 0f, -1.8f); close()
    }
    private val goldAura = RadialGradient(0f, 0f, 4f, intArrayOf(0xc0ffce70.toInt(), 0x30dc912f, 0x00dc912f), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
    private val blueAura = RadialGradient(0f, 0f, 4f, intArrayOf(0xc060e5ff.toInt(), 0x3024bfff, 0x0024bfff), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)

    fun stroke(canvas: Canvas, path: Path, color: Int, width: Float, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.color = color; paint.alpha = alpha; paint.strokeWidth = width
        canvas.drawPath(path, paint)
    }
    fun fill(canvas: Canvas, path: Path, color: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = alpha
        canvas.drawPath(path, paint)
    }
    fun glow(canvas: Canvas, path: Path, color: Int, width: Float) {
        for (i in widths.indices) stroke(canvas, path, if (i == 4) 0xfffffff0.toInt() else color, width * widths[i], alphas[i])
    }
    fun flare(canvas: Canvas, x: Float, y: Float, size: Float, blue: Boolean = false) {
        canvas.save(); canvas.translate(x, y); canvas.scale(size, size)
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.shader = if (blue) blueAura else goldAura
        canvas.drawCircle(0f, 0f, 4f, paint)
        paint.shader = null; paint.color = if (blue) CYAN else GOLD
        canvas.drawPath(star, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(0f, 0f, if (blue) .75f else .17f, paint)
        canvas.restore()
    }
    companion object {
        const val GOLD = 0xffffe9ad.toInt()
        const val CYAN = 0xff64dcff.toInt()
        fun crescent(x: Float, y: Float, r: Float) = Path().apply {
            moveTo(x, y - r)
            cubicTo(x + r * 1.333333f, y - r, x + r * 1.333333f, y + r, x, y + r)
            cubicTo(x + r * .72f, y + r * .6f, x + r * .72f, y - r * .6f, x, y - r)
            close()
        }
    }
}

package com.yj.magiccircle

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/** Direct numeric port of direct-circles.js r01(); geometry stays in its 1024-square space. */
class R01Renderer : AutoCloseable {
    private val ink = SanctuaryInk()
    private var frame = ArtworkGeometry.Frame(0f, 0f, 0f)
    private val ambient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 486f, intArrayOf(0x36126c91, 0x0b126c91, 0x00126c91),
            floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
    }
    private val rings = Path().apply {
        for (r in ArtworkGeometry.r01OuterRadii) addCircle(0f, 0f, r, Path.Direction.CW)
    }
    private val petal = Path().apply {
        moveTo(62f, 0f); cubicTo(126f, -60f, 196f, -100f, 249f, -88f)
        cubicTo(297f, -78f, 338f, -34f, 366f, 0f)
        cubicTo(322f, 46f, 281f, 92f, 245f, 87f)
        cubicTo(182f, 86f, 111f, 36f, 62f, 0f); close()
    }
    private val scaffold = scaffold()
    private val outerRunes = runeBand(roundedGlyphs(), ArtworkGeometry.r01OuterRunes(), 1.2f)
    private val innerRunes = runeBand(cursiveGlyphs(), ArtworkGeometry.r01InnerRunes(), 1.16f)
    private val nodeOutlines = ArtworkGeometry.r01Nodes.map { node -> Path().apply {
        addCircle(ArtworkGeometry.R01_NODE_X, 0f, ArtworkGeometry.r01NodeOuterRadii[0], Path.Direction.CW)
        addCircle(node.innerX, node.innerY, ArtworkGeometry.r01NodeInnerRadii[0], Path.Direction.CW)
        if (node.diamond) for (r in ArtworkGeometry.r01NodeDiamonds) diamond(node.innerX, node.innerY, r)
    } }
    private val nodeOuterThin = Path().apply {
        addCircle(ArtworkGeometry.R01_NODE_X, 0f, ArtworkGeometry.r01NodeOuterRadii[1], Path.Direction.CW)
    }
    private val nodeInnerThin = ArtworkGeometry.r01Nodes.map { node -> Path().apply {
        addCircle(node.innerX, node.innerY, ArtworkGeometry.r01NodeInnerRadii[1], Path.Direction.CW)
    } }
    private val nodeTicks = ArtworkGeometry.r01Nodes.map { node -> ticks(node.innerX, node.innerY, 38f, 20, 2f) }
    private val nodeDots = ArtworkGeometry.r01Nodes.map { node -> Path().apply {
        if (node.diamond) addCircle(node.innerX, node.innerY, 2.4f, Path.Direction.CW)
    } }
    private val core = Path().apply {
        for (r in listOf(62f, 44f, 36f)) addCircle(0f, 0f, r, Path.Direction.CW)
        addPath(ticks(0f, 0f, 48f, 24, 3f))
        diamond(0f, 0f, 28f); diamond(0f, 0f, 13f)
        addCircle(0f, 0f, 3f, Path.Direction.CW)
    }
    private val coreDot = Path().apply { addCircle(0f, 0f, 3f, Path.Direction.CW) }

    fun prepare(width: Int, height: Int) {
        frame = ArtworkGeometry.fit(width, height, ArtworkGeometry.R01_SIZE, ArtworkGeometry.R01_SIZE)
    }

    fun draw(canvas: Canvas, elapsedMs: Long, animated: Boolean) {
        if (frame.scale <= 0f) return
        val saved = canvas.save()
        canvas.drawColor(0xff02090e.toInt())
        canvas.translate(frame.left, frame.top)
        canvas.scale(frame.scale, frame.scale)
        canvas.translate(ArtworkGeometry.R01_CENTER_X, ArtworkGeometry.R01_CENTER_Y)
        canvas.drawCircle(0f, 0f, 486f, ambient)
        lit(canvas, rings, 0xff8aeaff.toInt(), 1.8f, .55f)
        val runes = canvas.save()
        canvas.rotate(ArtworkGeometry.r01RuneAngle(elapsedMs, animated))
        lit(canvas, outerRunes, 0xff8bddea.toInt(), 1.7f * 1.2f, .33f)
        canvas.restoreToCount(runes)
        // Inner glyphs follow the hexagon clearance lane and therefore stay fixed with it.
        lit(canvas, innerRunes, 0xff8bddea.toInt(), 1.7f * 1.16f, .33f)
        lit(canvas, scaffold, 0xff8be4f4.toInt(), 2f, .38f)
        for (angle in ArtworkGeometry.r01Angles) {
            val petalSave = canvas.save()
            canvas.rotate(angle)
            lit(canvas, petal, 0xffb9f9ff.toInt(), 2.5f, .55f)
            canvas.restoreToCount(petalSave)
        }
        for ((i, node) in ArtworkGeometry.r01Nodes.withIndex()) {
            val nodeSave = canvas.save()
            canvas.rotate(node.angle)
            lit(canvas, nodeOutlines[i], 0xffb9f9ff.toInt(), 2.05f, .38f)
            lit(canvas, nodeOuterThin, 0xffb9f9ff.toInt(), 1.2f, .38f)
            lit(canvas, nodeInnerThin[i], 0xffb9f9ff.toInt(), 1.15f, .38f)
            lit(canvas, nodeTicks[i], 0xffb9f9ff.toInt(), 2f, .38f)
            if (node.diamond) {
                lit(canvas, nodeDots[i], 0xffb9f9ff.toInt(), 2.05f, .38f)
                ink.fill(canvas, nodeDots[i], 0xffddfbff.toInt())
            }
            canvas.restoreToCount(nodeSave)
        }
        lit(canvas, core, 0xffd0fcff.toInt(), 2f, .55f)
        ink.fill(canvas, coreDot, 0xffecffff.toInt())
        canvas.restoreToCount(saved)
    }

    // No viewport bitmaps: resizing replaces only this frame; cached authored Paths are reused.
    override fun close() { frame = ArtworkGeometry.Frame(0f, 0f, 0f) }

    private fun lit(canvas: Canvas, path: Path, color: Int, width: Float, glow: Float) {
        // Canvas-safe approximation of SVG's 3.3px blur, followed by exactly one colored outline.
        ink.stroke(canvas, path, color, width + 10f, (glow * 12).toInt())
        ink.stroke(canvas, path, color, width + 5f, (glow * 28).toInt())
        ink.stroke(canvas, path, color, width + 2f, (glow * 42).toInt())
        ink.stroke(canvas, path, color, width)
    }

    private fun Path.diamond(x: Float, y: Float, r: Float) {
        moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close()
    }

    private fun ticks(x: Float, y: Float, r: Float, count: Int, length: Float) = Path().apply {
        for (i in 0 until count) {
            val angle = Math.toRadians(i * 360.0 / count)
            val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
            val end = r + if (i % 5 == 0) length * 1.7f else length
            moveTo(x + r * c, y + r * s); lineTo(x + end * c, y + end * s)
        }
    }

    private fun scaffold() = Path().apply {
        for ((i, angle) in ArtworkGeometry.r01Angles.withIndex()) {
            val radians = Math.toRadians(angle.toDouble())
            val x = 366f * cos(radians).toFloat(); val y = 366f * sin(radians).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
        val bridge = Path().apply {
            moveTo(-11f, -61.016f); lineTo(11f, -61.016f)
            cubicTo(9f, -102f, 9f, -143f, 13f, -180f); lineTo(-13f, -180f)
            cubicTo(-9f, -143f, -9f, -102f, -11f, -61.016f); close()
        }
        for (angle in ArtworkGeometry.r01Angles) addPath(bridge, Matrix().apply { setRotate(angle) })
        val gate = Path().apply {
            moveTo(-35f, -214f); lineTo(35f, -214f); quadTo(47f, -245f, 61f, -278f)
            quadTo(0f, -291f, -61f, -278f); quadTo(-47f, -245f, -35f, -214f); close()
            moveTo(-29f, -221f); lineTo(29f, -221f); quadTo(39f, -249f, 51f, -273f)
            quadTo(0f, -283f, -51f, -273f); quadTo(-39f, -249f, -29f, -221f); close()
        }
        for (angle in listOf(0f, 180f)) addPath(gate, Matrix().apply { setRotate(angle) })
        val brace = Path().apply {
            moveTo(205f, -180f); lineTo(257f, -126f); lineTo(199f, -155f); close()
            moveTo(211f, -166f); lineTo(241f, -134f); lineTo(208f, -152f); close()
        }
        for (x in listOf(1f, -1f)) for (y in listOf(1f, -1f)) addPath(brace, Matrix().apply { setScale(x, y) })
    }

    private fun runeBand(glyphs: List<Path>, placements: List<ArtworkGeometry.Rune>, size: Float) = Path().apply {
        for (rune in placements) addPath(glyphs[rune.glyph], Matrix().apply {
            setScale(size, size); postRotate(rune.rotation); postTranslate(rune.x, rune.y)
        })
    }

    // The sixteen rounded glyphs are the source's authored alphabet, not font substitutions.
    private fun roundedGlyphs() = listOf(
        Path().apply { moveTo(-5f,-6f); quadTo(-9f,-4f,-6f,1f); lineTo(-6f,5f); quadTo(0f,9f,5f,5f); lineTo(5f,-4f); quadTo(1f,-8f,-5f,-6f); close() },
        Path().apply { moveTo(-5f,-6f); lineTo(3f,-6f); quadTo(7f,-3f,4f,0f); quadTo(8f,6f,1f,6f); lineTo(-5f,4f); lineTo(-3f,0f); close() },
        Path().apply { moveTo(-4f,-6f); quadTo(3f,-8f,5f,-3f); lineTo(5f,5f); quadTo(0f,8f,-5f,4f); lineTo(-6f,-2f); close() },
        Path().apply { moveTo(-5f,-5f); lineTo(-1f,-7f); lineTo(5f,-4f); lineTo(4f,5f); lineTo(-3f,6f); quadTo(-7f,3f,-4f,0f); close() },
        Path().apply { moveTo(-5f,-5f); quadTo(-1f,-8f,4f,-5f); lineTo(5f,0f); lineTo(2f,1f); lineTo(5f,4f); quadTo(1f,8f,-5f,4f); close() },
        Path().apply { moveTo(-5f,-5f); lineTo(4f,-6f); lineTo(6f,-1f); lineTo(3f,5f); lineTo(-4f,6f); lineTo(-6f,0f); close() },
        Path().apply { moveTo(-6f,-4f); lineTo(-2f,-6f); lineTo(4f,-5f); quadTo(7f,-2f,3f,1f); lineTo(5f,5f); lineTo(-3f,6f); lineTo(-6f,2f); close() },
        Path().apply { moveTo(-5f,-6f); lineTo(2f,-6f); lineTo(2f,-2f); lineTo(5f,-1f); lineTo(4f,5f); quadTo(0f,8f,-4f,4f); lineTo(-4f,0f); lineTo(-6f,-1f); close() },
        Path().apply { moveTo(-4f,-6f); quadTo(1f,-8f,5f,-3f); lineTo(3f,0f); lineTo(5f,4f); quadTo(1f,8f,-3f,5f); lineTo(-5f,1f); close() },
        Path().apply { moveTo(-6f,-3f); quadTo(-4f,-7f,1f,-6f); lineTo(5f,-4f); lineTo(4f,2f); lineTo(1f,6f); lineTo(-5f,4f); close() },
        Path().apply { moveTo(-5f,-4f); lineTo(-1f,-7f); lineTo(4f,-4f); lineTo(3f,-1f); lineTo(5f,1f); lineTo(3f,5f); lineTo(-3f,6f); lineTo(-5f,2f); close() },
        Path().apply { moveTo(-4f,-6f); lineTo(4f,-5f); lineTo(5f,-1f); quadTo(1f,1f,4f,4f); lineTo(1f,6f); lineTo(-4f,4f); lineTo(-6f,-1f); close() },
        Path().apply { moveTo(-6f,-4f); lineTo(0f,-6f); lineTo(5f,-2f); lineTo(4f,4f); lineTo(0f,6f); lineTo(-4f,4f); lineTo(-3f,1f); lineTo(-6f,-1f); close() },
        Path().apply { moveTo(-5f,-5f); lineTo(4f,-6f); lineTo(4f,-3f); lineTo(6f,0f); lineTo(3f,5f); lineTo(-3f,6f); lineTo(-5f,3f); lineTo(-3f,0f); close() },
        Path().apply { moveTo(-4f,-6f); quadTo(0f,-7f,4f,-4f); lineTo(5f,2f); lineTo(2f,5f); lineTo(-4f,5f); lineTo(-6f,1f); lineTo(-3f,-1f); close() },
        Path().apply { moveTo(-6f,-4f); lineTo(-2f,-6f); lineTo(1f,-3f); lineTo(5f,-4f); lineTo(6f,2f); lineTo(2f,6f); lineTo(-3f,5f); lineTo(-5f,1f); close() },
    )

    private fun cursiveGlyphs() = listOf(
        Path().apply { moveTo(-9f,4f); lineTo(-10f,-5f); quadTo(-7f,-8f,-4f,-3f); lineTo(-1f,1f); lineTo(0f,-5f); quadTo(3f,-8f,6f,-3f); lineTo(9f,5f); lineTo(5f,7f); lineTo(2f,1f); lineTo(0f,6f); lineTo(-4f,6f); lineTo(-6f,-1f); lineTo(-6f,5f); close() },
        Path().apply { moveTo(-10f,-4f); lineTo(-6f,-7f); lineTo(-3f,-1f); lineTo(0f,-5f); lineTo(4f,-4f); lineTo(7f,0f); lineTo(10f,-2f); lineTo(10f,4f); lineTo(5f,6f); lineTo(1f,1f); lineTo(-1f,5f); lineTo(-5f,3f); lineTo(-6f,-1f); lineTo(-8f,1f); close() },
        Path().apply { moveTo(-9f,-6f); lineTo(-5f,-7f); lineTo(-3f,1f); quadTo(0f,-4f,2f,-4f); quadTo(7f,-7f,9f,-2f); lineTo(8f,5f); lineTo(4f,6f); lineTo(4f,0f); lineTo(1f,2f); lineTo(0f,7f); lineTo(-4f,6f); lineTo(-5f,2f); lineTo(-8f,3f); close() },
        Path().apply { moveTo(-10f,3f); lineTo(-8f,-3f); lineTo(-5f,-4f); lineTo(-4f,0f); lineTo(-1f,-6f); lineTo(3f,-5f); lineTo(5f,1f); lineTo(7f,-2f); lineTo(10f,0f); lineTo(8f,6f); lineTo(4f,6f); lineTo(1f,1f); lineTo(-1f,6f); lineTo(-5f,4f); lineTo(-8f,6f); close() },
        Path().apply { moveTo(-9f,-4f); lineTo(-5f,-6f); lineTo(-2f,0f); lineTo(0f,-4f); lineTo(4f,-5f); lineTo(6f,-1f); lineTo(9f,-3f); lineTo(10f,3f); lineTo(6f,5f); lineTo(2f,2f); lineTo(0f,6f); lineTo(-4f,6f); lineTo(-5f,1f); lineTo(-8f,2f); close() },
        Path().apply { moveTo(-10f,-3f); lineTo(-7f,-6f); lineTo(-3f,-3f); lineTo(-1f,-6f); lineTo(3f,-4f); lineTo(3f,0f); lineTo(8f,-1f); lineTo(10f,3f); lineTo(6f,6f); lineTo(1f,4f); lineTo(-1f,1f); lineTo(-3f,6f); lineTo(-7f,4f); lineTo(-6f,0f); lineTo(-9f,1f); close() },
        Path().apply { moveTo(-9f,3f); lineTo(-8f,-5f); lineTo(-4f,-6f); lineTo(-2f,-1f); lineTo(1f,-5f); lineTo(4f,-4f); lineTo(5f,1f); lineTo(8f,-2f); lineTo(10f,2f); lineTo(7f,6f); lineTo(3f,5f); lineTo(1f,1f); lineTo(-2f,6f); lineTo(-5f,3f); lineTo(-6f,6f); close() },
        Path().apply { moveTo(-10f,-4f); lineTo(-7f,-6f); lineTo(-4f,-1f); lineTo(-1f,-3f); lineTo(1f,-6f); lineTo(5f,-5f); lineTo(5f,0f); lineTo(9f,2f); lineTo(8f,6f); lineTo(3f,5f); lineTo(1f,0f); lineTo(-2f,5f); lineTo(-5f,4f); lineTo(-6f,0f); lineTo(-9f,1f); close() },
    )
}

package com.yj.magiccircle

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import java.util.Random
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Full-viewport background. Call outside the View's circle/panel design-space transform. */
class CosmicBackgroundRenderer {
    private val ink = SanctuaryInk()
    private var scene: Bitmap? = null
    private val sceneBounds = Rect()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val galaxyArt = Array(4) { galaxy(8341L + it * 177L, if (it % 2 == 0) 5 else 4) }
    private val centersX = floatArrayOf(118f, 768f, 104f, 786f)
    private val centersY = floatArrayOf(162f, 231f, 1275f, 1300f)
    private val radii = floatArrayOf(240f, 207f, 191f, 217f)
    private val rotations = floatArrayOf(-24f, 38f, 117f, -48f)
    private var width = 0
    private var height = 0
    private var scale = 1f
    private var texture: NebulaTexture? = null
    private var textureAttempted = false

    fun prepare(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || this.width == width && this.height == height) return
        this.width = width
        this.height = height
        scale = min(width / 864f, height / 1536f)
        // Cache our own procedural paint at native resolution; no reference/image asset is loaded.
        // ponytail: at most 4M pixels (16MiB); larger displays use a uniformly downsampled cache.
        val cacheScale = min(1f, kotlin.math.sqrt(4194304.0 / (width.toDouble() * height)).toFloat())
        val nextScene = Bitmap.createBitmap(max(1, (width * cacheScale).toInt()), max(1, (height * cacheScale).toInt()), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(nextScene)
        canvas.scale(cacheScale, cacheScale)
        sceneBounds.set(0, 0, width, height)
        val random = Random(73123L)
        val faintStars = Array(3) { Path() }
        for (i in 0 until 11000) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val size = (.18f + random.nextFloat() * .65f) * scale
            faintStars[i % 3].addCircle(x, y, size, Path.Direction.CW)
        }
        for (i in faintStars.indices) ink.fill(canvas, faintStars[i], if (i == 0) SanctuaryInk.GOLD else SanctuaryInk.CYAN, 60 + i * 35)
        for (i in galaxyArt.indices) {
            canvas.save()
            canvas.translate(centersX[i] / 864f * width, centersY[i] / 1536f * height)
            canvas.rotate(rotations[i])
            val size = radii[i] / 220f * scale
            canvas.scale(size, size * if (i % 2 == 0) .86f else .7f)
            canvas.translate(-280f, -280f)
            canvas.drawPicture(galaxyArt[i])
            canvas.restore()
        }
        val constellation = Path()
        val clusters = arrayOf(
            floatArrayOf(565f, 51f, 624f, 82f, 699f, 92f, 761f, 122f, 648f, 117f, 669f, 154f),
            floatArrayOf(8f, 304f, 95f, 337f, 147f, 382f, 200f, 354f, 211f, 430f),
            floatArrayOf(654f, 1068f, 730f, 1028f, 786f, 1067f, 719f, 1135f, 668f, 1167f),
        )
        for (cluster in clusters) {
            constellation.moveTo(cluster[0] / 864f * width, cluster[1] / 1536f * height)
            for (i in 2 until cluster.size step 2) constellation.lineTo(cluster[i] / 864f * width, cluster[i + 1] / 1536f * height)
            for (i in cluster.indices step 2) ink.flare(canvas, cluster[i] / 864f * width, cluster[i + 1] / 1536f * height, 3f * scale)
        }
        ink.stroke(canvas, constellation, SanctuaryInk.GOLD, .6f * scale, 130)
        for (i in 0 until 180) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            ink.flare(canvas, x, y, (1f + random.nextFloat() * 3f) * scale, i % 5 == 0)
        }
        planet(canvas, 61f / 864f * width, 1081f / 1536f * height, 44f * scale, false)
        planet(canvas, 787f / 864f * width, 1146f / 1536f * height, 44f * scale, true)
        scene = nextScene
        if (!textureAttempted && Build.VERSION.SDK_INT >= 33) {
            textureAttempted = true
            texture = try { Api33Nebula() } catch (_: RuntimeException) { null } catch (_: LinkageError) { null }
        }
    }

    fun draw(canvas: Canvas, elapsed: Long, forceCanvas: Boolean) {
        canvas.drawColor(0xff01040a.toInt())
        if (width == 0 || height == 0) return
        // The complete Canvas scene is always present; shader failure removes only an enhancement.
        scene?.let { canvas.drawBitmap(it, null, sceneBounds, bitmapPaint) }
        if (!forceCanvas && canvas.isHardwareAccelerated) {
            val activeTexture = texture
            if (activeTexture != null) {
                try {
                    activeTexture.time(elapsed)
                    for (i in galaxyArt.indices) {
                        val save = canvas.save()
                        try {
                            canvas.translate(centersX[i] / 864f * width, centersY[i] / 1536f * height)
                            canvas.rotate(rotations[i])
                            val size = radii[i] / 220f * scale
                            canvas.scale(size, size * if (i % 2 == 0) .86f else .7f)
                            activeTexture.draw(canvas)
                        } finally { canvas.restoreToCount(save) }
                    }
                } catch (_: RuntimeException) { texture = null } catch (_: LinkageError) { texture = null }
            }
        }
    }

    private fun galaxy(seed: Long, arms: Int): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(560, 560)
        canvas.translate(280f, 280f)
        val random = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(0f, 0f, 250f, intArrayOf(0x508dadde, 0x20216ca6, 0x00021426), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 250f, paint)
        val blueDust = Path()
        val warmDust = Path()
        val hotStars = Path()
        val coolStars = Path()
        for (arm in 0 until arms) {
            val cloud = Path()
            val warm = Path()
            val dark = Path()
            val filament = Path()
            for (j in 0..140) {
                val t = j / 140f
                val r = 8f + t * 225f
                val angle = arm * Math.PI * 2 / arms + ln(1.0 + t * 8) * 4.8 + sin(t * 41 + arm) * .04
                val jitter = sin(t * 54 + arm) * 2.3f + sin(t * 117) * 1.8f
                val x = cos(angle).toFloat() * (r + jitter)
                val y = sin(angle).toFloat() * (r + jitter)
                val wx = cos(angle - .085).toFloat() * r * .97f
                val wy = sin(angle - .085).toFloat() * r * .97f
                val dx = cos(angle + .055).toFloat() * r
                val dy = sin(angle + .055).toFloat() * r
                if (j == 0) { cloud.moveTo(x, y); warm.moveTo(wx, wy); dark.moveTo(dx, dy); filament.moveTo(x, y) }
                else { cloud.lineTo(x, y); warm.lineTo(wx, wy); dark.lineTo(dx, dy); filament.lineTo(x * 1.025f, y * 1.025f) }
                for (k in 0 until 24) {
                    val spread = (3 + t * 22) * random.nextGaussian().toFloat()
                    val a = angle + spread / max(r, 10f)
                    val rr = r + random.nextGaussian().toFloat() * (2 + t * 4)
                    val sx = cos(a).toFloat() * rr
                    val sy = sin(a).toFloat() * rr
                    val dot = .15f + random.nextFloat() * if (k < 2) .85f else .4f
                    if (k < 3) blueDust.addCircle(sx, sy, .6f + random.nextFloat() * 1.8f, Path.Direction.CW)
                    if (k == 3 && t < .9f) warmDust.addCircle(sx, sy, .4f + random.nextFloat() * 1.2f, Path.Direction.CW)
                    (if (k % 4 == 0) hotStars else coolStars).addCircle(sx, sy, dot, Path.Direction.CW)
                }
            }
            ink.stroke(canvas, cloud, 0xff135dba.toInt(), 32f, 13)
            ink.stroke(canvas, cloud, 0xff197bb7.toInt(), 19f, 28)
            ink.stroke(canvas, cloud, 0xff2596cc.toInt(), 8f, 40)
            ink.stroke(canvas, warm, 0xffefb266.toInt(), 12f, 30)
            ink.stroke(canvas, warm, 0xffffd59c.toInt(), 2f, 45)
            ink.stroke(canvas, dark, 0xff010713.toInt(), 4.2f, 190)
            ink.stroke(canvas, filament, 0xff7bceff.toInt(), .5f, 40)
        }
        ink.fill(canvas, blueDust, 0xff3a92d1.toInt(), 44)
        ink.fill(canvas, warmDust, 0xffeebd75.toInt(), 55)
        ink.fill(canvas, coolStars, 0xff74c8ff.toInt(), 162)
        ink.fill(canvas, hotStars, SanctuaryInk.GOLD, 205)
        for (i in 0 until 26) {
            val t = random.nextFloat()
            val r = 18f + t * 205f
            val angle = (i % arms) * Math.PI * 2 / arms + ln(1.0 + t * 8) * 4.8
            ink.flare(canvas, cos(angle).toFloat() * r, sin(angle).toFloat() * r, .65f + random.nextFloat() * 1.7f, i % 3 != 0)
        }
        paint.shader = RadialGradient(0f, 0f, 54f, intArrayOf(0xfffffff2.toInt(), 0xd8ffe0a1.toInt(), 0x50e79839, 0x00ba6a25), floatArrayOf(0f, .13f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 54f, paint)
        paint.shader = RadialGradient(-2f, -2f, 16f, intArrayOf(0xfffffff8.toInt(), 0xd8ffeec9.toInt(), 0x00ffe2a1), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(-2f, -2f, 16f, paint)
        ink.flare(canvas, 0f, 0f, 3f)
        picture.endRecording()
        return picture
    }

    private fun planet(canvas: Canvas, x: Float, y: Float, radius: Float, mirror: Boolean) {
        canvas.save(); canvas.translate(x, y)
        if (mirror) canvas.scale(-1f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(-radius * .3f, 0f, radius * 1.4f, intArrayOf(0xff000207.toInt(), 0xff020914.toInt(), 0xff194052.toInt()), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(0f, 0f, radius, p)
        val edge = Path().apply { addCircle(0f, 0f, radius, Path.Direction.CW) }
        ink.stroke(canvas, edge, SanctuaryInk.CYAN, radius * .05f, 90)
        val rim = Path().apply {
            moveTo(0f, -radius)
            cubicTo(radius * 1.333333f, -radius, radius * 1.333333f, radius, 0f, radius)
            cubicTo(radius * 1.2f, radius * .84f, radius * 1.2f, -radius * .84f, 0f, -radius)
            close()
        }
        ink.fill(canvas, rim, SanctuaryInk.GOLD, 235)
        ink.stroke(canvas, rim, 0xfffff3db.toInt(), .8f, 205)
        canvas.restore()
    }

    private interface NebulaTexture { fun time(elapsed: Long); fun draw(canvas: Canvas) }

    // The class is never loaded on pre-33 devices, including when preparing the Canvas fallback.
    @SuppressLint("NewApi") // Guarded at the sole construction site; no AndroidX dependency needed.
    private class Api33Nebula : NebulaTexture {
        private val shader = RuntimeShader("""
            uniform float time;
            float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
            float noise(float2 p) {
                float2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
                return mix(mix(hash(i),hash(i+float2(1,0)),f.x),mix(hash(i+float2(0,1)),hash(i+float2(1,1)),f.x),f.y);
            }
            float fbm(float2 p) {
                float value=0.0, weight=0.5;
                for(int i=0;i<4;i++) { value+=weight*noise(p); p=p*2.03+float2(7.2,3.1); weight*=0.5; }
                return value;
            }
            half4 main(float2 p) {
                float2 q=p/220.0;
                float r=length(q), a=atan(q.y,q.x);
                float n=fbm(q*9.0+float2(time*.006,0.0));
                float spiral=sin(a*4.0-log(r+0.025)*8.0+n*2.1);
                float arm=smoothstep(-.1,.85,spiral)*(.3+.7*fbm(q*38.0));
                float dust=smoothstep(.25,.7,noise(q*68.0))*smoothstep(-.4,.5,spiral);
                float envelope=(1.0-smoothstep(.65,1.15,r))*smoothstep(.025,.12,r);
                float light=max(0.0,arm-dust*.65)*envelope;
                float3 color=mix(float3(1.0,.67,.25),float3(.08,.48,1.0),smoothstep(.1,.6,r));
                float alpha=light*.23;
                return half4(color*alpha,alpha);
            }
        """.trimIndent())
        private val paint = Paint().apply { shader = this@Api33Nebula.shader }
        override fun time(elapsed: Long) { shader.setFloatUniform("time", (elapsed.coerceAtLeast(0) % 240000L) / 1000f) }
        override fun draw(canvas: Canvas) { canvas.drawRect(-255f, -255f, 255f, 255f, paint) }
    }
}

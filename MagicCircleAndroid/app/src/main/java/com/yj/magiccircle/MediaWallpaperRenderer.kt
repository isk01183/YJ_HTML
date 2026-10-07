@file:Suppress("DEPRECATION") // Movie provides deterministic frame seeking on every supported API, including 23–27.

package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt

/** A bounded, cached image. GIF frames are rasterized offscreen for reliable hardware Canvas output. */
@Suppress("DEPRECATION")
internal class MediaWallpaperRenderer(private val budgetBytes: Long = Long.MAX_VALUE, private val source: () -> InputStream) : AutoCloseable {
    private var bitmap: Bitmap? = null
    private var movie: Movie? = null
    private var frameCanvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var closed = false
    val animated get() = movie != null
    val imageWidth get() = bitmap?.width ?: 0
    val imageHeight get() = bitmap?.height ?: 0
    private val naturalBounds = RectF()

    fun prepare(width: Int, height: Int) {
        check(!closed)
        require(width > 0 && height > 0)
        if (bitmap == null) {
            val bytes = ByteArrayOutputStream().use { output ->
                // Bound peak memory before reading: growing buffer + its copy + decoder input.
                source().use { MediaValidation.copy(it, output, minOf(MediaValidation.MAX_BYTES, budgetBytes / 3)) }
                output.toByteArray()
            }
            require(bytes.size.toLong() + 1024 < budgetBytes) { "Image exceeds memory budget" }
            var pixelBudget = minOf(6_000_000L, (budgetBytes - bytes.size) / 4)
            require(pixelBudget >= 1024) { "Image exceeds memory budget" }
            if (MediaValidation.mime(bytes) == "image/gif") {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                // Movie keeps native decoded frames. Reserve two full source buffers before allocating our frame.
                val reserved = bounds.outWidth.toLong() * bounds.outHeight * 8 + bytes.size
                pixelBudget = minOf(1_500_000L, (budgetBytes-reserved)/4)
                require(pixelBudget >= 1024) { "GIF exceeds memory budget" }
                movie = checkNotNull(Movie.decodeByteArray(bytes, 0, bytes.size))
                val gif = movie!!
                require(MediaValidation.validDimensions(gif.width(), gif.height()))
                // Keep the per-frame software buffer below 1.5 MP; the screen composition stays hardware-backed.
                val scale = minOf(1.0, sqrt(pixelBudget.toDouble() / (gif.width().toDouble() * gif.height())))
                bitmap = Bitmap.createBitmap(maxOf(1, (gif.width() * scale).toInt()),
                    maxOf(1, (gif.height() * scale).toInt()), Bitmap.Config.ARGB_8888)
                frameCanvas = Canvas(bitmap!!)
            } else if (Build.VERSION.SDK_INT >= 28) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    require(MediaValidation.validDimensions(info.size.width, info.size.height))
                    val scale = minOf(1.0, sqrt(pixelBudget.toDouble() / (info.size.width.toDouble()*info.size.height)))
                    decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()), maxOf(1,(info.size.height*scale).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                require(options.outWidth > 0 && options.outHeight > 0)
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (options.outWidth.toLong() * options.outHeight / options.inSampleSize / options.inSampleSize > pixelBudget)
                    options.inSampleSize *= 2
                bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            }
        }
        val image = bitmap!!
        naturalBounds.set(0f,0f,image.width.toFloat(),image.height.toFloat())
        val scale = maxOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val left = (width - image.width * scale) / 2f
        val top = (height - image.height * scale) / 2f
        destination.set(left, top, width - left, height - top)
    }

    fun draw(canvas: Canvas, elapsedMs: Long, animated: Boolean, natural: Boolean = false) {
        if (closed) return
        val image = bitmap ?: return
        movie?.let { gif ->
            val frame = frameCanvas!!
            image.eraseColor(Color.TRANSPARENT)
            gif.setTime(if (animated) (elapsedMs.coerceAtLeast(0) % maxOf(1, gif.duration())).toInt() else 0)
            val save = frame.save()
            frame.scale(image.width.toFloat() / gif.width(), image.height.toFloat() / gif.height())
            gif.draw(frame, 0f, 0f)
            frame.restoreToCount(save)
        }
        if (!natural) canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(image, null, if(natural) naturalBounds else destination, paint)
    }

    override fun close() {
        closed = true
        frameCanvas = null
        bitmap?.recycle(); bitmap = null
        movie = null
    }
}

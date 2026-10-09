package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import java.io.ByteArrayOutputStream

/** Shared static/live entry point. Call prepare on size change and close when released. */
class WallpaperArtwork(val themeId: String, context: Context? = null,sceneOverride: ScreenScene?=null) : AutoCloseable {
    companion object {
        @JvmStatic @JvmOverloads fun thumbnail(themeId: String, context: Context? = null): ByteArray {
            val (width, height) = when (themeId) {
                "ref-W03" -> 512 to 910
                "ref-R01" -> 512 to 512
                else -> if(SceneRules.isSceneId(themeId)) 320 to 640 else error("Unsupported artwork")
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val artwork = WallpaperArtwork(themeId, context)
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

    private val library = context?.let {MediaLibrary.get(it)}
    private val snapshot = if(themeId.matches(Regex("upload-slot-[0-2]"))) UploadedWallpaperStore.snapshot(checkNotNull(context),themeId) else null
    private val scene = sceneOverride ?: snapshot?.scene ?: if(SceneRules.isSceneId(themeId))checkNotNull(library?.scene(themeId))else null
    init {require(scene?.vrm==null) {"VRM scenes require the live composition renderer"}}
    private val lease = if(snapshot==null) library?.leaseMedia(scene?.layers?.map {it.mediaId} ?: if(MediaValidation.isId(themeId))listOf(themeId)else emptyList())else null
    private val layered = scene?.let {s->LayeredSceneRenderer(s,{id->snapshot?.open(id) ?: checkNotNull(library).open(id,false)},LayeredSceneRenderer.budget(checkNotNull(context)))}
    private val media = when {
        layered!=null -> null
        MediaValidation.isId(themeId) -> MediaWallpaperRenderer(LayeredSceneRenderer.budget(checkNotNull(context))) { checkNotNull(library).open(themeId, false) }
        snapshot!=null -> MediaWallpaperRenderer(LayeredSceneRenderer.budget(checkNotNull(context))) { snapshot.openSingle() }
        else -> { ArtworkGeometry.designSize(themeId); null }
    }
    val animated get() = layered?.animated ?: media?.animated ?: true
    private val r01 = if (themeId == "ref-R01") R01Renderer() else null
    private val w03 = if (themeId == "ref-W03") W03Renderer() else null

    fun prepare(width: Int, height: Int) = when (themeId) {
        "ref-R01" -> r01!!.prepare(width, height)
        "ref-W03" -> w03!!.prepare(width, height)
        else -> if(layered!=null)layered.prepare(width,height)else checkNotNull(media).prepare(width, height)
    }

    /** Static callers use elapsedMs=0 and animated=false; live frame zero is identical. */
    fun draw(canvas: Canvas, elapsedMs: Long, animated: Boolean) = when (themeId) {
        "ref-R01" -> r01!!.draw(canvas, elapsedMs, animated)
        "ref-W03" -> w03!!.draw(canvas, elapsedMs, animated)
        else -> if(layered!=null)layered.draw(canvas,elapsedMs,animated)else checkNotNull(media).draw(canvas, elapsedMs, animated)
    }

    override fun close() {
        r01?.close();w03?.close();layered?.close();media?.close();lease?.close()
    }
}

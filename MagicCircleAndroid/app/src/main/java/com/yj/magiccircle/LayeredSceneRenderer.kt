package com.yj.magiccircle

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import java.io.Closeable
import java.io.InputStream

internal class LayeredSceneRenderer(scene: ScreenScene, private val openMedia: (String)->InputStream,
    private val budgetBytes: Long) : Closeable {
    private var layers=scene.layers.toList()
    private var character=scene.character
    private var avatar=character?.let {CharacterRenderer(it.definition).apply {prepare(130,220)}}
    private val characterMatrix=Matrix()
    private data class Cached(val image: MediaWallpaperRenderer, val matrix: Matrix=Matrix(), val inverse: Matrix=Matrix())
    private val images=linkedMapOf<String,Cached>()
    private val point=FloatArray(2)
    private var width=0
    private var height=0
    private var closed=false
    val animated get()=layers.any { it.visible && images[it.id]?.image?.animated==true } || (character?.visible==true && avatar?.animated==true)
    companion object {
        fun budget(context: Context): Long = minOf(48, (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass/4)*1024L*1024L
    }
    fun prepare(width: Int,height: Int) {
        check(!closed); require(width>0 && height>0)
        this.width=width; this.height=height
        try {
            val visible=layers.filter { it.visible }
            val share=budgetBytes/maxOf(1,layers.size)
            for(l in visible) if(!images.containsKey(l.id)) {
                val image=MediaWallpaperRenderer(share) { openMedia(l.mediaId) }
                images[l.id]=Cached(image)
                image.prepare(width,height)
            }
            updateMatrices()
        } catch(error: Throwable) { close(); throw error }
    }
    fun updateLayers(next: List<ImageLayer>) {
        check(!closed)
        require(next.map { it.id to it.mediaId } == layers.map { it.id to it.mediaId }) { "Rebuild for added or reordered assets" }
        layers=next.toList()
        updateMatrices()
    }
    fun updateCharacter(next: CharacterLayer?) {
        check(!closed)
        if(next==null) {avatar?.close();avatar=null}
        else if(avatar==null)avatar=CharacterRenderer(next.definition).apply {prepare(130,220)}
        else avatar!!.update(next.definition)
        character=next;updateMatrices()
    }
    private fun updateMatrices() {
        character?.let { c->
            val scale=c.width*minOf(width,height)/130f
            characterMatrix.reset();characterMatrix.postTranslate(-65f,-110f)
            characterMatrix.postScale(if(c.flipX)-scale else scale,scale);characterMatrix.postRotate(c.angle)
            characterMatrix.postTranslate(c.x*width,c.y*height)
        }
        for(l in layers) images[l.id]?.let { c ->
            val image=c.image
            val scale=l.width*minOf(width,height)/image.imageWidth
            c.matrix.reset()
            c.matrix.postTranslate(-image.imageWidth/2f,-image.imageHeight/2f)
            c.matrix.postScale(if(l.flipX) -scale else scale,scale)
            c.matrix.postRotate(l.angle)
            c.matrix.postTranslate(l.x*width,l.y*height)
            check(c.matrix.invert(c.inverse))
        }
    }
    fun draw(canvas: Canvas,elapsedMs: Long,animated: Boolean) {
        if(closed) return
        canvas.drawColor(Color.BLACK)
        for(i in 0..layers.size) {
            if(character?.visible==true && character?.beforeImage==i) {
                val save=canvas.save();canvas.concat(characterMatrix);avatar?.draw(canvas,elapsedMs,animated);canvas.restoreToCount(save)
            }
            val l=layers.getOrNull(i) ?: continue
            if(l.visible) images[l.id]?.let { c ->
            val save=canvas.save(); canvas.concat(c.matrix)
            c.image.draw(canvas,elapsedMs,animated,true)
            canvas.restoreToCount(save)
        }
        }
    }
    fun hitTest(x: Float,y: Float): String? {
        for(i in layers.indices.reversed()) {
            val l=layers[i]; if(!l.visible) continue
            val c=images[l.id] ?: continue
            point[0]=x; point[1]=y; c.inverse.mapPoints(point)
            if(point[0]>=0 && point[0]<=c.image.imageWidth && point[1]>=0 && point[1]<=c.image.imageHeight) return l.id
        }
        return null
    }
    override fun close() { if(closed) return; closed=true; images.values.forEach { it.image.close() }; images.clear();avatar?.close();avatar=null }
}

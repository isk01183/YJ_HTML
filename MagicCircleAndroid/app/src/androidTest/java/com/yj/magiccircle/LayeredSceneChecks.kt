package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

object LayeredSceneChecks {
    fun run() {
        val bitmap=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888)
        for(y in 0 until 80) for(x in 0 until 160) bitmap.setPixel(x,y,if(x<80) Color.RED else Color.BLUE)
        val bytes=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        bitmap.recycle()
        val layer=ImageLayer("upper","image",.5f,.5f,.5f,0f,false,true)
        val scene=ScreenScene("check","Check",ScenePurpose.CHARGING,listOf(layer))
        val renderer=LayeredSceneRenderer(scene,{bytes.inputStream()},8L*1024*1024)
        val target=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        try {
            renderer.prepare(320,320); renderer.prepare(320,320)
            renderer.draw(Canvas(target),0,false)
            check(renderer.hitTest(160f,160f)=="upper" && renderer.hitTest(0f,0f)==null)
            check(target.getPixel(120,160)==Color.RED && target.getPixel(200,160)==Color.BLUE)
            renderer.updateLayers(listOf(layer.copy(flipX=true)))
            renderer.draw(Canvas(target),0,false)
            check(target.getPixel(120,160)==Color.BLUE && target.getPixel(200,160)==Color.RED)
            renderer.updateLayers(listOf(layer.copy(angle=90f)))
            renderer.draw(Canvas(target),0,false)
            check(target.getPixel(160,120)==Color.RED && target.getPixel(160,200)==Color.BLUE)
            check(renderer.hitTest(220f,160f)==null)
            renderer.updateLayers(listOf(layer.copy(visible=false)))
            renderer.draw(Canvas(target),0,false)
            check(target.getPixel(160,160)==Color.BLACK && renderer.hitTest(160f,160f)==null)
        } finally { renderer.close(); renderer.close(); target.recycle() }
        val transparent=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888)
        transparent.setPixel(0,0,Color.WHITE)
        val alphaBytes=ByteArrayOutputStream().also {transparent.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();transparent.recycle()
        val alphaTarget=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        try {LayeredSceneRenderer(scene,{alphaBytes.inputStream()},8L*1024*1024).use { r->
            r.prepare(320,320);alphaTarget.eraseColor(Color.GREEN)
            r.drawImages(Canvas(alphaTarget),0,false,0,1,false)
            check(alphaTarget.getPixel(160,160)==Color.GREEN) {"Transparent foreground erased the back layer"}
        }} finally {alphaTarget.recycle()}
        LayeredSceneRenderer(scene.copy(layers=listOf(layer.copy(visible=false))),{error("Hidden image decoded")},4096).use { it.prepare(320,320) }
        check(runCatching { LayeredSceneRenderer(scene,{bytes.inputStream()},1).use { it.prepare(320,320) } }.isFailure)
        var opens=0
        val both=scene.copy(layers=listOf(layer,layer.copy(id="front",x=.2f,width=.1f)))
        val composed=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        try { LayeredSceneRenderer(both,{opens++;bytes.inputStream()},8L*1024*1024).use { r->
            r.prepare(320,320)
            check(opens==1) {"Same media decoded for each layer"}
            composed.eraseColor(Color.GREEN)
            r.drawImages(Canvas(composed),0,false,0,0,false)
            check(composed.getPixel(0,0)==Color.GREEN)
            r.drawImages(Canvas(composed),0,false,0,1,true)
            r.drawImages(Canvas(composed),0,false,1,2,false)
            check(composed.getPixel(120,160)==Color.RED && composed.getPixel(200,160)==Color.BLUE)
            check(composed.getPixel(55,160)==Color.RED && composed.getPixel(0,0)==Color.BLACK)
            check(opens==1)
            check(runCatching {r.drawImages(Canvas(composed),0,false,2,1,false)}.isFailure)
        }} finally {composed.recycle()}
    }
}

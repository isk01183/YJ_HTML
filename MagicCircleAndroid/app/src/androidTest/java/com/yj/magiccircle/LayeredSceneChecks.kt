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
        LayeredSceneRenderer(scene.copy(layers=listOf(layer.copy(visible=false))),{error("Hidden image decoded")},4096).use { it.prepare(320,320) }
        check(runCatching { LayeredSceneRenderer(scene,{bytes.inputStream()},1).use { it.prepare(320,320) } }.isFailure)
    }
}

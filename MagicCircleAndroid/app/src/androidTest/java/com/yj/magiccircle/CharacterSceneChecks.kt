package com.yj.magiccircle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.util.UUID

object CharacterSceneChecks {
    fun run(context: Context) {
        val root=File(context.cacheDir,"character-scene-${UUID.randomUUID()}").apply {check(mkdirs())}
        val definition=CharacterRules.defaults(UUID.randomUUID().toString(),"Snapshot")
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Character",ScenePurpose.WALLPAPER,emptyList(),CharacterLayer(definition))
        val landscape=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888)
        val tall=scene.copy(character=CharacterLayer(definition.copy(body=BodyProportions(heightCm=200f),motion=CharacterMotion.WAVE)))
        LayeredSceneRenderer(tall,{error("No media")},1024*1024).use { r->
            r.prepare(800,400);r.draw(Canvas(landscape),1100,true)
            for(x in 0 until 800)check(landscape.getPixel(x,0)==android.graphics.Color.BLACK && landscape.getPixel(x,399)==android.graphics.Color.BLACK) {"New character is clipped in landscape"}
        }
        landscape.recycle()
        check(SceneData.readScene(SceneData.sceneJson(scene))==scene)
        val old=SceneData.sceneJson(scene.copy(character=null));old.remove("character")
        check(SceneData.readScene(old).character==null)
        UploadedWallpaperStore.stageFiles(root,"upload-slot-0",scene.id,scene){error("Character must not request image assets")}
        val snapshot=UploadedWallpaperStore.snapshot(root,"upload-slot-0")
        val store=CharacterStore(File(root,"characters"));store.save(definition);store.save(definition.copy(name="Changed"));store.delete(definition.id)
        check(snapshot.scene==scene)
        val a=Bitmap.createBitmap(260,440,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(260,440,Bitmap.Config.ARGB_8888)
        LayeredSceneRenderer(snapshot.scene!!,{error("No media")},1024*1024).use { r->
            r.prepare(260,440);r.draw(Canvas(a),0,false);r.draw(Canvas(b),0,true);check(a.sameAs(b))
            r.draw(Canvas(b),900,true);check(!a.sameAs(b))
            r.updateCharacter(scene.character!!.copy(visible=false));r.draw(Canvas(b),0,false);check(!a.sameAs(b))
        }
        val png=File(root,"red.png");b.eraseColor(0xffff0000.toInt());png.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
        val media=UUID.randomUUID().toString()
        val withImage=scene.copy(layers=listOf(ImageLayer("red",media,.5f,.5f,4f,0f,false,true)))
        LayeredSceneRenderer(withImage,{png.inputStream()},1024*1024).use { r->
            r.prepare(260,440);r.draw(Canvas(a),0,false);check(a.getPixel(130,220)==0xffff0000.toInt())
            r.updateCharacter(scene.character!!.copy(beforeImage=1));r.draw(Canvas(b),0,false);check(!a.sameAs(b))
        }
        a.recycle();b.recycle()
    }
    fun gesture(context: Context) {
        val c=CharacterRules.defaults(UUID.randomUUID().toString(),"Gesture")
        val s=ScreenScene("scene-${UUID.randomUUID()}","Gesture",ScenePurpose.WALLPAPER,emptyList(),CharacterLayer(c))
        val view=ScreenEditorView(context)
        view.setDraft(EditorDraft(s.id,s,emptyList()));view.selectedCharacter=true
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(800,1073741824));view.layout(0,0,400,800)
        fun event(action:Int,x:Float,y:Float) {val e=android.view.MotionEvent.obtain(0,0,action,x,y,0);view.onTouchEvent(e);e.recycle()}
        event(0,200f,400f);event(2,240f,440f);event(1,240f,440f)
        check(view.currentDraft().scene!!.character!!.x>.5f)
        check(view.selectedLayer==null && view.selectedField==null)
        view.close()
    }
}

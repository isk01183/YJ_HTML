package com.yj.magiccircle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.util.UUID

object SceneWallpaperChecks {
    fun run(context: Context) {
        val root=File(context.cacheDir,"wall-scene-${UUID.randomUUID()}").apply {check(mkdirs())}
        val id=UUID.randomUUID().toString()
        val source=File(root,"source")
        val png=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply {eraseColor(0xffff0000.toInt())}
        source.outputStream().use {png.compress(Bitmap.CompressFormat.PNG,100,it)};png.recycle()
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Snapshot",ScenePurpose.WALLPAPER,listOf(ImageLayer("one",id,.5f,.5f,1f,0f,false,true)))
        check(runCatching { UploadedWallpaperStore.stageFiles(root,"upload-slot-0",id,scene.copy(purpose=ScenePurpose.CHARGING)){source.inputStream()} }.isFailure)
        UploadedWallpaperStore.stageFiles(root,"upload-slot-0",id,scene){source.inputStream()}
        UploadedWallpaperStore.stageFiles(root,"upload-slot-1",id,scene){source.inputStream()}
        val before=(0..1).map {File(root,"upload-slot-$it.json").readBytes().toList()}
        val snapshot=UploadedWallpaperStore.snapshot(root,"upload-slot-0")
        UploadedWallpaperStore.stageFiles(root,"upload-slot-2",id,scene){source.inputStream()}
        check(before==(0..1).map {File(root,"upload-slot-$it.json").readBytes().toList()})
        val candidate=File(root,"upload-slot-2.json").readBytes()
        check(runCatching {UploadedWallpaperStore.stageFiles(root,"upload-slot-2",id,scene){throw java.io.IOException("injected")}}.isFailure)
        check(candidate.contentEquals(File(root,"upload-slot-2.json").readBytes()))
        source.writeBytes(byteArrayOf(1,2,3)) // Published pixels no longer depend on the library.
        val output=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888)
        LayeredSceneRenderer(snapshot.scene!!,{snapshot.open(it)},1024*1024).use {
            it.prepare(40,40);it.draw(Canvas(output),0,false)
            check(output.getPixel(20,20)==0xffff0000.toInt())
        }
        output.recycle()
    }
}

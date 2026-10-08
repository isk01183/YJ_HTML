package com.yj.magiccircle

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

object SceneStorageChecks {
    fun run(context: Context) {
        val root=File(context.cacheDir,"scene-check-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val media=UUID.randomUUID().toString()
        val original="""{"version":2,"activationRevision":1,"media":[{"id":"$media","name":"A","mime":"image/png"}],"hidden":[],"selected":"classic","pendingDeletes":[],"tabs":[],"migrationNotice":""}"""
        val file=File(root,"media-library.json").apply { writeText(original) }
        File(root,"imported-media").mkdirs()
        val image=File(root,"imported-media/$media").apply { writeBytes(byteArrayOf(1,2,3)) }
        var lib=MediaLibrary(context,root,"classic")
        check(lib.isReadable())
        check(File(root,"media-library.v2-recovery.json").readText()==original)
        val scene=ScreenScene("scene-${UUID.randomUUID()}","별",ScenePurpose.CHARGING,listOf(ImageLayer("a",media,.2f,.7f,.8f,90f,true,true)))
        val info=listOf(InfoPlacement(InfoField.BATTERY,.5f,.4f,true))
        val draft=EditorDraft(scene.id,scene,info)
        lib.saveEditorDraft(draft)
        check(runCatching { lib.remove(media) }.isFailure && image.exists())
        lib=MediaLibrary(context,root,"classic")
        check(lib.editorDraft(scene.id)==draft && lib.selected()=="classic")
        lib.saveScene(scene,info)
        check(lib.editorDraft(scene.id)==null)
        lib.select(scene.id)
        lib.setDurationMs(1000)
        val restored=MediaLibrary(context,root,"classic")
        check(restored.scene(scene.id)==scene && restored.chargeInfo(scene.id)==info && restored.durationMs()==1000)
        check(restored.selected()==scene.id)
        val wallpaper=scene.copy(id="scene-${UUID.randomUUID()}",purpose=ScenePurpose.WALLPAPER)
        restored.saveScene(wallpaper,emptyList())
        restored.select(wallpaper.id)
        check(restored.selected()==scene.id) {"A wallpaper-only scene cannot become the charging selection"}
        restored.removeScene(wallpaper.id)
        check(runCatching { restored.remove(media) }.isFailure)
        restored.removeScene(scene.id)
        val lease=restored.leaseMedia(listOf(media))
        check(runCatching { restored.remove(media) }.isFailure)
        lease.close(); lease.close()
        restored.remove(media)
        check(!image.exists())
        check(JSONObject(file.readText()).getInt("version")==4)
        val legacyScene=wallpaper.copy(layers=emptyList())
        val legacy=JSONObject(file.readText()).put("version",3).put("editor",SceneData(scenes=mapOf(wallpaper.id to legacyScene),duration=3000).json()).toString()
        file.writeText(legacy)
        val upgraded=MediaLibrary(context,root,"classic")
        check(upgraded.isReadable() && upgraded.scene(wallpaper.id)==legacyScene && upgraded.durationMs()==3000)
        check(File(root,"media-library.v3-recovery.json").readText()==legacy)
    }
}

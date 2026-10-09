package com.yj.magiccircle

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object VrmAvatarSceneChecks {
    fun run(test: Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"))
        val add=ScreenEditorActivity::class.java.getDeclaredMethod("addAvatar",VrmAvatarDefinition::class.java).apply {isAccessible=true}
        val context=test.targetContext
        val root=File(context.cacheDir,"avatar-scene-${UUID.randomUUID()}").apply {check(mkdirs())}
        val media=UUID.randomUUID().toString()
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply {eraseColor(0xff183347.toInt())}
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        File(root,"imported-media").mkdirs();File(root,"imported-media/$media").writeBytes(bytes)
        File(root,"media-library.json").writeText("""{"version":2,"activationRevision":1,"media":[{"id":"$media","name":"Fixture","mime":"image/png"}],"hidden":[],"selected":"classic","pendingDeletes":[],"tabs":[],"migrationNotice":""}""")
        var library=MediaLibrary(context,root,"classic")
        val avatars=VrmAvatarStore(File(root,"avatars"))
        val a=avatars.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Scene avatar",VrmAvatarRules.original().copy(dye=VrmDye("#204EFF","#ED91B9"))),null)
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Appearance snapshot",ScenePurpose.WALLPAPER,
            listOf(ImageLayer("background",media,.5f,.5f,4f,0f,false,true)),vrm=VrmSceneLayer(a.appearance.modelId,beforeImage=1,avatar=a))
        library.saveScene(scene,null)
        library=MediaLibrary(context,root,"classic")
        check(library.scene(scene.id)==scene) {"Reload lost saved appearance"}
        val b=avatars.save(a.copy(appearance=a.appearance.copy(dye=VrmDye("#EE2038"))),a.revision)
        check(library.scene(scene.id)==scene && scene.vrm!!.avatar==a && b.revision==2)
        val snapshots=File(root,"snapshots")
        val published=VrmWallpaperStore.stageFiles(snapshots,"vrm-slot-0",scene){bytes.inputStream()}
        check(JSONObject(File(snapshots,"vrm-slot-0.json").readText()).getInt("version")==3)
        check(VrmWallpaperStore.snapshot(snapshots,"vrm-slot-0")==published)
        // failedApplyPreservesPreviousSnapshot: I/O and mismatched model must not publish.
        val prior=File(snapshots,"vrm-slot-0.json").readBytes()
        check(runCatching {VrmWallpaperStore.stageFiles(snapshots,"vrm-slot-0",scene){error("read interrupted")}}.isFailure)
        check(runCatching {VrmWallpaperStore.stageFiles(snapshots,"vrm-slot-0",scene.copy(vrm=scene.vrm!!.copy(modelId="a".repeat(64)))){bytes.inputStream()}}.isFailure)
        check(File(snapshots,"vrm-slot-0.json").readBytes().contentEquals(prior))
        check(VrmWallpaperStore.snapshot(snapshots,"vrm-slot-0").scene!!.vrm!!.avatar==a)
        val old=SceneData.sceneJson(scene.copy(vrm=scene.vrm!!.copy(avatar=null)))
        old.getJSONObject("vrm").remove("avatar");check(SceneData.readScene(old).vrm!!.avatar==null)
        val legacy=JSONObject(String(prior)).put("version",2).put("scene",old)
        File(snapshots,"vrm-slot-1.json").writeText(legacy.toString())
        check(VrmWallpaperStore.snapshot(snapshots,"vrm-slot-1").scene!!.vrm!!.avatar==null)
        VrmWallpaperStore.stageFiles(snapshots,"vrm-slot-2",a.appearance.modelId,VrmPlacement())
        check(VrmWallpaperStore.snapshot(snapshots,"vrm-slot-2").scene==null)
        val mediaField=MediaLibrary::class.java.getDeclaredField("instance").apply {isAccessible=true}
        val modelField=VrmModelStore::class.java.getDeclaredField("instance").apply {isAccessible=true}
        val priorMedia=mediaField.get(null);val models=VrmModelStore.get(context)
        mediaField.set(null,library)
        var activity: ScreenEditorActivity?=null;var host: VrmSceneView?=null
        fun main(action:()->Unit){var error: Throwable?=null;test.runOnMainSync {try{action()}catch(e: Throwable){error=e}};error?.let {throw it}}
        fun waitFor(label: String,condition:()->Boolean){val end=SystemClock.elapsedRealtime()+90_000;var done=false;while(!done&&SystemClock.elapsedRealtime()<end){main {done=condition()};if(!done)Thread.sleep(100)};check(done){label}}
        fun appearance(): VrmAvatarAppearance? {
            val latch=CountDownLatch(1);var raw="null"
            main {host!!.webView!!.evaluateJavascript("window.vrmPreview.info.appearance"){raw=it;latch.countDown()}}
            check(latch.await(20,TimeUnit.SECONDS));return if(raw=="null")null else VrmAvatarRules.readAppearance(JSONObject(raw))
        }
        try {
            activity=test.startActivitySync(Intent(context,ScreenEditorActivity::class.java).putExtra("themeId",scene.id).putExtra("scenePurpose","WALLPAPER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
            val editorField=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}
            val busy=ScreenEditorActivity::class.java.getDeclaredField("busy").apply {isAccessible=true}
            val editor=editorField.get(activity) as ScreenEditorView
            // missingAvatarModelPreservesDraft: an empty model store must not replace the current scene.
            main {editor.setRenderingEnabled(false)}
            val draft=editor.currentDraft()
            modelField.set(null,VrmModelStore(File(root,"missing-models")))
            main {add.invoke(activity,b)};waitFor("Missing model check did not finish"){!busy.getBoolean(activity)}
            check(editor.currentDraft()==draft)
            modelField.set(null,models)
            main {add.invoke(activity,b)};waitFor("Saved avatar selection failed"){!busy.getBoolean(activity)}
            val updated=editor.currentDraft().scene!!
            check(updated.vrm!!.avatar==b && updated.layers==scene.layers && updated.vrm.placement==scene.vrm!!.placement)
            library.saveScene(updated,null);check(MediaLibrary(context,root,"classic").scene(scene.id)==updated)
            // Same host used by preview and wallpaper service; no actual system wallpaper is changed.
            var failure: VrmFailure?=null
            main {host=VrmSceneView(activity!!,published.scene!!,{models.openModel(a.appearance.modelId)},{published.open(it)},{},{failure=it});activity!!.setContentView(host)}
            waitFor("Snapshot renderer failed: $failure"){host!!.ready || failure!=null};check(failure==null)
            check(appearance()==a.appearance)
            val sameWeb=host!!.webView
            main {host!!.updateScene(updated)}
            Thread.sleep(2500)
            main {host!!.webView?.evaluateJavascript("window.vrmPreview.info") {android.util.Log.i("VrmChecks","SCENE_UPDATE_INFO $it")}}
            waitFor("Updated colors not rendered"){host!!.ready || failure!=null};check(failure==null){"Updated scene failed: $failure"}
            check(host!!.webView===sameWeb && appearance()==b.appearance)
            main {host!!.updateScene(updated.copy(vrm=updated.vrm.copy(avatar=null)))}
            waitFor("Raw model did not restore"){host!!.ready || failure!=null};check(failure==null && appearance()==null)
            main {host!!.updateScene(published.scene!!)};waitFor("Snapshot did not restore"){host!!.ready || failure!=null};check(failure==null && appearance()==a.appearance)
            val output=File(context.getExternalFilesDir(null),"avatar-scene-${UUID.randomUUID()}.png")
            VrmHairChecks.capture(test,host!!.webView!!,output)
            android.util.Log.i("VrmChecks","AVATAR_SCENE_CAPTURE ${output.absolutePath}")
        } finally {main {host?.close();activity?.finish()};test.waitForIdleSync();modelField.set(null,models);mediaField.set(null,priorMedia)}
    }
}

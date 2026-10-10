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
    fun proof(test: Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"))
        val context=test.targetContext
        val isolated=object: android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir()=File(context.cacheDir,"scene-proof-$runId")
            private val runId=UUID.randomUUID().toString()
        }
        val store=VrmHairPartStore.get(context);val part=store.prepare(128L*1024*1024).first()
        val appearance=store.compose(part.id,128L*1024*1024).appearance().copy(parts=mapOf("hair" to "f".repeat(64)))
        val avatar=VrmAvatarDefinition(UUID.randomUUID().toString(),1,"Missing proof",appearance)
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Missing proof",ScenePurpose.WALLPAPER,emptyList(),vrm=VrmSceneLayer(appearance.modelId,avatar=avatar))
        val request=VrmWallpaperStore.beginApplication(isolated);val before=VrmWallpaperStore.pendingApplication(isolated)
        val components=(0..2).map {VrmWallpaperStore.component(context,"vrm-slot-$it")}
        val enabled=components.map {context.packageManager.getComponentEnabledSetting(it)}
        try {
            check(runCatching {VrmWallpaperStore.stage(isolated,request,scene)}.isFailure){"Missing proof published wallpaper"}
            check(VrmWallpaperStore.pendingApplication(isolated)==before){"Bad proof changed pending wallpaper"}
        }finally {
            components.forEachIndexed {i,c->context.packageManager.setComponentEnabledSetting(c,enabled[i],android.content.pm.PackageManager.DONT_KILL_APP)}
            VrmWallpaperStore.finishApplication(isolated,request)
        }
    }
    /** Separate instrumentation process reopens a fixture left by run(); never uses the personal library. */
    fun reopen(test: Instrumentation,fixture: String) {
        require(fixture.startsWith("avatar-scene-") && UUID.fromString(fixture.removePrefix("avatar-scene-")).toString()==fixture.removePrefix("avatar-scene-"))
        val context=test.targetContext;val root=File(context.cacheDir,fixture)
        val library=MediaLibrary(context,root,"classic");val scene=library.scenes().single()
        val avatar=scene.vrm!!.avatar!!;check(avatar.revision==2 && avatar.appearance.dye.hair=="#EE2038")
        val snapshot=VrmWallpaperStore.snapshot(File(root,"snapshots"),"vrm-slot-0")
        check(snapshot.scene!!.vrm!!.avatar!!.revision==1 && snapshot.scene.vrm!!.avatar!!.appearance.dye.hair=="#204EFF")
        val field=MediaLibrary::class.java.getDeclaredField("instance").apply {isAccessible=true};val previous=field.get(null);field.set(null,library)
        var activity: ScreenEditorActivity?=null
        try {
            activity=test.startActivitySync(Intent(context,ScreenEditorActivity::class.java).putExtra("themeId",scene.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
            val editor=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}.get(activity) as ScreenEditorView
            val hostField=ScreenEditorView::class.java.getDeclaredField("vrmHost").apply {isAccessible=true}
            var host: VrmSceneView?=null;var ready=false;val end=SystemClock.elapsedRealtime()+90_000
            while(!ready&&SystemClock.elapsedRealtime()<end){test.runOnMainSync {host=hostField.get(editor) as? VrmSceneView;ready=host?.ready==true};Thread.sleep(100)}
            check(ready){"Saved scene did not reopen after process restart"}
            val latch=CountDownLatch(1);var raw="null"
            test.runOnMainSync {host!!.webView!!.evaluateJavascript("window.vrmPreview.info.appearance"){raw=it;latch.countDown()}}
            check(latch.await(20,TimeUnit.SECONDS));check(VrmAvatarRules.readAppearance(JSONObject(raw))==avatar.appearance)
        } finally {test.runOnMainSync {activity?.finish()};test.waitForIdleSync();field.set(null,previous)}
    }
    fun run(test: Instrumentation,parts: Boolean=false) {
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
        val selected=VrmModelStore.get(context).selected()
        val pending=VrmWallpaperStore.pendingApplication(context)
        val manager=android.app.WallpaperManager.getInstance(context)
        val home=manager.wallpaperInfo?.component;val lock=manager.getWallpaperInfo(android.app.WallpaperManager.FLAG_LOCK)?.component
        val legacyRoot=File(root,"legacy")
        val legacyAvatars=VrmAvatarStore(File(legacyRoot,"avatars"))
        val legacyAvatar=legacyAvatars.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Legacy",VrmAvatarRules.original()),null)
        legacyAvatars.saveDraft(legacyAvatar.copy(name="Draft"))
        val legacyScene=ScreenScene("scene-${UUID.randomUUID()}","Legacy",ScenePurpose.WALLPAPER,emptyList(),vrm=VrmSceneLayer(legacyAvatar.appearance.modelId,avatar=legacyAvatar))
        val legacyLibrary=MediaLibrary(context,File(legacyRoot,"library"),"classic");legacyLibrary.saveScene(legacyScene,null)
        VrmWallpaperStore.stageFiles(File(legacyRoot,"wallpapers"),"vrm-slot-0",legacyScene){error("No images")}
        val legacyBytes=legacyRoot.walkTopDown().filter {it.isFile}.associate {it to it.readBytes()}
        val partStore=VrmHairPartStore.get(context)
        val hair=if(parts)partStore.prepare(128L*1024*1024).single {it.styleId=="e-hair02"}else null
        val original=if(parts)partStore.prepare(128L*1024*1024).single {it.styleId=="e-original"}else null
        val initial=if(hair==null)VrmAvatarRules.original()else partStore.compose(hair.id,128L*1024*1024).appearance()
        val a=avatars.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Scene avatar",initial.copy(dye=VrmDye("#204EFF","#ED91B9"))),null)
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Appearance snapshot",ScenePurpose.WALLPAPER,
            listOf(ImageLayer("background",media,.5f,.5f,4f,0f,false,true)),vrm=VrmSceneLayer(a.appearance.modelId,beforeImage=1,avatar=a))
        library.saveScene(scene,null)
        library=MediaLibrary(context,root,"classic")
        check(library.scene(scene.id)==scene) {"Reload lost saved appearance"}
        val b=avatars.save(a.copy(appearance=a.appearance.copy(dye=VrmDye("#EE2038"))),a.revision)
        if(original!=null)avatars.save(b.copy(appearance=partStore.compose(original.id,128L*1024*1024).appearance()),b.revision)
        check(library.scene(scene.id)==scene && scene.vrm!!.avatar==a && b.revision==2)
        check(legacyBytes.all {(file,bytes)->file.readBytes().contentEquals(bytes)}){"Legacy bytes changed"}
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
            if(parts) {
                val bad=b.copy(appearance=b.appearance.copy(parts=mapOf("hair" to "f".repeat(64))))
                val before=editor.currentDraft()
                main {add.invoke(activity,bad)};waitFor("Bad proof did not finish"){!busy.getBoolean(activity)}
                check(editor.currentDraft()==before){"Bad proof replaced the scene"}
                main {check(runCatching {host!!.updateScene(updated.copy(vrm=updated.vrm.copy(avatar=bad)))}.isFailure)}
                val partField=VrmHairPartStore::class.java.getDeclaredField("instance").apply {isAccessible=true};val actual=partField.get(null)
                try {
                    partField.set(null,VrmHairPartStore(File(root,"missing-parts"),models))
                    main {host!!.close();failure=null;host=VrmSceneView(activity!!,published.scene!!,{models.openModel(a.appearance.modelId)},{published.open(it)},{},{failure=it});activity!!.setContentView(host)}
                    waitFor("Missing proof was not rejected"){failure!=null||host!!.ready};check(failure==VrmFailure.MODEL&&!host!!.ready)
                }finally {partField.set(null,actual)}
                main {host!!.close();failure=null;host=VrmSceneView(activity!!,published.scene!!,{models.openModel(a.appearance.modelId)},{published.open(it)},{},{failure=it});activity!!.setContentView(host)}
                waitFor("Restored proof did not render"){host!!.ready||failure!=null};check(failure==null&&appearance()==a.appearance)
            }
            check(VrmModelStore.get(context).selected()==selected&&VrmWallpaperStore.pendingApplication(context)==pending)
            check(manager.wallpaperInfo?.component==home&&manager.getWallpaperInfo(android.app.WallpaperManager.FLAG_LOCK)?.component==lock)
            val output=File(context.getExternalFilesDir(null),"avatar-scene-${UUID.randomUUID()}.png")
            VrmHairChecks.capture(test,host!!.webView!!,output)
            android.util.Log.i("VrmChecks","AVATAR_SCENE_CAPTURE ${output.absolutePath}")
            android.util.Log.i("VrmChecks","AVATAR_SCENE_FIXTURE ${root.name}")
        } finally {main {host?.close();activity?.finish()};test.waitForIdleSync();modelField.set(null,models);mediaField.set(null,priorMedia)}
    }
}

package com.yj.magiccircle

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object VrmSceneChecks {
    fun live(test: Instrumentation,seconds: Int=0,modelIndex: Int=0,soak: Int=0) {
        check(android.os.Build.PRODUCT.startsWith("sdk_") && android.os.Build.VERSION.SDK_INT>=29)
        val context=test.targetContext;val library=MediaLibrary.get(context)
        val model=VrmModelStore.get(context).entries()[modelIndex]
        val media=fixtureImages(context)
        val layers=listOf(ImageLayer("jpg",media[0],.5f,.5f,1f,0f,false,true))+
            (0..4).map {i->ImageLayer("png-$i",media[1],if(i==0).5f else if(i%2==0).9f else .1f,if(i==0).5f else if(i>2).85f else .15f,if(i==0)1f else .15f,0f,false,true)}+
            listOf(ImageLayer("gif-a",media[2],.08f,.5f,.05f,0f,false,true),ImageLayer("gif-b",media[2],.92f,.5f,.05f,0f,false,true))
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Live composition check",ScenePurpose.WALLPAPER,layers,vrm=VrmSceneLayer(model.id,beforeImage=1))
        try {library.saveScene(scene,emptyList())}catch(e: Exception){media.forEach(library::remove);throw e}
        val manager=android.app.WallpaperManager.getInstance(context)
        val old=VrmWallpaperStore.key(context,manager.wallpaperInfo?.component)?.let {it to VrmWallpaperStore.snapshot(context,it)}
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var controller: WallpaperController?=null
        try {
            main(test){
                controller=MainActivity::class.java.getDeclaredField("wallpaperController").apply {isAccessible=true}.get(activity) as WallpaperController
                controller!!.show(scene.id,"home")
                check(button("vrm-compose-apply")?.isEnabled==false) {"VRM composition did not gate Apply on its first frame"}
            }
            waitFor(test){button("vrm-compose-apply")?.isEnabled==true}
            main(test){button("vrm-compose-apply")!!.performClick()}
            waitFor(test){VrmWallpaperStore.pendingApplication(context)?.launched==true}
            val p=VrmWallpaperStore.pendingApplication(context)!!
            val candidate=VrmWallpaperStore.snapshot(context,p.slot!!)
            check(candidate.scene==scene && candidate.modelId==model.id)
            check(p.slot!=old?.first) {"Candidate replaced the active wallpaper slot"}
            library.saveScene(scene.copy(vrm=scene.vrm!!.copy(placement=VrmPlacement(.3f))),emptyList())
            check(VrmWallpaperStore.snapshot(context,p.slot)==candidate)
            android.util.Log.i("VrmChecks","SYSTEM_PREVIEW_READY slot=${p.slot} model=${model.format}")
            if(seconds>0)waitFor(test,seconds.coerceIn(10,90)*1000L){VrmWallpaperStore.pendingApplication(context)==null}
            else {
                test.uiAutomation.executeShellCommand("input keyevent 4").close()
                waitFor(test){VrmWallpaperStore.pendingApplication(context)==null}
                old?.let {check(VrmWallpaperStore.snapshot(context,it.first)==it.second)}
            }
            check(VrmWallpaperStore.pendingApplication(context)==null)
            library.removeScene(scene.id);media.forEach(library::remove)
            check(VrmWallpaperStore.snapshot(context,p.slot)==candidate)
            candidate.scene!!.layers.forEach {candidate.open(it.mediaId).use {stream->check(stream.read()!=-1)}}
            // Instrumentation shutdown force-stops wallpaper services, so apply and verify in one process.
            if(seconds>0) {
                if(soak>0)VrmWallpaperChecks.live(test,soak)
                VrmWallpaperChecks.live(test)
            }
        } finally {main(test){controller?.close();activity.finish()};library.removeScene(scene.id);media.forEach {runCatching {library.remove(it)}}}
    }
    private fun fixtureImages(context: android.content.Context): List<String> {
        val library=MediaLibrary.get(context)
        val bitmap=Bitmap.createBitmap(360,720,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader=android.graphics.LinearGradient(0f,0f,360f,720f,intArrayOf(Color.rgb(15,33,64),Color.rgb(71,39,93)),null,android.graphics.Shader.TileMode.CLAMP)
        canvas.drawPaint(paint);paint.shader=null;paint.color=0xffb7a4dc.toInt()
        repeat(48){i->canvas.drawCircle(((i*73)%360).toFloat(),((i*127)%720).toFloat(),1.5f,paint)}
        val jpg=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)}.toByteArray()
        bitmap.eraseColor(Color.TRANSPARENT);paint.color=0xffe7cea0.toInt();paint.strokeWidth=2f;paint.style=android.graphics.Paint.Style.STROKE
        canvas.drawRoundRect(16f,20f,344f,700f,28f,28f,paint)
        val png=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val gif="47494638396101000100800000ff00000000ff21ff0b4e45545343415045322e30030100000021f90400640000002c000000000100010000020244010021f90400640000002c00000000010001000002024c01003b".chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        return listOf("image/jpeg" to jpg,"image/png" to png,"image/gif" to gif).map {(mime,bytes)->
            val values=android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"vrm-fixture-${UUID.randomUUID()}");put(android.provider.MediaStore.MediaColumns.MIME_TYPE,mime)}
            val uri=checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            try {context.contentResolver.openOutputStream(uri)!!.use {it.write(bytes)};library.importEditorDocument(uri)}
            finally {context.contentResolver.delete(uri,null,null)}
        }
    }
    private fun button(tag: String): android.widget.Button? = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .firstNotNullOfOrNull {it.findViewWithTag<android.widget.Button>(tag)}
    fun screen(test: Instrumentation,seconds: Int) {
        check(android.os.Build.PRODUCT.startsWith("sdk_") && android.os.Build.VERSION.SDK_INT>=29)
        val activity=test.startActivitySync(Intent(test.targetContext,ScreenEditorActivity::class.java)
            .putExtra("scenePurpose","WALLPAPER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
        var editor: ScreenEditorView?=null
        try {
            main(test){
                editor=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}.get(activity) as ScreenEditorView
                ScreenEditorActivity::class.java.getDeclaredMethod("chooseVrm").apply {isAccessible=true}.invoke(activity)
            }
            waitFor(test) {
                fun list(v: android.view.View): android.widget.ListView? {
                    if(v is android.widget.ListView)return v
                    if(v is android.view.ViewGroup)for(i in 0 until v.childCount)list(v.getChildAt(i))?.let {return it}
                    return null
                }
                val choices=android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::list)
                if(choices==null || choices.count==0)false else {choices.performItemClick(choices.getChildAt(0),0,choices.adapter.getItemId(0));true}
            }
            waitFor(test){editor!!.currentDraft().scene?.vrm!=null}
            val valid=editor!!.currentDraft()
            val errorField=ScreenEditorActivity::class.java.getDeclaredField("renderError").apply {isAccessible=true}
            main(test){editor!!.change(valid.copy(scene=valid.scene!!.copy(vrm=valid.scene.vrm!!.copy(modelId="0".repeat(64)))))}
            waitFor(test){errorField.getBoolean(activity)}
            main(test){editor!!.change(valid)}
            waitFor(test){!errorField.getBoolean(activity)}
            Thread.sleep(seconds.coerceIn(5,60)*1000L)
        } finally {main(test){activity.finish();editor?.let {MediaLibrary.get(activity).discardEditorDraft(it.currentDraft().key)}}}
    }
    private fun main(test: Instrumentation, action: ()->Unit) {
        var failure: Throwable?=null
        test.runOnMainSync {try {action()}catch(e: Throwable){failure=e}}
        failure?.let {throw it}
    }
    private fun waitFor(test: Instrumentation,timeout: Long=45000,predicate: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<end) {
            var done=false;main(test){done=predicate()};if(done)return;Thread.sleep(50)
        }
        error("Composition did not reach expected state")
    }
    private fun js(test: Instrumentation,view: VrmSceneView,script: String): String {
        val latch=CountDownLatch(1);var value="null"
        main(test){view.webView!!.evaluateJavascript(script){value=it;latch.countDown()}}
        check(latch.await(10,TimeUnit.SECONDS));return value
    }
    fun run(test: Instrumentation,holdSeconds: Int=0) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"))
        LayeredSceneChecks.run()
        val store=VrmModelStore.get(test.targetContext)
        val entries=store.entries();check(entries.isNotEmpty()) {"Import a test VRM first"}
        val original=store.selected()
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(12,70,110))}
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val media=UUID.randomUUID().toString()
        val scene=ScreenScene("scene-${UUID.randomUUID()}","Composition check",ScenePurpose.WALLPAPER,
            listOf(ImageLayer("back",media,.5f,.5f,4f,0f,false,true)),vrm=VrmSceneLayer(entries.first().id,beforeImage=1))
        val activity=test.startActivitySync(Intent(test.targetContext,ScreenEditorActivity::class.java)
            .putExtra("scenePurpose","WALLPAPER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
        var view: VrmSceneView?=null
        var failure: VrmFailure?=null
        try {
            main(test){
                val editor=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}.get(activity) as ScreenEditorView
                editor.setRenderingEnabled(false)
                editor.setDraft(EditorDraft(scene.id,scene,emptyList()))
                editor.selectedVrm=true
                editor.modifyVrm {it.copy(placement=it.placement.withScreenPosition(.7f,.4f),beforeImage=1)}
                val placed=editor.currentDraft().scene!!
                check(placed.layers==scene.layers && kotlin.math.abs(placed.vrm!!.placement.screenX()-.7f)<.0001f)
                check(store.selected()==original)
                editor.change(editor.currentDraft().copy(scene=placed.copy(vrm=placed.vrm!!.copy(modelId=entries.last().id))))
                check(editor.currentDraft().scene!!.vrm!!.placement==placed.vrm.placement)
                editor.selectedLayer="back";check(!editor.selectedVrm)
                editor.selectedVrm=true;check(editor.selectedLayer==null && !editor.selectedCharacter)
                editor.change(editor.currentDraft().copy(scene=editor.currentDraft().scene!!.copy(layers=emptyList())))
                check(editor.currentDraft().scene!!.vrm!!.beforeImage==0)
                check(runCatching {WallpaperArtwork(scene.id,activity,scene)}.isFailure) {"A still renderer silently omitted VRM"}
                editor.close()
            }
            for(entry in entries) {
                main(test) {
                    view?.close();failure=null
                    view=VrmSceneView(activity,scene.copy(vrm=scene.vrm!!.copy(modelId=entry.id)),{store.openModel(entry.id)},{bytes.inputStream()}, {}, {failure=it})
                    check(!view!!.ready);activity.setContentView(view!!)
                }
                waitFor(test){failure!=null || view!!.ready};check(failure==null){"VRM ${entry.format}: $failure"}
                val current=view!!
                check(js(test,current,"getComputedStyle(document.body).backgroundColor") == "\"rgba(0, 0, 0, 0)\"")
                check(js(test,current,"getComputedStyle(document.documentElement).backgroundColor") == "\"rgba(0, 0, 0, 0)\"")
                val info=JSONObject(js(test,current,"window.vrmPreview.info"))
                check(info.getLong("frames")>0 && info.getInt("triangles")>0)
                android.util.Log.i("VrmChecks","COMPOSITION_READY ${entry.format} ${entry.id.take(8)}")
                if(holdSeconds>0)Thread.sleep(holdSeconds.coerceAtMost(30)*1000L)
                main(test){current.setActive(false)}
                val paused=JSONObject(js(test,current,"window.vrmPreview.info")).getLong("frames")
                Thread.sleep(250)
                check(JSONObject(js(test,current,"window.vrmPreview.info")).getLong("frames")==paused)
                main(test){current.setActive(true);current.updateScene(scene.copy(vrm=scene.vrm!!.copy(modelId=entry.id,visible=false)))}
                waitFor(test){current.ready}
                val hidden=JSONObject(js(test,current,"window.vrmPreview.info")).getLong("frames")
                Thread.sleep(250);check(JSONObject(js(test,current,"window.vrmPreview.info")).getLong("frames")==hidden)
                main(test){current.updateScene(scene.copy(vrm=scene.vrm!!.copy(modelId=entry.id)))}
                waitFor(test){current.ready}
            }
            main(test) {
                view!!.close();failure=null
                view=VrmSceneView(activity,scene.copy(vrm=scene.vrm!!.copy(visible=false)),{store.openModel(entries.first().id)},
                    {bytes.inputStream()}, {}, {failure=it})
                activity.setContentView(view!!)
            }
            waitFor(test){failure!=null || view!!.ready}
            main(test){check(failure==null && view!!.webView==null) {"Hidden VRM allocated a renderer"}}
            val truncated=store.openModel(entries.first().id)!!.use {input->ByteArray(12).also {java.io.DataInputStream(input).readFully(it)}}
            main(test) {
                view!!.close();failure=null
                view=VrmSceneView(activity,scene.copy(vrm=scene.vrm!!.copy(visible=false)),{truncated.inputStream()},
                    {bytes.inputStream()}, {}, {failure=it})
                activity.setContentView(view!!)
            }
            waitFor(test){failure!=null || view!!.ready}
            check(failure==VrmFailure.MODEL) {"Hidden truncated VRM was accepted"}
            main(test) {
                view!!.close();failure=null
                view=VrmSceneView(activity,scene,{store.openModel(entries.first().id)},{byteArrayOf(1,2,3).inputStream()}, {}, {failure=it})
                activity.setContentView(view!!)
            }
            waitFor(test){failure!=null};check(failure==VrmFailure.MEDIA)
            val entered=CountDownLatch(1);val release=CountDownLatch(1);var callbacks=0
            main(test) {
                view!!.close()
                view=VrmSceneView(activity,scene,{store.openModel(entries.first().id)},
                    {entered.countDown();check(release.await(5,TimeUnit.SECONDS));bytes.inputStream()}, {callbacks++}, {callbacks++})
                activity.setContentView(view!!)
            }
            check(entered.await(10,TimeUnit.SECONDS));main(test){view!!.close();view!!.close()};release.countDown()
            Thread.sleep(250);main(test){check(callbacks==0)}
            check(store.selected()==original)
        } finally {main(test){view?.close();activity.finish()}}
    }
}

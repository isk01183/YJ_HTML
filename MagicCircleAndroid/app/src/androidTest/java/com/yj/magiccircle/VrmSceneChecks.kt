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

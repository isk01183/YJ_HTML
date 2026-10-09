package com.yj.magiccircle

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object VrmMemoryChecks {
    /** Model IDs are the existing SAF imports of E and Hair02, in that order. */
    fun run(test: Instrumentation, modelId: String?, alternateId: String?) {
        check(Build.PRODUCT.startsWith("sdk_")) {"VRM memory checks are emulator-only"}
        val store=VrmModelStore.get(test.targetContext)
        val originalEntries=store.entries();val originalSelection=store.selected()
        val models=listOf(modelId to 58_523_840L,alternateId to 54_067_392L).map { (id,pixels)->
            val entry=originalEntries.singleOrNull {it.id==id}
            check(entry!=null) {"Pass model and alternate IDs for the existing E and Hair02 SAF imports"}
            entry to pixels
        }
        check(models[0].first.id!=models[1].first.id) {"Two different models are required"}
        fun digest(id: String): String {
            val digest=MessageDigest.getInstance("SHA-256")
            checkNotNull(store.openModel(id)).use { input->
                val bytes=ByteArray(32*1024)
                while(true) {val count=input.read(bytes);if(count<0)break;check(count>0);digest.update(bytes,0,count)}
            }
            return digest.digest().joinToString("") {"%02x".format(it)}
        }
        val originalHashes=models.associate {it.first.id to digest(it.first.id)}
        check(originalHashes.all {it.key==it.value}) {"Imported model differs from its original SHA-256"}
        val activity=test.startActivitySync(Intent(test.targetContext,ScreenEditorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
        var web: WebView?=null
        val failure=AtomicReference<VrmFailure?>(null)
        fun main(action: ()->Unit) {
            var error: Throwable?=null
            test.runOnMainSync {try {action()}catch(e: Throwable){error=e}}
            error?.let {throw it}
        }
        fun js(script: String): JSONObject {
            val ready=CountDownLatch(1);var value="null"
            main {checkNotNull(web).evaluateJavascript(script) {value=it;ready.countDown()}}
            check(ready.await(15,TimeUnit.SECONDS)) {"Viewer did not respond; native failure=${failure.get()}"}
            return JSONObject(value)
        }
        fun info()=js("window.vrmPreview?.info || {state:'loading',frames:0}")
        fun close() {
            main {
                web?.let {view->
                    if(failure.get()!=VrmFailure.RENDERER) {
                        view.evaluateJavascript("window.vrmPreview?.dispose()",null)
                        view.stopLoading()
                    }
                    (view.parent as? ViewGroup)?.removeView(view);view.destroy()
                }
                web=null
            }
        }
        try {
            main {
                val editor=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}
                    .get(activity) as ScreenEditorView
                editor.setRenderingEnabled(false);editor.close()
            }
            repeat(2) {round->
                for((entry,pixels) in models) {
                    main {
                        failure.set(null)
                        web=VrmWebView.create(activity,{store.openModel(entry.id)},{failure.set(it)}) {view->
                            VrmWebView.configure(view,entry.placement)
                            view.evaluateJavascript("window.vrmPreview?.resume()",null)
                        }
                        activity.setContentView(web!!)
                        web!!.loadUrl(VrmWebView.url("en",true,false))
                    }
                    val deadline=SystemClock.elapsedRealtime()+120_000
                    var state=info()
                    while(state.optString("state")=="loading" && failure.get()==null && SystemClock.elapsedRealtime()<deadline) {
                        Thread.sleep(200);state=info()
                    }
                    check(failure.get()==null && state.optString("state")=="ready") {"Model ${entry.name} failed: native=${failure.get()}, $state"}
                    val firstFrame=state.getLong("frames")
                    val frameDeadline=SystemClock.elapsedRealtime()+15_000
                    while(state.getLong("frames")<=maxOf(1,firstFrame) && SystemClock.elapsedRealtime()<frameDeadline) {
                        Thread.sleep(200);state=info()
                    }
                    check(state.getLong("frames")>maxOf(1,firstFrame)) {"Model ${entry.name} stopped rendering: $state"}
                    check(state.getInt("triangles")>0 && state.getInt("materials")>0) {"Model has no rendered geometry: $state"}
                    check(state.getLong("texturePixels")==pixels) {"Original texture dimensions changed: $state"}
                    check(state.getInt("decodedImages")>0 && state.getInt("peakDecodes")==1) {"Texture decodes were missing or concurrent: $state"}
                    check(state.getInt("activeDecodes")==0 && state.getInt("gpuTextures")>0) {"Model did not finish uploading textures: $state"}
                    check(state.getLong("estimatedBytes") in 1..state.getLong("budgetBytes")) {"Model exceeded its memory budget: $state"}
                    Log.i("VrmChecks","MEMORY_READY round=${round+1} name=${entry.name} info=$state")
                    val disposed=js("window.vrmPreview.dispose();window.vrmPreview.info")
                    check(disposed.getInt("liveBitmaps")==0 && disposed.getInt("activeDecodes")==0 && disposed.getInt("gpuTextures")==0) {
                        "Disposed model retained texture resources: $disposed"
                    }
                    Thread.sleep(250)
                    check(info().getLong("frames")==disposed.getLong("frames")) {"Disposed viewer continued rendering"}
                    Log.i("VrmChecks","MEMORY_DISPOSED round=${round+1} name=${entry.name} info=$disposed")
                    close()
                    check(digest(entry.id)==originalHashes[entry.id]) {"Rendering changed the imported model"}
                }
            }
            check(store.entries()==originalEntries && store.selected()==originalSelection) {"Memory checks changed the model library"}
        } finally {
            try {close()} finally {main {activity.finish()};test.waitForIdleSync()}
        }
    }
}

package com.yj.magiccircle

import android.content.Context
import java.io.File
import java.util.UUID

object VrmWallpaperChecks {
    /** Emulator-only; exercises real GPU loss / renderer exit, never a callback stand-in. */
    fun live(test: android.app.Instrumentation,seconds: Int=0) {
        check(android.os.Build.PRODUCT.startsWith("sdk_") && android.os.Build.VERSION.SDK_INT>=29)
        val context=test.targetContext
        val manager=android.app.WallpaperManager.getInstance(context)
        val slot=VrmWallpaperStore.key(context,manager.wallpaperInfo?.component) ?: error("Apply a VRM home wallpaper through system UI first")
        val snapshot=VrmWallpaperStore.snapshot(context,slot)
        fun <T> main(block: ()->T): T {
            var result: Result<T>?=null
            test.runOnMainSync {result=runCatching(block)}
            return result!!.getOrThrow()
        }
        fun web(): android.webkit.WebView?=main {
            fun find(v: android.view.View): android.webkit.WebView? {
                if(v is android.webkit.WebView && v.url?.contains("wallpaper=1")==true)return v
                if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
                return null
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews()
                .filter {it.tag=="vrm-wallpaper:$slot:false"}.firstNotNullOfOrNull(::find)
        }
        fun js(view: android.webkit.WebView,script: String): String {
            val latch=java.util.concurrent.CountDownLatch(1);var result="null"
            main {view.evaluateJavascript(script){result=it;latch.countDown()}}
            // First MToon shader compilation on the software-GPU tablet can block JS >8s.
            check(latch.await(45,java.util.concurrent.TimeUnit.SECONDS)) {"Wallpaper JS unresponsive"}
            return result
        }
        fun info(view: android.webkit.WebView)=org.json.JSONObject(js(view,"window.vrmPreview?.info || {state:'loading'}"))
        fun waitReady(old: android.webkit.WebView?=null): android.webkit.WebView {
            val deadline=android.os.SystemClock.elapsedRealtime()+45000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                val view=web()
                if(view!=null && view!==old && info(view).optString("state")=="ready" && info(view).optLong("frames")>1)return view
                Thread.sleep(200)
            }
            error("Wallpaper did not become ready")
        }
        fun home(){context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))}
        fun settings(){
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                if(main {android.view.inspector.WindowInspector.getGlobalWindowViews().any {it.tag=="vrm-wallpaper:$slot:false" && !it.isActivated}})return
                Thread.sleep(100)
            }
            error("Settings did not hide the wallpaper")
        }
        fun loss(view: android.webkit.WebView) {
            check(js(view,"(()=>{const c=document.querySelector('canvas');const g=c.getContext('webgl2')||c.getContext('webgl');const e=g.getExtension('WEBGL_lose_context');if(!e)return false;e.loseContext();return true})()")=="true")
        }
        fun log(view: android.webkit.WebView,label: String) {
            val m=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(m)
            android.util.Log.i("VrmChecks",label+" info="+info(view)+" hostPssKb="+m.totalPss)
        }
        home();var view=waitReady()
        if(seconds>0) {
            val until=android.os.SystemClock.elapsedRealtime()+seconds.coerceAtMost(600)*1000L
            while(android.os.SystemClock.elapsedRealtime()<until){log(view,"SOAK");Thread.sleep(15000);check(info(view).getString("state")=="ready")}
            repeat(10) {
                settings();Thread.sleep(700);val paused=info(view).getLong("frames");Thread.sleep(500)
                check(info(view).getLong("frames")==paused) {"Hidden wallpaper rendered"}
                home();view=waitReady();Thread.sleep(700);log(view,"TRANSITION-"+it)
            }
        } else {
            settings();Thread.sleep(1000);val paused=info(view).getLong("frames");Thread.sleep(1000)
            check(info(view).getLong("frames")==paused) {"Hidden wallpaper rendered"}
            home();waitReady();Thread.sleep(700);check(info(view).getLong("frames")>paused)
            val stale=view;val client=main {view.webViewClient}
            loss(view);view=waitReady(view)
            main {client.onRenderProcessGone(stale,object: android.webkit.RenderProcessGoneDetail() {
                override fun didCrash()=false
                override fun rendererPriorityAtExit()=0
            })}
            check(web()===view) {"Stale callback replaced a new engine renderer"}
            loss(view);view=waitReady(view)
            loss(view);Thread.sleep(6500);check(web()==null) {"Retry budget was not bounded"}
            main {VrmWallpaperStore.retry(context,slot,snapshot.generation)}
            view=waitReady()
            val old=view
            check(main {view.webViewRenderProcess?.terminate()}==true) {"Real renderer termination unavailable"}
            view=waitReady(old)
            settings();Thread.sleep(700);loss(view);Thread.sleep(4500)
            check(web()==null || web()===view) {"Hidden failure started a retry"}
            home();view=waitReady();log(view,"RECOVERY_OK")
        }
        check(VrmWallpaperStore.snapshot(context,slot)==snapshot) {"Runtime modified applied snapshot"}
    }
    fun run(context: Context) {
        val root=File(context.cacheDir,"vrm-wallpaper-check-${UUID.randomUUID()}")
        try {
            check(VrmWallpaperStore.freeSlot(setOf("vrm-slot-0","vrm-slot-1","vrm-slot-2"))==null)
            check(VrmWallpaperStore.freeSlot(setOf("vrm-slot-0","vrm-slot-2"))=="vrm-slot-1")
            val a=VrmWallpaperStore.stageFiles(root,"vrm-slot-0","a".repeat(64),VrmPlacement())
            val b=VrmWallpaperStore.stageFiles(root,"vrm-slot-1","b".repeat(64),VrmPlacement(.2f))
            VrmWallpaperStore.stageFiles(root,"vrm-slot-0","c".repeat(64),VrmPlacement(scale=1.3f))
            check(a.modelId=="a".repeat(64) && b.modelId=="b".repeat(64))
            check(VrmWallpaperStore.snapshot(root,"vrm-slot-0").modelId=="c".repeat(64))
            check(VrmWallpaperStore.snapshot(root,"vrm-slot-1")==b)
            check(runCatching {VrmWallpaperStore.stageFiles(root,"../outside","a".repeat(64),VrmPlacement())}.isFailure)
            check(runCatching {VrmWallpaperStore.stageFiles(root,"vrm-slot-1","../model",VrmPlacement())}.isFailure)
            val current=VrmWallpaperStore.snapshot(root,"vrm-slot-0")
            check(!current.acceptsRetry(a.generation+":retry"))
            check(current.acceptsRetry(current.generation+":retry"))
            val media=UUID.randomUUID().toString()
            val bitmap=android.graphics.Bitmap.createBitmap(12,12,android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            val encoded=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
            bitmap.recycle()
            val scene=ScreenScene("scene-${UUID.randomUUID()}","Composition",ScenePurpose.WALLPAPER,
                listOf(ImageLayer("back",media,.5f,.5f,1f,0f,false,true)),vrm=VrmSceneLayer("a".repeat(64),beforeImage=1))
            val composed=VrmWallpaperStore.stageFiles(root,"vrm-slot-1",scene){encoded.inputStream()}
            check(VrmWallpaperStore.snapshot(root,"vrm-slot-1")==composed)
            check(composed.scene==scene && composed.open(media).use {it.readBytes()}.contentEquals(encoded))
            val published=File(root,"vrm-slot-1.json").readBytes()
            check(runCatching {VrmWallpaperStore.stageFiles(root,"vrm-slot-1",scene){throw java.io.IOException("interrupted")}}.isFailure)
            check(File(root,"vrm-slot-1.json").readBytes().contentEquals(published))
            check(composed.open(media).use {it.readBytes()}.contentEquals(encoded))
            val reading=java.util.concurrent.atomic.AtomicBoolean(true)
            val readerError=java.util.concurrent.atomic.AtomicReference<Throwable?>()
            val reader=Thread {
                while(reading.get())runCatching {VrmWallpaperStore.snapshot(root,"vrm-slot-0")}
                    .onFailure {readerError.compareAndSet(null,it)}
            }.apply {start()}
            try {
                repeat(64) {
                    val next=VrmWallpaperStore.stageFiles(root,"vrm-slot-0","a".repeat(64),VrmPlacement())
                    check(VrmWallpaperStore.snapshot(root,"vrm-slot-0")==next) {"Concurrent reads interrupted atomic publication"}
                }
                check(readerError.get()==null) {"Concurrent snapshot read failed: ${readerError.get()}"}
            } finally {reading.set(false);reader.join(10000);check(!reader.isAlive)}
            check(runCatching {composed.open("../outside")}.isFailure)
            check(runCatching {VrmWallpaperStore.stageFiles(root,"vrm-slot-1",scene){byteArrayOf(1,2,3).inputStream()}}.isFailure)
            check(File(root,"vrm-slot-1.json").readBytes().contentEquals(published))
            val isolated=object: android.content.ContextWrapper(context) {override fun getNoBackupFilesDir()=File(root,"isolated")}
            val requestA=VrmWallpaperStore.beginApplication(isolated)
            check(runCatching {VrmWallpaperStore.beginApplication(isolated)}.isFailure)
            check(VrmWallpaperStore.pendingApplication(isolated)?.request==requestA)
            check(runCatching {VrmWallpaperStore.markLaunched(isolated,requestA)}.isFailure)
            VrmWallpaperStore.finishApplication(isolated,requestA)
            val requestB=VrmWallpaperStore.beginApplication(isolated)
            VrmWallpaperStore.finishApplication(isolated,requestA)
            check(VrmWallpaperStore.pendingApplication(isolated)?.request==requestB)
            VrmWallpaperStore.finishApplication(isolated,requestB)
            check(VrmWallpaperStore.pendingApplication(isolated)==null)
            val pending=File(isolated.noBackupFilesDir,"vrm-wallpapers/pending.json")
            val candidate=VrmWallpaperStore.stageFiles(pending.parentFile!!,"vrm-slot-2",scene){encoded.inputStream()}
            val requestC=UUID.randomUUID().toString()
            // Recreate the persisted state after the publisher exited, without any in-memory owner.
            pending.writeText(org.json.JSONObject().put("version",1).put("request",requestC).put("slot","vrm-slot-2")
                .put("generation",candidate.generation).put("launched",false).toString())
            check(VrmWallpaperStore.pendingApplication(isolated)?.generation==candidate.generation)
            val launchA=VrmWallpaperStore.markLaunched(isolated,requestC)
            check(VrmWallpaperStore.pendingApplication(isolated)?.launched==true)
            check(runCatching {VrmWallpaperStore.beginApplication(isolated)}.isFailure)
            VrmWallpaperStore.finishApplication(isolated,requestB)
            check(VrmWallpaperStore.pendingApplication(isolated)?.request==requestC)
            val launchB=VrmWallpaperStore.markLaunched(isolated,requestC)
            check(!VrmWallpaperStore.finishApplication(isolated,requestC,launchA)) {"A stale picker released the new picker reservation"}
            check(VrmWallpaperStore.pendingApplication(isolated)?.launch==launchB)
            check(VrmWallpaperStore.finishApplication(isolated,requestC,launchB))
            check(VrmWallpaperStore.snapshot(pending.parentFile!!,"vrm-slot-2")==candidate)
            pending.writeText("{broken")
            check(runCatching {VrmWallpaperStore.beginApplication(isolated)}.isFailure)
            check(pending.readText()=="{broken")
        } finally {root.deleteRecursively()}
    }
}

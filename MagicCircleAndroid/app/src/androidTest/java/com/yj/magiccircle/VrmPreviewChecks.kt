package com.yj.magiccircle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object VrmPreviewChecks {
    /** Requires a model already imported through SAF; never changes the model or its source. */
    fun render(test: android.app.Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_")) {"VRM render checks are emulator-only"}
        check(VrmModelStore.get(test.targetContext).hasModel()) {"Import a model through SAF before the render check"}
        val activity=test.startActivitySync(android.content.Intent(test.targetContext,VrmPreviewActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        fun javascript(script: String,timeoutMs: Long=15000): String {
            val ready=java.util.concurrent.CountDownLatch(1);var result="null"
            test.runOnMainSync {activity.window.decorView.findViewWithTag<android.webkit.WebView>("vrm-webview")
                .evaluateJavascript(script) {result=it;ready.countDown()}}
            check(ready.await(timeoutMs,java.util.concurrent.TimeUnit.MILLISECONDS)) {
                var details="WebView missing"
                test.runOnMainSync {activity.window.decorView.findViewWithTag<android.webkit.WebView>("vrm-webview")?.let {details="progress=${it.progress}, url=${it.url}"}}
                "Viewer did not respond within ${timeoutMs}ms ($details)"
            }
            return result
        }
        fun info(timeoutMs: Long=15000)=JSONObject(javascript("window.vrmPreview?.info || {state:'loading'}",timeoutMs))
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+45000
            var pageReady=false
            while(!pageReady && android.os.SystemClock.elapsedRealtime()<deadline) {
                test.runOnMainSync {
                    val view=activity.window.decorView.findViewWithTag<android.webkit.WebView>("vrm-webview")
                    pageReady=view?.progress==100 && view.url?.startsWith("https://appassets.androidplatform.net/vrm-preview/index.html")==true
                }
                if(!pageReady)Thread.sleep(100)
            }
            check(pageReady) {"Bundled viewer page did not finish navigating"}
            fun loadingInfo()=info((deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1))
            var state=loadingInfo()
            while(state.getString("state")=="loading" && android.os.SystemClock.elapsedRealtime()<deadline) {
                Thread.sleep(200);state=loadingInfo()
            }
            check(state.getString("state")=="ready") {"VRM rendering failed: $state"}
            val expected=if(VrmModelStore.get(test.targetContext).selected()!!.format==VrmFormat.V0)"0" else "1"
            check(state.getString("metaVersion")==expected) {"Loaded wrong VRM version"}
            android.util.Log.i("VrmChecks","RENDER version="+expected+" triangles="+state.getInt("triangles")+" materials="+state.getInt("materials"))
            check(state.getInt("triangles")>0 && state.getInt("materials")>0) {"Model did not create visible geometry/materials"}
            fun waitFrames(after: Int) {
                val until=android.os.SystemClock.elapsedRealtime()+15000
                while(info().getInt("frames")<=after && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(200)
                check(info().getInt("frames")>after) {"Preview is not rendering frames"}
            }
            waitFrames(1)
            javascript("window.vrmPreview.pause();window.vrmPreview.info.frames=0")
            Thread.sleep(800)
            test.runOnMainSync {check(!activity.window.decorView.findViewWithTag<android.widget.Button>("vrm-apply").isEnabled) {"Unrendered ready model could be applied"}}
            javascript("window.vrmPreview.resume()");waitFrames(1)
            javascript("window.vrmPreview.pause()")
            val paused=info().getInt("frames");Thread.sleep(300)
            check(info().getInt("frames")==paused) {"Paused preview kept rendering"}
            javascript("window.vrmPreview.resume()")
            waitFrames(paused)
            val blinkBefore=javascript("document.getElementById('motion').getAttribute('aria-pressed')")
            for(id in listOf("face","full","pose","motion")) {
                check(javascript("(()=>{const b=document.getElementById('$id');if(!b||b.disabled)return false;b.click();return window.vrmPreview.info.state==='ready'})()") == "true") {"Viewer control failed: $id"}
            }
            check(javascript("document.getElementById('pose').getAttribute('aria-pressed')") == "\"true\"")
            check(javascript("document.getElementById('motion').getAttribute('aria-pressed')") != blinkBefore)
            // An isolated empty store forces savePlacement to fail without risking private models.
            val storeField=VrmPreviewActivity::class.java.getDeclaredField("store").apply {isAccessible=true}
            val savedStore=storeField.get(activity) as VrmModelStore
            val saved=savedStore.selected()!!
            val scratch=File(test.targetContext.cacheDir,"vrm-save-failure-${UUID.randomUUID()}")
            javascript("window.checkedPlacement=null;window.savedConfigure=window.vrmPreview.configure;window.vrmPreview.configure=p=>{window.checkedPlacement=p;window.savedConfigure(p)}")
            try {
                test.runOnMainSync {
                    storeField.set(activity,VrmModelStore(scratch))
                    activity.window.decorView.findViewWithTag<android.widget.Button>("vrm-settings").performClick()
                    val panel=android.view.inspector.WindowInspector.getGlobalWindowViews().first {it.findViewWithTag<android.widget.SeekBar>("vrm-x")!=null}
                    panel.findViewWithTag<android.widget.SeekBar>("vrm-x").progress=if(saved.placement.x==.35f)0 else 70
                    panel.findViewWithTag<android.widget.Switch>("vrm-blink").isChecked=!saved.placement.blink
                    panel.findViewById<android.widget.Button>(android.R.id.button1).performClick()
                }
                val until=android.os.SystemClock.elapsedRealtime()+5000
                var idle=false
                while(!idle && android.os.SystemClock.elapsedRealtime()<until) {
                    test.runOnMainSync {idle=activity.window.decorView.findViewWithTag<android.widget.Button>("vrm-import").isEnabled}
                    if(!idle)Thread.sleep(50)
                }
                check(idle)
                val restored=JSONObject(javascript("window.checkedPlacement"))
                check(kotlin.math.abs(restored.getDouble("x")-saved.placement.x)<.00001 && restored.getBoolean("blink")==saved.placement.blink) {"Failed save left unsaved preview settings"}
                check(savedStore.selected()==saved) {"Failed save modified the library"}
            } finally {
                test.runOnMainSync {storeField.set(activity,savedStore)}
                javascript("window.vrmPreview.configure=window.savedConfigure")
                scratch.deleteRecursively()
            }
        } finally {test.runOnMainSync {activity.finish()};test.waitForIdleSync()}
    }

    @Suppress("DEPRECATION") // Exercises the platform renderer-termination callback with a real WebView.
    fun screen(test: android.app.Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_")) {"VRM UI checks are emulator-only"}
        val type=runCatching {Class.forName("com.yj.magiccircle.VrmPreviewActivity")}.getOrNull()
        check(type!=null) {"Local VRM preview screen is missing"}
        val activity=test.startActivitySync(android.content.Intent(test.targetContext,type)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val original=VrmModelStore.get(test.targetContext).selected()
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+10000
            var loaded=false
            while(!loaded && android.os.SystemClock.elapsedRealtime()<deadline) {
                test.runOnMainSync {loaded=activity.window.decorView.findViewWithTag<android.webkit.WebView>("vrm-webview")!=null}
                if(!loaded)Thread.sleep(50)
            }
            test.runOnMainSync {
                val root=activity.window.decorView
                check(root.findViewWithTag<android.widget.Button>("vrm-import").isEnabled)
                check(root.findViewWithTag<android.widget.Button>("vrm-back").isEnabled)
                for(tag in listOf("vrm-models","vrm-rename","vrm-settings","vrm-apply"))
                    check(root.findViewWithTag<android.widget.Button>(tag)!=null) {"Missing control: $tag"}
                val view=root.findViewWithTag<android.webkit.WebView>("vrm-webview")
                check(view.settings.javaScriptEnabled && view.settings.blockNetworkLoads)
                check(!view.settings.allowContentAccess && !view.settings.allowFileAccess)
                check(!view.settings.domStorageEnabled)
                val client=view.webViewClient
                @Suppress("DEPRECATION")
                for(url in listOf("https://example.com/vrm-preview/index.html","file:///etc/passwd","content://test/model.vrm",
                    "http://appassets.androidplatform.net/vrm-preview/index.html","https://appassets.androidplatform.net.evil/vrm-preview/index.html",
                    "https://appassets.androidplatform.net:443/vrm-preview/index.html","https://appassets.androidplatform.net/vrm-preview/../index.html")) {
                    check(client.shouldInterceptRequest(view,url)?.statusCode==403) {"Unexpected accessible URL: $url"}
                    check(client.shouldOverrideUrlLoading(view,url)) {"External navigation escaped the viewer"}
                }
                @Suppress("DEPRECATION")
                val page=client.shouldInterceptRequest(view,"https://appassets.androidplatform.net/vrm-preview/index.html?lang=ko")!!
                check(page.statusCode==200 && page.responseHeaders["Cache-Control"]=="no-store")
                page.data.close()
                check(client.onRenderProcessGone(view,object: android.webkit.RenderProcessGoneDetail() {
                    override fun didCrash()=false
                    override fun rendererPriorityAtExit()=0
                }))
                check(root.findViewWithTag<android.webkit.WebView>("vrm-webview")==null) {"Dead renderer remained attached"}
                check(!root.findViewWithTag<android.widget.Button>("vrm-apply").isEnabled) {"Failed renderer can be applied"}
                check(root.findViewWithTag<android.widget.Button>("vrm-retry").performClick())
                check(root.findViewWithTag<android.webkit.WebView>("vrm-webview")!==view) {"Retry reused terminated renderer"}
                val replacement=root.findViewWithTag<android.webkit.WebView>("vrm-webview")
                client.onRenderProcessGone(view,object: android.webkit.RenderProcessGoneDetail() {
                    override fun didCrash()=false
                    override fun rendererPriorityAtExit()=0
                })
                check(root.findViewWithTag<android.webkit.WebView>("vrm-webview")===replacement) {"Stale callback destroyed new viewer"}
                check(!root.findViewWithTag<android.widget.Button>("vrm-apply").isEnabled) {"Loading renderer can be applied"}
            }
            if(original!=null) {
                fun panel(): android.view.View = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .first {it.findViewWithTag<android.widget.SeekBar>("vrm-x")!=null}
                test.runOnMainSync {
                    activity.window.decorView.findViewWithTag<android.widget.Button>("vrm-settings").performClick()
                    val p=panel()
                    p.findViewWithTag<android.widget.SeekBar>("vrm-x").progress=70
                    p.findViewWithTag<android.widget.SeekBar>("vrm-y").progress=0
                    p.findViewWithTag<android.widget.SeekBar>("vrm-scale").progress=100
                    p.findViewWithTag<android.widget.Switch>("vrm-blink").isChecked=false
                    p.findViewById<android.widget.Button>(android.R.id.button1).performClick()
                }
                val until=android.os.SystemClock.elapsedRealtime()+5000
                while(VrmModelStore.get(test.targetContext).selected()?.placement!=VrmPlacement(.35f,-.35f,1.5f,false) && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(50)
                check(VrmModelStore.get(test.targetContext).selected()?.placement==VrmPlacement(.35f,-.35f,1.5f,false))
                test.waitForIdleSync()
                test.runOnMainSync {
                    activity.window.decorView.findViewWithTag<android.widget.Button>("vrm-settings").performClick()
                    val p=panel()
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-x").progress==70)
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-y").progress==0)
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-scale").progress==100)
                    check(!p.findViewWithTag<android.widget.Switch>("vrm-blink").isChecked)
                    p.findViewWithTag<android.widget.Button>("vrm-reset").performClick()
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-x").progress==35)
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-y").progress==35)
                    check(p.findViewWithTag<android.widget.SeekBar>("vrm-scale").progress==50)
                    check(p.findViewWithTag<android.widget.Switch>("vrm-blink").isChecked)
                    p.findViewById<android.widget.Button>(android.R.id.button2).performClick()
                }
                check(VrmModelStore.get(test.targetContext).selected()?.placement==VrmPlacement(.35f,-.35f,1.5f,false)) {"Cancelled settings changed storage"}
            }
        } finally {
            test.runOnMainSync {activity.finish()};test.waitForIdleSync()
            original?.let {VrmModelStore.get(test.targetContext).savePlacement(it.id,it.placement)}
        }
    }

    fun run(context: Context) {
        val root=File(context.cacheDir,"vrm-check-${UUID.randomUUID()}")
        try {
            val store=VrmModelStore(root)
            fun import(input: InputStream) {store.importModel(input)}
            val original=glb(document())
            import(ByteArrayInputStream(original))
            fun savedBytes()=store.openModel()!!.use {it.readBytes()}
            check(savedBytes().contentEquals(original)) {"Valid embedded VRM1 was not saved intact"}
            val escaped=document().put("extras","quote \" slash \\ newline\n tab\t control\u0001")
            val validSyntax=glb(escaped,rawJson=escaped.toString().replace("\"byteLength\":36","\"byteLength\":3.6e1"))
            import(ByteArrayInputStream(validSyntax))
            check(savedBytes().contentEquals(validSyntax)) {"Valid JSON escapes or exponent notation rejected"}
            import(ByteArrayInputStream(original))
            fun reject(bytes: ByteArray,label: String) {
                check(runCatching {import(ByteArrayInputStream(bytes))}.isFailure) {"Accepted $label"}
                check(savedBytes().contentEquals(original)) {"Failed $label replacement destroyed the previous model"}
                check(root.listFiles()!!.none {it.name.endsWith(".tmp")}) {"Failed import left private temporary data"}
            }
            reject(original.copyOf(original.size-1),"truncated GLB")
            reject(original.copyOf().also {it[0]=0},"invalid GLB magic")
            reject(original.copyOf().also {ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(8,28)},"declared file length mismatch")
            reject(original.copyOf().also {ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(12,Int.MAX_VALUE)},"invalid JSON chunk length")
            reject(glb(document(),rawJson=document().toString().replace("\"asset\":","\"asset\" /* invalid JSON */:")),"nonstandard JSON")
            for(name in listOf("Private\nimporter test","Private\\qimporter test","Private\\u12XXimporter test")) {
                reject(glb(document(),rawJson=document().toString().replace("Private importer test",name)),"invalid JSON string")
            }
            reject(glb(document(),rawJson=document().put("extras",true).toString().replace("\"extras\":true","\"extras\":TRUE")),"uppercase JSON literal")
            reject(glb(document(),rawJson=document().toString().replace("\"byteLength\":36","\"byteLength\":36.")),"trailing decimal point")
            reject(glb(document().apply {remove("extensions")}),"non-VRM GLB")
            reject(glb(document().apply {getJSONObject("extensions").getJSONObject("VRMC_vrm").put("specVersion","0.0")}),"unsupported VRM version")
            reject(glb(document().apply {getJSONObject("extensions").put("VRM",documentV0().getJSONObject("extensions").getJSONObject("VRM"))}),"ambiguous VRM extensions")
            for(zero in listOf(false,true)) {
                val doc=if(zero)documentV0() else document()
                val vrm=doc.getJSONObject("extensions").getJSONObject(if(zero)"VRM" else "VRMC_vrm")
                if(zero)vrm.getJSONObject("humanoid").getJSONArray("humanBones").getJSONObject(1).put("node",0)
                else vrm.getJSONObject("humanoid").getJSONObject("humanBones").getJSONObject("spine").put("node",0)
                reject(glb(doc),"duplicate humanoid node")
            }
            reject(glb(documentV0().apply {
                getJSONObject("extensions").getJSONObject("VRM").getJSONObject("humanoid").getJSONArray("humanBones").remove(0)
            }),"missing VRM0 bone")
            reject(glb(documentV0().apply {
                val bones=getJSONObject("extensions").getJSONObject("VRM").getJSONObject("humanoid").getJSONArray("humanBones")
                for(i in 0 until bones.length()) if(bones.getJSONObject(i).getString("bone")=="chest"){bones.remove(i);break}
            }),"missing legacy chest")
            reject(glb(document().apply {
                getJSONObject("extensions").getJSONObject("VRMC_vrm").getJSONObject("humanoid").getJSONObject("humanBones").getJSONObject("hips").put("node",9999)
            }),"humanoid index outside nodes")
            for(uri in listOf("https://example.com/texture.png","file:///sdcard/model.bin","../other.bin","data:application/octet-stream;base64,AAAA")) {
                reject(glb(document().apply {getJSONArray("buffers").getJSONObject(0).put("uri",uri)}),"buffer URI $uri")
                reject(glb(document().put("images",JSONArray().put(JSONObject().put("uri",uri)))),"image URI $uri")
            }
            reject(glb(document().apply {getJSONArray("buffers").getJSONObject(0).put("byteLength",1000)}),"buffer past BIN")
            reject(glb(document().apply {getJSONArray("bufferViews").getJSONObject(0).put("byteOffset",1000)}),"buffer view past BIN")
            reject(glb(document().apply {getJSONArray("bufferViews").getJSONObject(0).put("byteOffset",-1)}),"negative offset")
            reject(glb(document().apply {getJSONArray("bufferViews").getJSONObject(0).put("byteLength",1.5)}),"fractional byte length")
            reject(glb(document().apply {getJSONArray("accessors").getJSONObject(0).put("count",1000)}),"accessor past buffer view")
            reject(glb(document().apply {getJSONArray("accessors").getJSONObject(0).put("count",Int.MAX_VALUE).remove("bufferView")}),"oversized zero-filled accessor")
            reject(glb(document().put("images",JSONArray().put(JSONObject().put("bufferView",0).put("mimeType","image/png")))),"invalid embedded image")
            fun imageFixture(width: Int,height: Int=1,copies: Int=1,extraPixel: Boolean=false): ByteArray {
                val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
                val encoded=java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded);bitmap.recycle()
                val png=encoded.toByteArray()
                val one=if(extraPixel)java.io.ByteArrayOutputStream().also {out->
                    val single=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888)
                    single.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);single.recycle()
                }.toByteArray() else ByteArray(0)
                val bytes=ByteArray(36)+png+one
                val json=document()
                json.getJSONArray("buffers").getJSONObject(0).put("byteLength",bytes.size)
                json.getJSONArray("bufferViews").put(JSONObject().put("buffer",0).put("byteOffset",36).put("byteLength",png.size))
                json.put("images",JSONArray().apply {repeat(copies){put(JSONObject().put("bufferView",1).put("mimeType","image/png"))}})
                if(extraPixel) {
                    json.getJSONArray("bufferViews").put(JSONObject().put("buffer",0).put("byteOffset",36+png.size).put("byteLength",one.size))
                    json.getJSONArray("images").put(JSONObject().put("bufferView",2).put("mimeType","image/png"))
                }
                return glb(json,bytes)
            }
            reject(imageFixture(4097),"oversized compressed texture")
            reject(imageFixture(1,1,65),"too many images")
            reject(imageFixture(4096,2048,6),"total texture pixels over bound")
            reject(imageFixture(4096,2048,5,true),"texture limit plus one pixel")
            var bytesRead=0L
            val huge=object: InputStream() {
                override fun read(): Int {bytesRead++;return 0}
                override fun read(b: ByteArray,off: Int,len: Int): Int {b.fill(0,off,off+len);bytesRead+=len;return len}
            }
            check(runCatching {import(huge)}.isFailure) {"Unbounded stream accepted"}
            check(bytesRead<=64L*1024*1024+1) {"Import read beyond its 64 MiB limit"}
            check(savedBytes().contentEquals(original)) {"Oversized import replaced saved model"}
            val unreadable=object: InputStream() {override fun read(): Int=throw IOException("Provider failed")}
            check(runCatching {import(unreadable)}.isFailure)
            check(savedBytes().contentEquals(original)) {"Provider failure replaced saved model"}
            import(ByteArrayInputStream(imageFixture(4096,2048,5)))
            val v0=glb(documentV0())
            import(ByteArrayInputStream(v0))
            check(savedBytes().contentEquals(v0)) {"VRM0 not saved intact"}
            val replacement=imageFixture(1)
            import(ByteArrayInputStream(replacement))
            check(savedBytes().contentEquals(replacement)) {"Successful replacement did not commit"}
            val folder=File(root,"collection")
            val library=VrmModelStore(folder)
            val a=library.importModel(ByteArrayInputStream(original),"test.vrm")
            val b=library.importModel(ByteArrayInputStream(v0),"test.vrm")
            check(a.id!=b.id && library.entries().size==2) {"Same filename overwrote a different character"}
            library.rename(a.id,"My character")
            library.savePlacement(a.id,VrmPlacement(.2f,-.3f,1.2f,false))
            check(library.importModel(ByteArrayInputStream(original),"again.vrm").id==a.id)
            check(library.entries().size==2 && library.selected()!!.name=="My character")
            library.select(b.id);library.select(a.id)
            val reopened=VrmModelStore(folder)
            check(reopened.selected()==library.selected())
            check(reopened.selected()!!.placement==VrmPlacement(.2f,-.3f,1.2f,false))
            check(reopened.openModel(b.id)!!.use {it.readBytes()}.contentEquals(v0))
            check(runCatching {reopened.select("../model")}.isFailure)
            check(runCatching {reopened.importModel(ByteArrayInputStream(ByteArray(28)))}.isFailure)
            check(reopened.entries().size==2 && reopened.selected()!!.id==a.id)
            Thread.currentThread().interrupt()
            try {check(runCatching {reopened.importModel(ByteArrayInputStream(v0))}.isFailure)}
            finally {Thread.interrupted()}
            check(reopened.selected()!!.id==a.id)
            val legacy=File(root,"legacy").apply {mkdir()}
            File(legacy,"model.vrm").writeBytes(original)
            check(VrmModelStore(legacy).entries().size==1)
            check(VrmModelStore(legacy).entries().size==1)
            check(File(legacy,"model.vrm").readBytes().contentEquals(original))
            val index=File(folder,"index.json")
            val before=index.readBytes()
            val obstacle=File(folder,"index.json.new").apply {mkdir();resolve("block").writeText("test")}
            check(runCatching {reopened.rename(a.id,"should not persist")}.isFailure)
            check(index.readBytes().contentEquals(before) && reopened.selected()!!.name=="My character")
            obstacle.deleteRecursively()
            val missing=File(folder,"models/${a.id}.vrm")
            check(missing.renameTo(File(folder,"missing-model-test.vrm")))
            check(reopened.entries().size==2) {"One missing output invalidated the whole character library"}
            reopened.select(b.id)
            check(reopened.openModel(b.id)!!.use {it.readBytes()}.contentEquals(v0))
            check(reopened.openModel(a.id)==null)
        } finally {root.deleteRecursively()}
    }

    // Generated test-only triangle and skeleton; no personal or distributed avatar asset.
    private fun documentV0(): JSONObject = document().apply {
        val old=getJSONObject("extensions").getJSONObject("VRMC_vrm")
        val bones=old.getJSONObject("humanoid").getJSONObject("humanBones")
        val human=JSONArray()
        bones.keys().forEach {bone->human.put(JSONObject().put("bone",bone).put("node",bones.getJSONObject(bone).getInt("node")))}
        for(bone in listOf("chest","neck")) {
            val nodes=getJSONArray("nodes")
            human.put(JSONObject().put("bone",bone).put("node",nodes.length()))
            nodes.put(JSONObject().put("name",bone))
        }
        put("extensionsUsed",JSONArray().put("VRM"))
        put("extensions",JSONObject().put("VRM",JSONObject().put("specVersion","0.0")
            .put("meta",JSONObject().put("title","Private old VRM").put("author","Test").put("licenseName","Redistribution_Prohibited"))
            .put("humanoid",JSONObject().put("humanBones",human))))
    }
    private fun document(): JSONObject {
        val bones=listOf("hips","spine","head","leftUpperLeg","leftLowerLeg","leftFoot","rightUpperLeg","rightLowerLeg","rightFoot","leftUpperArm","leftLowerArm","leftHand","rightUpperArm","rightLowerArm","rightHand")
        val human=JSONObject();bones.forEachIndexed {i,bone->human.put(bone,JSONObject().put("node",i))}
        return JSONObject().put("asset",JSONObject().put("version","2.0"))
            .put("extensionsUsed",JSONArray().put("VRMC_vrm"))
            .put("extensions",JSONObject().put("VRMC_vrm",JSONObject().put("specVersion","1.0")
                .put("meta",JSONObject().put("name","Private importer test").put("authors",JSONArray().put("Test")).put("licenseUrl","https://vrm.dev/licenses/1.0/"))
                .put("humanoid",JSONObject().put("humanBones",human))))
            .put("nodes",JSONArray(bones.map {JSONObject().put("name",it)}))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",36)))
            .put("bufferViews",JSONArray().put(JSONObject().put("buffer",0).put("byteLength",36)))
            .put("accessors",JSONArray().put(JSONObject().put("bufferView",0).put("componentType",5126).put("count",3).put("type","VEC3")))
    }

    private fun glb(json: JSONObject,binary: ByteArray=ByteArray(36),rawJson: String=json.toString()): ByteArray {
        val bytes=rawJson.toByteArray(Charsets.UTF_8)
        val padded=(bytes.size+3)/4*4
        val binaryPadded=(binary.size+3)/4*4
        val buffer=ByteBuffer.allocate(12+8+padded+8+binaryPadded).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity())
        buffer.putInt(padded).putInt(0x4e4f534a).put(bytes)
        repeat(padded-bytes.size) {buffer.put(0x20.toByte())}
        buffer.putInt(binaryPadded).putInt(0x004e4942).put(binary)
        return buffer.array()
    }
}

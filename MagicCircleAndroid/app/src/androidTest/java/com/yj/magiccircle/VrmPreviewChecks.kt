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
        fun javascript(script: String,timeoutMs: Long=5000): String {
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
        fun info(timeoutMs: Long=5000)=JSONObject(javascript("window.vrmPreview?.info || {state:'loading'}",timeoutMs))
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
            check(state.getInt("triangles")>0 && state.getInt("materials")>0) {"Model did not create visible geometry/materials"}
            Thread.sleep(400)
            check(info().getInt("frames")>1) {"Preview is not rendering frames"}
            javascript("window.vrmPreview.pause()")
            val paused=info().getInt("frames");Thread.sleep(300)
            check(info().getInt("frames")==paused) {"Paused preview kept rendering"}
            javascript("window.vrmPreview.resume()")
            Thread.sleep(400)
            check(info().getInt("frames")>paused) {"Preview did not resume"}
            for(id in listOf("face","full","pose","motion")) {
                check(javascript("(()=>{const b=document.getElementById('$id');if(!b||b.disabled)return false;b.click();return window.vrmPreview.info.state==='ready'})()") == "true") {"Viewer control failed: $id"}
            }
            check(javascript("document.getElementById('pose').getAttribute('aria-pressed')") == "\"true\"")
            check(javascript("document.getElementById('motion').getAttribute('aria-pressed')") == "\"true\"")
        } finally {test.runOnMainSync {activity.finish()};test.waitForIdleSync()}
    }

    @Suppress("DEPRECATION") // Exercises the platform renderer-termination callback with a real WebView.
    fun screen(test: android.app.Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_")) {"VRM UI checks are emulator-only"}
        val type=runCatching {Class.forName("com.yj.magiccircle.VrmPreviewActivity")}.getOrNull()
        check(type!=null) {"Local VRM preview screen is missing"}
        val activity=test.startActivitySync(android.content.Intent(test.targetContext,type)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            test.runOnMainSync {
                val root=activity.window.decorView
                check(root.findViewWithTag<android.widget.Button>("vrm-import").isEnabled)
                check(root.findViewWithTag<android.widget.Button>("vrm-back").isEnabled)
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
                check(root.findViewWithTag<android.widget.Button>("vrm-retry").performClick())
                check(root.findViewWithTag<android.webkit.WebView>("vrm-webview")!==view) {"Retry reused terminated renderer"}
            }
        } finally {test.runOnMainSync {activity.finish()};test.waitForIdleSync()}
    }

    fun run(context: Context) {
        val root=File(context.cacheDir,"vrm-check-${UUID.randomUUID()}")
        try {
            val store=VrmModelStore(root)
            fun import(input: InputStream) {store.importModel(input)}
            val original=glb(document())
            import(ByteArrayInputStream(original))
            val saved=File(root,"model.vrm")
            check(saved.readBytes().contentEquals(original)) {"Valid embedded VRM1 was not saved intact"}
            val escaped=document().put("extras","quote \" slash \\ newline\n tab\t control\u0001")
            val validSyntax=glb(escaped,rawJson=escaped.toString().replace("\"byteLength\":36","\"byteLength\":3.6e1"))
            import(ByteArrayInputStream(validSyntax))
            check(saved.readBytes().contentEquals(validSyntax)) {"Valid JSON escapes or exponent notation rejected"}
            import(ByteArrayInputStream(original))
            fun reject(bytes: ByteArray,label: String) {
                check(runCatching {import(ByteArrayInputStream(bytes))}.isFailure) {"Accepted $label"}
                check(saved.readBytes().contentEquals(original)) {"Failed $label replacement destroyed the previous model"}
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
            fun imageFixture(width: Int): ByteArray {
                val bitmap=android.graphics.Bitmap.createBitmap(width,1,android.graphics.Bitmap.Config.ARGB_8888)
                val encoded=java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded);bitmap.recycle()
                val png=encoded.toByteArray();val bytes=ByteArray(36)+png
                val json=document()
                json.getJSONArray("buffers").getJSONObject(0).put("byteLength",bytes.size)
                json.getJSONArray("bufferViews").put(JSONObject().put("buffer",0).put("byteOffset",36).put("byteLength",png.size))
                json.put("images",JSONArray().put(JSONObject().put("bufferView",1).put("mimeType","image/png")))
                return glb(json,bytes)
            }
            reject(imageFixture(4097),"oversized compressed texture")
            var bytesRead=0L
            val huge=object: InputStream() {
                override fun read(): Int {bytesRead++;return 0}
                override fun read(b: ByteArray,off: Int,len: Int): Int {b.fill(0,off,off+len);bytesRead+=len;return len}
            }
            check(runCatching {import(huge)}.isFailure) {"Unbounded stream accepted"}
            check(bytesRead<=64L*1024*1024+1) {"Import read beyond its 64 MiB limit"}
            check(saved.readBytes().contentEquals(original)) {"Oversized import replaced saved model"}
            val unreadable=object: InputStream() {override fun read(): Int=throw IOException("Provider failed")}
            check(runCatching {import(unreadable)}.isFailure)
            check(saved.readBytes().contentEquals(original)) {"Provider failure replaced saved model"}
            val replacement=imageFixture(1)
            import(ByteArrayInputStream(replacement))
            check(saved.readBytes().contentEquals(replacement)) {"Successful replacement did not commit"}
        } finally {root.deleteRecursively()}
    }

    // Generated test-only triangle and skeleton; no personal or distributed avatar asset.
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

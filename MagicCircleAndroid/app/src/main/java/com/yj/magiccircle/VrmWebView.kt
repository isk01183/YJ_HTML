package com.yj.magiccircle

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.webkit.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream

enum class VrmFailure { RENDERER, CONTEXT, PAGE, MODEL, MEDIA, MEMORY, TIMEOUT }

/** One local resource policy for both the Activity and each wallpaper engine. */
internal object VrmWebView {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun create(context: Context,openModel: ()->InputStream?,onFailure: (VrmFailure)->Unit,
               transparent: Boolean=false,
               initialAppearance: ()->VrmAvatarAppearance?={null},
               onPageFinished: (WebView)->Unit): WebView = WebView(context).apply {
        setBackgroundColor(if(transparent)android.graphics.Color.TRANSPARENT else 0xffeee9f3.toInt())
        settings.apply {
            javaScriptEnabled=true;domStorageEnabled=false;allowContentAccess=false;allowFileAccess=false
            allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true;cacheMode=WebSettings.LOAD_NO_CACHE;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false);javaScriptCanOpenWindowsAutomatically=false;mediaPlaybackRequiresUserGesture=true
        }
        webViewClient=object: WebViewClient() {
            private fun resource(uri: Uri,method: String): WebResourceResponse {
                fun response(code: Int,mime: String,stream: InputStream)=WebResourceResponse(mime,"UTF-8",code,
                    if(code==200)"OK"else "Blocked",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),stream)
                fun blocked()=response(403,"text/plain",ByteArrayInputStream(ByteArray(0)))
                if(method!="GET" || uri.scheme!="https" || uri.encodedAuthority!="appassets.androidplatform.net")return blocked()
                return try {when(uri.encodedPath) {
                    "/vrm-preview/index.html"->response(200,"text/html",context.assets.open("vrm-preview/index.html"))
                    "/vrm-preview/viewer.js"->response(200,"application/javascript",context.assets.open("vrm-preview/viewer.js"))
                    "/vrm-preview/viewer.css"->response(200,"text/css",context.assets.open("vrm-preview/viewer.css"))
                    "/vrm-preview/appearance.json"->response(200,"application/json",ByteArrayInputStream(
                        (initialAppearance()?.let {VrmAvatarRules.appearanceJson(it).toString()} ?: "null").toByteArray(Charsets.UTF_8)))
                    "/vrm-preview/memory.json"->{
                        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memory=ActivityManager.MemoryInfo().also {manager.getMemoryInfo(it)}
                        val budget=VrmMemoryPolicy.budget(memory.totalMem,memory.availMem,memory.threshold,memory.lowMemory,manager.isLowRamDevice)
                        response(200,"application/json",ByteArrayInputStream(JSONObject().put("budgetBytes",budget).toString().toByteArray(Charsets.UTF_8)))
                    }
                    "/vrm-preview/model.vrm"->openModel()?.let {response(200,"model/gltf-binary",it)} ?: blocked()
                    else->blocked()
                }}catch(_: Exception){blocked()}
            }
            override fun shouldOverrideUrlLoading(v: WebView,url: String)=true
            override fun shouldOverrideUrlLoading(v: WebView,request: WebResourceRequest)=true
            override fun shouldInterceptRequest(v: WebView,url: String)=resource(Uri.parse(url),"GET")
            override fun shouldInterceptRequest(v: WebView,request: WebResourceRequest)=resource(request.url,request.method)
            override fun onPageFinished(v: WebView,url: String)=onPageFinished(v)
            override fun onReceivedError(v: WebView,request: WebResourceRequest,error: WebResourceError) {
                if(request.isForMainFrame)onFailure(VrmFailure.PAGE)
            }
            override fun onReceivedHttpError(v: WebView,request: WebResourceRequest,response: WebResourceResponse) {
                if(request.isForMainFrame)onFailure(VrmFailure.PAGE)
            }
            override fun onRenderProcessGone(v: WebView,detail: RenderProcessGoneDetail): Boolean {
                onFailure(VrmFailure.RENDERER);return true
            }
        }
    }
    fun url(language: String,hasModel: Boolean,wallpaper: Boolean,composition: Boolean=false)=Uri.parse("https://appassets.androidplatform.net/vrm-preview/index.html").buildUpon()
        .appendQueryParameter("lang",language).appendQueryParameter("model",if(hasModel)"1"else "0")
        .appendQueryParameter("wallpaper",if(wallpaper)"1"else "0")
        .appendQueryParameter("composition",if(composition)"1"else "0").build().toString()
    fun configure(view: WebView,placement: VrmPlacement) {
        val p=placement.normalized()
        val json=JSONObject().put("x",p.x).put("y",p.y).put("scale",p.scale).put("blink",p.blink)
        view.evaluateJavascript("window.vrmPreview?.configure($json)",null)
    }
    fun appearance(view: WebView,value: VrmAvatarAppearance?) {
        val json=value?.let {VrmAvatarRules.appearanceJson(it).toString()} ?: "null"
        view.evaluateJavascript("window.vrmPreview?.appearance($json)",null)
    }
}

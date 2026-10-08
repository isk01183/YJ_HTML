package com.yj.magiccircle

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class VrmPreviewActivity: Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private val importer=Executors.newSingleThreadExecutor()
    private val importStream=AtomicReference<InputStream?>()
    private var importCancellation: CancellationSignal?=null
    private lateinit var store: VrmModelStore
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var retryButton: Button
    private var web: WebView?=null
    private var active=false
    private var importing=false
    private var pendingReload=false
    @Volatile private var closed=false
    private fun w(ko: String,ja: String,en: String)=when(WebViews.selectedLanguage(this)){"ja"->ja;"en"->en;else->ko}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=VrmModelStore.get(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            if(android.os.Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xfff6f3ec.toInt())}
        root.setOnApplyWindowInsetsListener {v,insets->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        val header=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4))}
        fun button(text: String,tag: String,action: ()->Unit)=Button(this).apply {
            this.text=text;this.tag=tag;isAllCaps=false;minHeight=dp(48);textSize=14f
            setTextColor(0xff343b50.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xffeae5db.toInt())
            header.addView(this,LinearLayout.LayoutParams(0,-2,1f));setOnClickListener {action()}
        }
        button(w("← 뒤로","← 戻る","← Back"),"vrm-back") {finish()}
        importButton=button(w("VRM 불러오기","VRMを開く","Import VRM"),"vrm-import") {chooseModel()}
        root.addView(header)
        status=TextView(this).apply {
            tag="vrm-status";textSize=12f;setTextColor(0xff555469.toInt());setPadding(dp(16),dp(4),dp(16),dp(8))
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            text=w("VRM 1.0 · 최대 64MiB · 이 기기에만 보관","VRM 1.0 · 最大64MiB · この端末だけに保存","VRM 1.0 · Up to 64 MiB · Stored only on this device")
        }
        root.addView(status)
        retryButton=Button(this).apply {
            tag="vrm-retry";text=w("다시 열기","再試行","Try again");isAllCaps=false;minHeight=dp(48);visibility=View.GONE
            setTextColor(0xff343b50.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xffeae5db.toInt())
            setOnClickListener {if(!closed && !isFinishing){visibility=View.GONE;showViewer()}}
        }
        root.addView(retryButton)
        setContentView(root)
        showViewer()
    }

    @Suppress("DEPRECATION")
    private fun chooseModel() {
        if(importing || closed)return
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },41)
        } catch(_: Exception) {
            status.text=w("파일 선택기를 열 수 없습니다.","ファイル選択を開けません。","Could not open the file picker.")
        }
    }

    @Deprecated("Platform document picker result for API 23+")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=41 || resultCode!=RESULT_OK || importing || closed)return
        val uri=data?.data ?: return
        if(uri.scheme!="content") {importFailed();return}
        importing=true;importButton.isEnabled=false
        status.text=w("모델을 확인하고 있습니다…","モデルを確認しています…","Checking model…")
        val cancellation=CancellationSignal();importCancellation=cancellation
        importer.execute {
            val result=runCatching {
                contentResolver.openAssetFileDescriptor(uri,"r",cancellation)?.use {descriptor->
                    descriptor.createInputStream().use {stream->
                        importStream.set(stream)
                        try {
                            if(closed)throw java.io.InterruptedIOException("Preview closed")
                            store.importModel(stream)
                        } finally {importStream.compareAndSet(stream,null)}
                    }
                } ?: error("Cannot open model")
            }
            if(!closed)ui.post {
                if(closed || isFinishing)return@post
                importCancellation=null;importing=false;importButton.isEnabled=true
                if(result.isSuccess) {
                    status.text=w("이 기기에 저장했습니다. 3D 미리보기 전용입니다.","この端末に保存しました。3Dプレビュー専用です。","Saved on this device for 3D preview.")
                    pendingReload=true
                    if(active)reloadModel()
                } else importFailed()
            }
        }
    }

    private fun importFailed() {
        status.text=w("불러올 수 없습니다. 텍스처가 포함된 VRM 1.0(64MiB 이하)을 선택하세요. 이전 모델은 보존됩니다.",
            "読み込めません。テクスチャ内蔵のVRM 1.0（64MiB以下）を選んでください。以前のモデルは保持されます。",
            "Could not import. Choose an embedded VRM 1.0 file up to 64 MiB. The previous model is preserved.")
    }

    @SuppressLint("SetJavaScriptEnabled") // Only the three bundled viewer resources are allowed.
    @Suppress("DEPRECATION")
    private fun showViewer() {
        disposeWebView()
        val view=WebView(this)
        web=view
        view.tag="vrm-webview"
        view.setBackgroundColor(0xffeee9f3.toInt())
        view.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=false
            allowContentAccess=false;allowFileAccess=false
            allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true;cacheMode=WebSettings.LOAD_NO_CACHE
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false);javaScriptCanOpenWindowsAutomatically=false
            mediaPlaybackRequiresUserGesture=true
        }
        view.webViewClient=object: WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView,url: String)=true
            override fun shouldOverrideUrlLoading(v: WebView,request: WebResourceRequest)=true
            override fun shouldInterceptRequest(v: WebView,url: String)=resource(Uri.parse(url),"GET")
            override fun shouldInterceptRequest(v: WebView,request: WebResourceRequest)=resource(request.url,request.method)
            override fun onPageFinished(v: WebView,url: String) {
                if(web===v && !closed)v.evaluateJavascript("window.vrmPreview?.${if(active)"resume" else "pause"}()",null)
            }
            override fun onReceivedError(v: WebView,request: WebResourceRequest,error: WebResourceError) {
                if(request.isForMainFrame && web===v && !closed)showWebError()
            }
            override fun onReceivedHttpError(v: WebView,request: WebResourceRequest,response: WebResourceResponse) {
                if(request.isForMainFrame && web===v && !closed)showWebError()
            }
            override fun onRenderProcessGone(v: WebView,detail: RenderProcessGoneDetail): Boolean {
                (v.parent as? ViewGroup)?.removeView(v)
                if(web===v)web=null
                v.destroy()
                if(!closed)showWebError()
                return true
            }
        }
        root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        loadPage(view)
        if(!active)view.onPause()
    }

    private fun showWebError() {
        status.text=w("3D 보기가 중단되었습니다. 다시 열거나 더 작은 모델을 선택하세요.",
            "3D表示が停止しました。再試行するか、小さいモデルを選んでください。",
            "The 3D viewer stopped. Try again or choose a smaller model.")
        retryButton.visibility=View.VISIBLE
    }

    private fun resource(uri: Uri,method: String): WebResourceResponse {
        fun response(code: Int,mime: String,stream: java.io.InputStream)=WebResourceResponse(mime,"UTF-8",code,
            if(code==200)"OK"else "Blocked",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),stream)
        fun blocked()=response(403,"text/plain",ByteArrayInputStream(ByteArray(0)))
        if(method!="GET" || uri.scheme!="https" || uri.encodedAuthority!="appassets.androidplatform.net")return blocked()
        val path=uri.encodedPath
        return try {
            when(path) {
                "/vrm-preview/index.html"->response(200,"text/html",assets.open("vrm-preview/index.html"))
                "/vrm-preview/viewer.js"->response(200,"application/javascript",assets.open("vrm-preview/viewer.js"))
                "/vrm-preview/viewer.css"->response(200,"text/css",assets.open("vrm-preview/viewer.css"))
                "/vrm-preview/model.vrm"->store.openModel()?.let {response(200,"model/gltf-binary",it)} ?: blocked()
                else->blocked()
            }
        } catch(_: Exception) {blocked()}
    }

    private fun loadPage(view: WebView) {
        val url=Uri.parse("https://appassets.androidplatform.net/vrm-preview/index.html").buildUpon()
        if(store.hasModel())url.appendQueryParameter("model","1")
        url.appendQueryParameter("lang",WebViews.selectedLanguage(this))
        view.loadUrl(url.build().toString())
    }
    private fun reloadModel() {
        pendingReload=false;retryButton.visibility=View.GONE
        showViewer()
    }
    override fun onResume() {
        super.onResume();active=true
        if(pendingReload)reloadModel()
        web?.apply {onResume();evaluateJavascript("window.vrmPreview?.resume()",null)}
    }
    override fun onPause() {
        active=false
        web?.apply {evaluateJavascript("window.vrmPreview?.pause()",null);onPause()}
        super.onPause()
    }
    private fun disposeWebView() {
        val view=web ?: return;web=null
        view.evaluateJavascript("window.vrmPreview?.dispose()",null)
        view.stopLoading();(view.parent as? ViewGroup)?.removeView(view);view.destroy()
    }
    override fun onDestroy() {
        closed=true;active=false;importer.shutdownNow();ui.removeCallbacksAndMessages(null)
        val cancellation=importCancellation;val stream=importStream.getAndSet(null)
        if(cancellation!=null || stream!=null)Thread({
            runCatching {stream?.close()}
            runCatching {cancellation?.cancel()}
        },"vrm-import-close").start()
        disposeWebView();super.onDestroy()
    }
}

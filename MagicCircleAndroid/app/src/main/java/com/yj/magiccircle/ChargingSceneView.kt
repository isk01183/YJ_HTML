package com.yj.magiccircle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.Closeable
import java.util.concurrent.Executors

class ChargingSceneView @JvmOverloads constructor(context: Context, val themeId: String,
    information: List<InfoPlacement>?, sceneOverride: ScreenScene? = null) : FrameLayout(context), Closeable {
    private val library=MediaLibrary.get(context)
    private val scene=sceneOverride ?: library.scene(themeId)
    private var layout=resolveInformation(information)
    val informationView=ChargeInfoView(context)
    private var native: MainMagicChargeView?=null
    private var web: WebView?=null
    private var layered: LayeredSceneRenderer?=null
    private var lease: Closeable?=null
    private var closed=false
    private var prepared=false
    private var ready: Runnable?=null
    private var failure: Runnable?=null
    private var receiverRegistered=false
    private var startedAt=0L
    private var deadline=0L
    private var editor=false
    private var editorProgress=.5f
    private var snapshot=ChargeSnapshot(null,null,null,null,null)
    private val ui=Handler(Looper.getMainLooper())
    private val frame: Runnable=Runnable { if(!closed) { updateInfo(); imageView.invalidate() } }
    private val poll=Runnable { pollWeb() }
    private val imageView: View=object: View(context) {
        override fun onDraw(c: Canvas) {
            val elapsed=if(editor || startedAt==0L) 0L else SystemClock.uptimeMillis()-startedAt
            layered?.draw(c,elapsed,!editor)
            if(!editor && startedAt>0 && SystemClock.uptimeMillis()<deadline && isShown) {
                ui.removeCallbacks(frame);ui.postDelayed(frame,34)
            }
        }
    }
    private val receiver=object: BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { readBattery(i);updateInfo() } }
    init {
        setBackgroundColor(0xff000000.toInt())
        alpha=0f
        if(scene!=null) addView(imageView,LayoutParams(-1,-1))
        else if(ThemeSelection.isNative(themeId)) {
            native=MainMagicChargeView(context,themeId).also { it.customInformation=layout!=null; addView(it,LayoutParams(-1,-1)) }
        } else {
            require(ThemeSelection.isValid(themeId) || library.find(themeId)!=null)
            web=WebViews.magicCircle(context).also { addView(it,LayoutParams(-1,-1)) }
        }
        addView(informationView,LayoutParams(-1,-1))
        informationView.setInformation(layout ?: emptyList())
        setOnApplyWindowInsetsListener { _,insets ->
            @Suppress("DEPRECATION")
            informationView.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
    }
    fun prepare(onReady: Runnable,onFailure: Runnable) {
        if(closed) return
        ready=onReady;failure=onFailure
        try {
            val ids=scene?.layers?.map { it.mediaId } ?: if(library.find(themeId)!=null) listOf(themeId) else emptyList()
            lease=library.leaseMedia(ids)
            if(layout!=null) {
                val filter=IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val intent=if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver,filter)
                receiverRegistered=true; readBattery(intent);updateInfo()
            }
            post { prepareSized() }
        } catch(_: Exception) { fail() }
    }
    private fun prepareSized() {
        if(closed) return
        if(width<=0 || height<=0) { postDelayed({prepareSized()},16);return }
        if(scene!=null) {
            val w=width;val h=height
            IO.execute {
                var result: LayeredSceneRenderer?=null
                try {
                    result=LayeredSceneRenderer(scene,{library.open(it,false)},LayeredSceneRenderer.budget(context))
                    result.prepare(w,h)
                    val renderer=result
                    ui.post { if(closed) renderer.close() else { layered=renderer;markReady() } }
                } catch(_: Exception) { result?.close();ui.post { fail() } }
                catch(_: OutOfMemoryError) { result?.close();ui.post { fail() } }
            }
        } else if(native!=null) {
            try { native!!.editorFrame=true;native!!.start();markReady() } catch(_: RuntimeException) { fail() }
        } else web?.let { view ->
            view.setWebViewClient(object: WebViews.LocalClient(context) {
                override fun shouldOverrideUrlLoading(v: WebView,url: String)=true
                override fun shouldOverrideUrlLoading(v: WebView,r: WebResourceRequest)=true
                override fun onPageFinished(v: WebView,url: String) { if(!closed && v===web) pollWeb() }
                override fun onReceivedError(v: WebView,r: WebResourceRequest,e: WebResourceError) { if(r.isForMainFrame) fail() }
                override fun onRenderProcessGone(v: WebView,detail: RenderProcessGoneDetail): Boolean { fail();return true }
            })
            WebViews.loadMagicCircle(view,themeId,layout!=null)
        }
    }
    private fun pollWeb() {
        if(closed || prepared) return
        web?.evaluateJavascript("typeof prepareChargingAnimation==='function' && prepareChargingAnimation()") { result ->
            if(closed) return@evaluateJavascript
            if(result=="true") markReady() else {ui.removeCallbacks(poll);ui.postDelayed(poll,40)}
        }
    }
    private fun markReady() { if(closed || prepared)return;prepared=true;ready?.run();ready=null }
    private fun fail() { if(!closed) { val callback=failure;close();callback?.run() } }
    fun start(startUptimeMs: Long,deadlineUptimeMs: Long) {
        if(closed || !prepared || startedAt!=0L)return
        editor=false;startedAt=startUptimeMs;deadline=deadlineUptimeMs
        val remaining=deadline-SystemClock.uptimeMillis()
        if(remaining<=0) { fail();return }
        native?.let { it.editorFrame=false;it.invalidate() }
        web?.let { WebViews.startMagicCircle(it,remaining) { result -> if(!closed && result!="true") fail() } }
        alpha=1f; imageView.invalidate();updateInfo()
    }
    fun showEditorFrame(progress: Float=.5f) {
        editorProgress=progress.coerceIn(0f,1f)
        if(closed || !prepared)return
        editor=true;native?.editorFrame=true
        web?.evaluateJavascript("showChargeEditorFrame($editorProgress)",null)
        alpha=1f;imageView.invalidate();updateInfo()
    }
    fun setInformation(items: List<InfoPlacement>?) {
        layout=resolveInformation(items);informationView.setInformation(layout ?: emptyList());updateInfo()
    }
    private fun resolveInformation(items: List<InfoPlacement>?) = items ?: when(scene?.purpose) {
        ScenePurpose.CHARGING -> ChargeInfoView.defaultInformation(themeId)
        ScenePurpose.WALLPAPER -> emptyList()
        null -> null
    }
    fun updateLayers(layers: List<ImageLayer>) { layered?.updateLayers(layers);imageView.invalidate() }
    fun imageAt(x: Float,y: Float)=layered?.hitTest(x,y)
    private fun readBattery(i: Intent?) {
        fun v(key: String)=if(i?.hasExtra(key)==true)i.getIntExtra(key,0)else null
        snapshot=ChargeSnapshot.fromRaw(v(BatteryManager.EXTRA_LEVEL),v(BatteryManager.EXTRA_SCALE),v(BatteryManager.EXTRA_TEMPERATURE),v(BatteryManager.EXTRA_HEALTH),v(BatteryManager.EXTRA_STATUS),v(BatteryManager.EXTRA_PLUGGED))
    }
    private fun updateInfo() {
        val p=if(editor || startedAt==0L)editorProgress else ((SystemClock.uptimeMillis()-startedAt).toFloat()/maxOf(1,deadline-startedAt)).coerceIn(0f,1f)
        informationView.update(snapshot,WebViews.selectedLanguage(context),p)
        if(!editor && startedAt>0 && SystemClock.uptimeMillis()<deadline) { ui.removeCallbacks(frame);ui.postDelayed(frame,100) }
    }
    override fun close() {
        if(closed)return;closed=true;ui.removeCallbacksAndMessages(null)
        if(receiverRegistered) {receiverRegistered=false;context.unregisterReceiver(receiver)}
        native?.stop();web?.let { WebViews.destroy(it) };web=null
        layered?.close();layered=null;lease?.close();lease=null
        ready=null;failure=null
    }
    companion object { private val IO=Executors.newSingleThreadExecutor() }
}

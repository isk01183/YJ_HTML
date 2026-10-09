package com.yj.magiccircle

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.DataInputStream
import java.io.InputStream
import java.util.concurrent.Executors

/** One bounded image cache, with the live character between its back and front ranges. */
internal class VrmSceneView(context: Context,scene: ScreenScene,private val openModel: ()->InputStream?,
    private val openMedia: (String)->InputStream,private val onReady: ()->Unit,
    private val onFailure: (VrmFailure)->Unit): FrameLayout(context),AutoCloseable {
    private var scene=scene
    var webView: WebView?=null;private set
    var ready=false;private set
    private var renderer: LayeredSceneRenderer?=null
    private val ui=Handler(Looper.getMainLooper())
    private val health=VrmRenderHealth()
    private var closed=false
    private var active=true
    private var imageEpoch=0
    private var revision=0
    private var modelChecked=false
    private var pageReady=false
    private var requiredFrame=0L
    private var configured=false
    private var elapsed=0L
    private var lastFrame=0L
    private val back=ImageRange(false)
    private val front=ImageRange(true)
    private inner class ImageRange(private val foreground: Boolean): View(context) {
        override fun onDraw(canvas: Canvas) {
            val at=scene.vrm!!.beforeImage
            renderer?.drawImages(canvas,elapsed,true,if(foreground)at else 0,
                if(foreground)scene.layers.size else at,!foreground)
        }
    }
    private val frame=object: Runnable {
        override fun run() {
            if(closed || !active)return
            val now=SystemClock.uptimeMillis()
            if(lastFrame>0)elapsed+=now-lastFrame
            lastFrame=now;back.invalidate();front.invalidate()
            if(renderer?.animated==true)ui.postDelayed(this,34)
        }
    }
    private val poll=object: Runnable {
        override fun run() {
            if(closed || !active)return
            val view=webView ?: return
            if(!scene.vrm!!.visible)return
            val token=revision
            view.evaluateJavascript("window.vrmPreview?.info || null") {value->
                if(closed || webView!==view || revision!=token)return@evaluateJavascript
                val info=runCatching {JSONObject(value)}.getOrNull()
                if(info?.optString("state")=="error") {
                    fail(if(info.optString("failure")=="CONTEXT")VrmFailure.CONTEXT else VrmFailure.MODEL)
                } else {
                    val frames=info?.optLong("frames",0) ?: 0
                    val displayed=configured && info?.optString("state")=="ready" && frames>requiredFrame
                    health.sample(displayed,frames,SystemClock.elapsedRealtime())?.let {fail(it);return@evaluateJavascript}
                    if(displayed)markReady()
                }
            }
            ui.postDelayed(this,250)
        }
    }
    init {
        require(scene.vrm!=null && scene.character==null && scene.purpose==ScenePurpose.WALLPAPER)
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(back,LayoutParams(-1,-1));addView(front,LayoutParams(-1,-1))
        health.newAttempt(SystemClock.elapsedRealtime());health.setVisible(true,SystemClock.elapsedRealtime())
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(!closed && w>0 && h>0)prepareImages(w,h)
    }
    private fun prepareImages(w: Int,h: Int) {
        val token=++imageEpoch;val value=scene
        ready=false;renderer?.close();renderer=null
        IO.execute {
            var result: LayeredSceneRenderer?=null
            var failure=VrmFailure.MODEL
            try {
                // These private models were fully validated at import; check the container is still readable.
                DataInputStream(checkNotNull(openModel())).use {input->
                    check(Integer.reverseBytes(input.readInt())==0x46546c67)
                    check(Integer.reverseBytes(input.readInt())==2)
                    check(Integer.reverseBytes(input.readInt()).toLong() in 28..VrmModelStore.MAX_BYTES)
                }
                failure=VrmFailure.MEDIA
                result=LayeredSceneRenderer(value,openMedia,LayeredSceneRenderer.budget(context))
                result.prepare(w,h)
                val prepared=result
                ui.post {
                    if(closed || imageEpoch!=token)prepared.close()
                    else {
                        prepared.updateLayers(scene.layers);renderer=prepared;modelChecked=true
                        refreshWeb();redraw();if(!scene.vrm!!.visible)markReady()
                    }
                }
            } catch(_: Exception) {result?.close();ui.post {if(!closed && imageEpoch==token)fail(failure)}}
            catch(_: OutOfMemoryError) {result?.close();ui.post {if(!closed && imageEpoch==token)fail(failure)}}
        }
    }
    fun updateScene(value: ScreenScene) {
        check(!closed)
        require(value.vrm?.modelId==scene.vrm!!.modelId && value.character==null && value.purpose==ScenePurpose.WALLPAPER)
        require(value.layers.map {it.id to it.mediaId}==scene.layers.map {it.id to it.mediaId})
        val visibilityChanged=value.layers.map {it.visible}!=scene.layers.map {it.visible}
        scene=value;revision++;ready=false;configured=false
        renderer?.updateLayers(value.layers)
        if(visibilityChanged && width>0 && height>0)prepareImages(width,height)
        refreshWeb();redraw()
        if(!value.vrm!!.visible)markReady()
    }
    private fun refreshWeb() {
        if(closed || !modelChecked)return
        val visible=scene.vrm!!.visible
        if(visible && webView==null) {
            val view=VrmWebView.create(context,openModel,{fail(it)},transparent=true) {v->
                if(!closed && webView===v){pageReady=true;configure();setActive(active)}
            }
            webView=view;addView(view,1,LayoutParams(-1,-1))
            view.loadUrl(VrmWebView.url(WebViews.selectedLanguage(context),true,true,true))
        } else if(visible)configure()
        setActive(active)
    }
    private fun configure() {
        val view=webView ?: return
        if(!pageReady || !scene.vrm!!.visible)return
        val token=revision
        configured=false
        VrmWebView.configure(view,scene.vrm!!.placement)
        view.evaluateJavascript("window.vrmPreview?.info.frames || 0") {value->
            if(!closed && view===webView && token==revision) {requiredFrame=value.toLongOrNull() ?: 0;configured=true}
        }
    }
    private fun markReady() {
        if(closed || ready || renderer==null || !modelChecked)return
        ready=true;onReady()
    }
    fun setActive(value: Boolean) {
        if(closed)return
        active=value;health.setVisible(value && scene.vrm!!.visible,SystemClock.elapsedRealtime())
        ui.removeCallbacks(frame);ui.removeCallbacks(poll);lastFrame=0
        webView?.apply {
            val draw=value && scene.vrm!!.visible
            visibility=if(scene.vrm!!.visible)View.VISIBLE else View.INVISIBLE
            if(draw){onResume();evaluateJavascript("window.vrmPreview?.resume()",null)}
            else {evaluateJavascript("window.vrmPreview?.pause()",null);onPause()}
        }
        if(value){ui.post(frame);if(scene.vrm!!.visible && webView!=null)ui.post(poll)}
    }
    private fun redraw() {back.invalidate();front.invalidate();ui.removeCallbacks(frame);if(active)ui.post(frame)}
    private fun fail(kind: VrmFailure) {if(!closed){close(kind==VrmFailure.RENDERER);onFailure(kind)}}
    fun close(crashed: Boolean) {
        if(closed)return
        closed=true;ready=false;imageEpoch++;revision++;ui.removeCallbacksAndMessages(null)
        val view=webView;webView=null
        if(!crashed){runCatching {view?.evaluateJavascript("window.vrmPreview?.dispose()",null)};runCatching {view?.stopLoading()}}
        runCatching {(view?.parent as? ViewGroup)?.removeView(view)};runCatching {view?.destroy()}
        renderer?.close();renderer=null
    }
    override fun close()=close(false)
    companion object {private val IO=Executors.newSingleThreadExecutor()}
}

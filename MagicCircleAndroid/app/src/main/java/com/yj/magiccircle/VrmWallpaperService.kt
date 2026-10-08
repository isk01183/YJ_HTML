package com.yj.magiccircle

import android.app.Presentation
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.TextView
import org.json.JSONObject
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Each engine owns its display, renderer, immutable application and retry budget. */
abstract class VrmWallpaperService: WallpaperService() {
    protected abstract val slot: String
    private val states=ConcurrentHashMap<Int,String>()
    private val ids=AtomicInteger()
    override fun onCreateEngine(): Engine=VrmEngine()
    override fun dump(fd: FileDescriptor,out: PrintWriter,args: Array<out String>) {
        super.dump(fd,out,args);states.toSortedMap().values.forEach(out::println)
    }
    private inner class VrmEngine: Engine() {
        private val id=ids.incrementAndGet()
        private val ui=Handler(Looper.getMainLooper())
        private val health=VrmRenderHealth()
        private val preferences=getSharedPreferences("vrm-retry",MODE_PRIVATE)
        private var snapshot: VrmWallpaperStore.Snapshot?=null
        private var display: VirtualDisplay?=null
        private var presentation: Presentation?=null
        private var web: WebView?=null
        private var epoch=0
        private var visible=false
        private var closed=false
        private var surfaceReady=false
        private var width=0
        private var height=0
        private var terminal=false
        private var nextDelay: Long?=0L
        private var state="loading"
        private var frames=0L
        private var sampleAt=0L
        private var sampleFrames=0L
        private val retryListener=SharedPreferences.OnSharedPreferenceChangeListener {prefs,key->
            if(!closed && key==slot && snapshot?.acceptsRetry(prefs.getString(slot,null))==true) {
                health.reset(now());terminal=false;nextDelay=0L;releaseRenderer();schedule()
            }
        }
        private val start=Runnable {if(canDraw())createRenderer()}
        private val sample=object: Runnable {
            override fun run() {
                if(!canDraw() || web==null)return
                val view=web!!;val token=epoch
                health.sample(state=="ready",frames,now())?.let {fail(it);return}
                view.evaluateJavascript("window.vrmPreview?.info || null") {value->
                    if(closed || epoch!=token || web!==view)return@evaluateJavascript
                    val info=runCatching {JSONObject(value)}.getOrNull()
                    state=info?.optString("state","loading") ?: "loading"
                    frames=info?.optLong("frames",0) ?: 0
                    if(state=="error") {
                        fail(if(info?.optString("failure")=="CONTEXT")VrmFailure.CONTEXT else VrmFailure.MODEL)
                    } else record("sample")
                }
                ui.postDelayed(this,1000)
            }
        }
        private fun now()=SystemClock.elapsedRealtime()
        private fun canDraw()=!closed && visible && surfaceReady && surfaceHolder.surface.isValid
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder);setOffsetNotificationsEnabled(false);setTouchEventsEnabled(false)
            snapshot=runCatching {VrmWallpaperStore.snapshot(this@VrmWallpaperService,slot)}.getOrNull()
            if(snapshot==null){terminal=true;state="MODEL"}
            preferences.registerOnSharedPreferenceChangeListener(retryListener)
        }
        override fun onSurfaceChanged(holder: SurfaceHolder,format: Int,w: Int,h: Int) {
            super.onSurfaceChanged(holder,format,w,h)
            releaseRenderer();width=w;height=h;surfaceReady=w>0 && h>0
            if(!terminal && nextDelay==null)nextDelay=0L
            schedule()
        }
        override fun onVisibilityChanged(value: Boolean) {
            super.onVisibilityChanged(value);visible=value;health.setVisible(value,now())
            ui.removeCallbacks(start);ui.removeCallbacks(sample)
            updateVisibility();if(value)schedule();record("visibility")
        }
        private fun schedule() {
            if(!canDraw())return
            if(terminal) {if(display==null)showError();return}
            if(web!=null) {ui.removeCallbacks(sample);ui.post(sample);return}
            ui.removeCallbacks(start);ui.postDelayed(start,nextDelay ?: 0L)
        }
        private fun createOutput(): Presentation {
            val token=epoch
            display=getSystemService(DisplayManager::class.java).createVirtualDisplay("VRM-$slot-$id-$token",width,height,
                resources.displayMetrics.densityDpi,surfaceHolder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                object: VirtualDisplay.Callback() {
                    override fun onStopped(){if(!closed && epoch==token && !terminal)fail(VrmFailure.CONTEXT)}
                },ui) ?: error("Display unavailable")
            return Presentation(this@VrmWallpaperService,display!!.display,android.R.style.Theme_Material_NoActionBar_Fullscreen).also {screen->
                presentation=screen
                screen.window?.apply {
                    addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    setBackgroundDrawable(ColorDrawable(0xffeee9f3.toInt()));setDimAmount(0f)
                }
            }
        }
        private fun createRenderer() {
            releaseRenderer();val token=epoch
            val applied=snapshot ?: return
            nextDelay=null;state="loading";frames=0;health.newAttempt(now())
            try {
                val screen=createOutput()
                val view=VrmWebView.create(screen.context,{VrmModelStore.get(this@VrmWallpaperService).openModel(applied.modelId)},
                    {failure->if(epoch==token && !closed)fail(failure)}) {v->
                    if(epoch==token && web===v && !closed) {VrmWebView.configure(v,applied.placement);updateVisibility()}
                }
                web=view;screen.setContentView(view,ViewGroup.LayoutParams(-1,-1))
                screen.setOnDismissListener {if(epoch==token && !closed)fail(VrmFailure.CONTEXT)}
                screen.show();screen.window?.setLayout(-1,-1)
                view.loadUrl(VrmWebView.url(WebViews.selectedLanguage(this@VrmWallpaperService),true,true))
                updateVisibility();ui.post(sample);record("created")
            } catch(_: Exception) {if(epoch==token)fail(VrmFailure.CONTEXT)}
        }
        private fun updateVisibility() {
            web?.apply {
                visibility=if(visible)View.VISIBLE else View.INVISIBLE
                if(visible){onResume();evaluateJavascript("window.vrmPreview?.resume()",null)}
                else {evaluateJavascript("window.vrmPreview?.pause()",null);onPause()}
            }
        }
        private fun fail(kind: VrmFailure) {
            nextDelay=health.onFailure(kind);terminal=nextDelay==null
            releaseRenderer(kind==VrmFailure.RENDERER);state=kind.name;record("failure")
            if(canDraw()){showError();schedule()}
        }
        private fun showError() {
            if(!canDraw() || display!=null)return
            runCatching {
                val screen=createOutput()
                screen.setContentView(TextView(screen.context).apply {
                    text=getString(if(terminal)R.string.vrm_wallpaper_failed else R.string.vrm_wallpaper_retrying)
                    textSize=16f;gravity=android.view.Gravity.CENTER;setTextColor(0xff343b50.toInt());setPadding(32,32,32,32)
                })
                screen.show();screen.window?.setLayout(-1,-1)
            }.onFailure {releaseRenderer()}
        }
        private fun releaseRenderer(crashed: Boolean=false) {
            epoch++;ui.removeCallbacksAndMessages(null)
            val view=web;web=null;val screen=presentation;presentation=null;val output=display;display=null
            if(!crashed) {runCatching {view?.evaluateJavascript("window.vrmPreview?.dispose()",null)};runCatching {view?.stopLoading()}}
            runCatching {(view?.parent as? ViewGroup)?.removeView(view)};runCatching {view?.destroy()}
            runCatching {screen?.dismiss()};runCatching {output?.surface=null};runCatching {output?.release()}
            // The framework owns surfaceHolder.surface; never release it here.
        }
        private fun record(event: String) {
            val t=now();val fps=if(sampleAt>0 && t>sampleAt && frames>=sampleFrames)(frames-sampleFrames)*1000/(t-sampleAt) else 0
            val line="slot=$slot engine=$id epoch=$epoch event=$event visible=$visible preview=$isPreview state=$state frames=$frames fps=$fps display=${display?.display?.displayId}"
            states[id]=line
            if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)android.util.Log.i("VrmWallpaper",line)
            sampleAt=t;sampleFrames=frames
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady=false;releaseRenderer();if(!terminal && nextDelay==null)nextDelay=0L
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            closed=true;visible=false;preferences.unregisterOnSharedPreferenceChangeListener(retryListener)
            releaseRenderer();states.remove(id);super.onDestroy()
        }
    }
}
class VrmWallpaperService0: VrmWallpaperService(){override val slot="vrm-slot-0"}
class VrmWallpaperService1: VrmWallpaperService(){override val slot="vrm-slot-1"}
class VrmWallpaperService2: VrmWallpaperService(){override val slot="vrm-slot-2"}

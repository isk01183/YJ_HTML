package com.yj.magiccircle

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.os.*
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class VrmPreviewActivity: Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private val importer=Executors.newSingleThreadExecutor()
    private val importStream=AtomicReference<InputStream?>()
    private var importCancellation: CancellationSignal?=null
    private lateinit var store: VrmModelStore
    private lateinit var wallpaper: WallpaperController
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var modelButton: Button
    private lateinit var renameButton: Button
    private lateinit var settingsButton: Button
    private lateinit var applyButton: Button
    private lateinit var retryButton: Button
    private lateinit var appliedButton: Button
    private var settingsDialog: AlertDialog?=null
    private var entries=emptyList<VrmEntry>()
    private var entry: VrmEntry?=null
    private var web: WebView?=null
    private var active=false
    private var busy=false
    private var ready=false
    private var failed=false
    private var pendingReload=false
    private val health=VrmRenderHealth()
    @Volatile private var closed=false
    private fun w(ko: String,ja: String,en: String)=when(WebViews.selectedLanguage(this)){"ja"->ja;"en"->en;else->ko}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun now()=SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);store=VrmModelStore.get(this)
        wallpaper=WallpaperController(this).apply {beforeVrmLaunch={disposeWebView();pendingReload=true};restoreState(savedInstanceState)}
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            if(Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xfff6f3ec.toInt())}
        root.setOnApplyWindowInsetsListener {v,insets->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
        }
        val header=LinearLayout(this);root.addView(header)
        button(header,w("← 뒤로","← 戻る","← Back"),"vrm-back"){finish()}
        importButton=button(header,w("VRM 불러오기","VRMを開く","Import VRM"),"vrm-import"){chooseModel()}
        val scroll=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false}
        val tools=LinearLayout(this);scroll.addView(tools);root.addView(scroll)
        modelButton=button(tools,w("캐릭터 선택","キャラクター","Characters"),"vrm-models",false){selectModel()}
        renameButton=button(tools,w("이름 변경","名前変更","Rename"),"vrm-rename",false){rename()}
        settingsButton=button(tools,w("위치·크기","位置・サイズ","Placement"),"vrm-settings",false){settings()}
        applyButton=button(tools,w("배경화면 적용","壁紙に設定","Set wallpaper"),"vrm-apply",false){applyWallpaper()}
        status=label(w("VRM 0.x / 1.0 · 최대 64MiB · 이 기기에만 보관","VRM 0.x / 1.0 · 最大64MiB · 端末内保存","VRM 0.x / 1.0 · Up to 64 MiB · On-device only")).apply {
            tag="vrm-status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        };root.addView(status)
        retryButton=button(root,w("다시 열기","再試行","Try again"),"vrm-retry"){if(!busy)showViewer()}.apply {visibility=View.GONE}
        appliedButton=button(root,w("적용 상태 / 배경 재시도","適用状況 / 再試行","Applied wallpaper / Retry"),"vrm-applied"){showApplied()}
        setContentView(root)
        async({store.entries() to store.selected()}) {data->entries=data.first;entry=data.second;showViewer()}
    }
    private fun label(text: String)=TextView(this).apply {this.text=text;textSize=12f;setTextColor(0xff555469.toInt());setPadding(dp(12),dp(4),dp(12),dp(4))}
    private fun button(parent: LinearLayout,text: String,tag: String,weighted: Boolean=true,action: ()->Unit)=Button(this).apply {
        this.text=text;this.tag=tag;isAllCaps=false;minHeight=dp(48);textSize=13f
        setTextColor(0xff343b50.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xffeae5db.toInt())
        parent.addView(this,if(parent.orientation==LinearLayout.HORIZONTAL && weighted)LinearLayout.LayoutParams(0,-2,1f) else LinearLayout.LayoutParams(if(weighted)-1 else -2,-2))
        setOnClickListener {action()}
    }
    private fun controls() {
        importButton.isEnabled=!busy;modelButton.isEnabled=!busy && entries.isNotEmpty()
        renameButton.isEnabled=!busy && entry!=null;settingsButton.isEnabled=!busy && entry!=null
        applyButton.isEnabled=!busy && entry!=null && ready && !failed
        retryButton.isEnabled=!busy;appliedButton.isEnabled=!busy
    }
    private fun <T> async(work: ()->T,done: (T)->Unit) {
        if(busy || closed)return
        busy=true;controls()
        importer.execute {
            val result=runCatching(work)
            if(!closed)ui.post {
                if(closed || isFinishing)return@post
                busy=false;result.fold(done){operationFailed()};controls()
            }
        }
    }
    private fun describe(value: VrmEntry)=value.name+" · "+value.id.take(8)+" · VRM "+(if(value.format==VrmFormat.V0)"0.x" else "1.0")+" · "+String.format(java.util.Locale.ROOT,"%.1f",value.sizeBytes/1048576.0)+" MiB"
    private fun selectModel() {
        if(busy)return
        AlertDialog.Builder(this).setTitle(w("캐릭터 선택","キャラクター選択","Choose character"))
            .setSingleChoiceItems(entries.map(::describe).toTypedArray(),entries.indexOfFirst {it.id==entry?.id}) {dialog,index->
                val id=entries[index].id;dialog.dismiss()
                async({store.select(id);store.entries() to store.selected()}) {data->entries=data.first;entry=data.second;showViewer()}
            }.setNegativeButton(android.R.string.cancel,null).show()
    }
    private fun rename() {
        val selected=entry ?: return
        val input=EditText(this).apply {setSingleLine();setText(selected.name);filters=arrayOf(android.text.InputFilter.LengthFilter(80));contentDescription=w("캐릭터 이름","名前","Character name")}
        val dialog=AlertDialog.Builder(this).setTitle(w("이름 변경","名前変更","Rename"))
            .setView(input).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name=input.text.toString().trim()
            if(name.isBlank() || name.any {it<' '}) {input.error=w("이름을 입력해 주세요.","名前を入力してください。","Enter a name.");return@setOnClickListener}
            dialog.dismiss();async({store.rename(selected.id,name);store.entries() to store.selected()}) {data->entries=data.first;entry=data.second;status.text=entry?.let(::describe)}
        }};dialog.show()
    }
    private fun settings() {
        val selected=entry ?: return
        var placement=selected.placement
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8))}
        fun configure(){web?.let {VrmWebView.configure(it,placement)}}
        fun slider(title: String,tag: String,max: Int,initial: Int,value: (Int)->Int,change: (Int)->Unit): SeekBar {
            val label=label("");panel.addView(label)
            return SeekBar(this).apply {
                this.tag=tag;this.max=max;progress=initial;minimumHeight=dp(48);contentDescription=title
                fun show(v: Int){label.text=title+": "+value(v)+"%"}
                show(progress)
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar,p: Int,user: Boolean){show(p);change(p);configure()}
                    override fun onStartTrackingTouch(bar: SeekBar){}
                    override fun onStopTrackingTouch(bar: SeekBar){}
                });panel.addView(this)
            }
        }
        val x=slider(w("가로 위치","横位置","Horizontal"),"vrm-x",70,Math.round(placement.x*100)+35,{it-35}){placement=placement.copy(x=(it-35)/100f)}
        val y=slider(w("세로 위치","縦位置","Vertical"),"vrm-y",70,Math.round(placement.y*100)+35,{it-35}){placement=placement.copy(y=(it-35)/100f)}
        val scale=slider(w("크기","サイズ","Size"),"vrm-scale",100,Math.round(placement.scale*100)-50,{it+50}){placement=placement.copy(scale=(it+50)/100f)}
        @Suppress("DEPRECATION")
        val blink=Switch(this).apply {tag="vrm-blink";text=w("눈 깜박임","まばたき","Blink");isChecked=placement.blink;minHeight=dp(48)
            setOnCheckedChangeListener {_,checked->placement=placement.copy(blink=checked);configure()}}
        panel.addView(blink)
        button(panel,w("기본값으로","初期値","Reset"),"vrm-reset"){x.progress=35;y.progress=35;scale.progress=50;blink.isChecked=true}
        panel.addView(label(w("저장 후 배경화면을 다시 적용하면 반영됩니다.","保存後、壁紙を再設定してください。","Save, then apply the wallpaper again to update it.")))
        var saved=false
        val dialog=AlertDialog.Builder(this).setTitle(w("위치·크기·깜박임","位置・サイズ・まばたき","Placement and blink"))
            .setView(ScrollView(this).apply {addView(panel)}).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(w("저장","保存","Save")){_,_->
                saved=true;async({store.savePlacement(selected.id,placement);store.entries() to store.selected()}) {data->
                    entries=data.first;entry=data.second;entry?.let {e->web?.let {VrmWebView.configure(it,e.placement)}}
                }
            }.create()
        dialog.setOnDismissListener {settingsDialog=null;if(!saved)web?.let {VrmWebView.configure(it,selected.placement)}}
        settingsDialog=dialog;dialog.show()
    }
    @Suppress("DEPRECATION")
    private fun chooseModel() {
        if(busy || closed)return
        try {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type="*/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },41)}catch(_: Exception){operationFailed()}
    }
    @Deprecated("Platform document picker result for API 23+")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(wallpaper.onActivityResult(requestCode,resultCode,data)){pendingReload=true;return}
        if(requestCode==42) {pendingReload=true;return}
        if(requestCode!=41 || resultCode!=RESULT_OK || busy || closed)return
        val uri=data?.data ?: return
        if(uri.scheme!="content"){operationFailed();return}
        status.text=w("모델을 확인하고 있습니다…","モデルを確認しています…","Checking model…")
        val cancellation=CancellationSignal();importCancellation=cancellation
        async({
            val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null,cancellation)?.use {cursor->if(cursor.moveToFirst())cursor.getString(0) else null} ?: "test.vrm"
            contentResolver.openAssetFileDescriptor(uri,"r",cancellation)?.use {descriptor->
                descriptor.createInputStream().use {stream->
                    importStream.set(stream)
                    try {if(closed)throw java.io.InterruptedIOException("Preview closed");store.importModel(stream,name)}
                    finally {importStream.compareAndSet(stream,null)}
                }
            } ?: error("Cannot open model")
            store.entries() to store.selected()
        }) {data->importCancellation=null;entries=data.first;entry=data.second;pendingReload=true;if(active)reloadModel()}
    }
    private fun operationFailed() {
        importCancellation=null
        entry?.let {saved->web?.let {VrmWebView.configure(it,saved.placement)}}
        status.text=w("작업에 실패했습니다. 이전 자료는 보존됩니다. 내장 텍스처 VRM 0.x/1.0 · 64MiB 이하를 사용해 주세요.",
            "処理できません。既存データは保持されます。テクスチャ内蔵VRM 0.x/1.0・64MiB以下をご利用ください。",
            "Operation failed; previous data is preserved. Use embedded VRM 0.x/1.0 up to 64 MiB.")
        if(web==null)retryButton.visibility=View.VISIBLE
    }
    @Suppress("DEPRECATION")
    private fun applyWallpaper() {
        val selected=entry ?: return
        if(!applyButton.isEnabled)return
        wallpaper.applyVrm(selected)
    }
    private fun appliedTargets(): List<Pair<String,String>> {
        val manager=WallpaperManager.getInstance(this);val targets=mutableListOf<Pair<String,String>>()
        VrmWallpaperStore.key(this,manager.wallpaperInfo?.component)?.let {targets.add(w("홈 화면","ホーム","Home") to it)}
        if(Build.VERSION.SDK_INT>=34)VrmWallpaperStore.key(this,manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component)?.let {targets.add(w("잠금 화면","ロック","Lock") to it)}
        return targets
    }
    private fun showApplied() {
        async({appliedTargets().map {it to VrmWallpaperStore.snapshot(this,it.second)}}) {targets->
            if(targets.isEmpty())AlertDialog.Builder(this).setMessage(w("시스템에서 확인된 VRM 배경화면이 없습니다.","VRM壁紙を確認できませんでした。","No applied VRM wallpaper was confirmed by the system.")).setPositiveButton(android.R.string.ok,null).show()
            else AlertDialog.Builder(this).setTitle(w("적용된 배경화면 재시도","適用済み壁紙を再試行","Retry applied wallpaper"))
                .setItems(targets.map {(target,snapshot)->target.first+" · "+(entries.find {it.id==snapshot.modelId}?.name ?: snapshot.modelId.take(8))}.toTypedArray()) {_,i->
                    val (target,snapshot)=targets[i]
                    async({VrmWallpaperStore.retry(this,target.second,snapshot.generation)}) {status.text=w("재시도를 요청했습니다. 홈/잠금 화면에서 확인하세요.","再試行を要求しました。ホーム/ロック画面をご確認ください。","Retry requested. Check your home or lock screen.")}
                }.setNegativeButton(android.R.string.cancel,null).show()
        }
    }
    private val poll=object: Runnable {
        override fun run() {
            val view=web ?: return
            if(!active || failed || closed)return
            view.evaluateJavascript("window.vrmPreview?.info || null") {value->
                if(web!==view || closed || failed)return@evaluateJavascript
                val info=runCatching {JSONObject(value)}.getOrNull()
                val state=info?.optString("state")
                if(state=="error" || (entry!=null && health.sample(state=="ready",info?.optLong("frames",0) ?: 0,now())!=null)) {
                    disposeWebView();showWebError(info?.optString("failure")=="MEMORY")
                }
                else {ready=state=="ready" && (info?.optLong("frames",0) ?: 0)>0;controls()}
            }
            ui.postDelayed(this,500)
        }
    }
    private fun showViewer() {
        disposeWebView();failed=false;retryButton.visibility=View.GONE
        val selected=entry
        if(selected!=null)status.text=describe(selected)
        health.newAttempt(now());health.setVisible(active,now())
        lateinit var view: WebView
        view=VrmWebView.create(this,{selected?.let {store.openModel(it.id)}},{failure->
            if(web===view && !closed){disposeWebView(failure==VrmFailure.RENDERER);showWebError()}
        }) {v->if(web===v && !closed){entry?.let {VrmWebView.configure(v,it.placement)};v.evaluateJavascript(if(active)"window.vrmPreview?.resume()" else "window.vrmPreview?.pause()",null)}}
        web=view;view.tag="vrm-webview";root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        view.loadUrl(VrmWebView.url(WebViews.selectedLanguage(this),selected!=null,false))
        if(!active)view.onPause() else ui.post(poll)
        controls()
    }
    private fun showWebError(memory: Boolean=false) {
        failed=true;ready=false;ui.removeCallbacks(poll);controls()
        status.text=if(memory)w("원본 화질로 표시할 메모리가 부족합니다. 다른 앱을 닫고 다시 시도하세요. 원본 파일은 보존됩니다.",
            "元の画質で表示するメモリが不足しています。他のアプリを閉じて再試行してください。元ファイルは保持されます。",
            "Not enough memory for original-quality textures. Close other apps and retry. The original file is preserved.")
        else w("3D 보기가 중단되었습니다. 다시 열거나 다른 모델을 선택하세요.","3D表示が停止しました。再試行するか別のモデルを選んでください。","The 3D viewer stopped. Try again or choose another model.")
        retryButton.visibility=View.VISIBLE
    }
    private fun reloadModel(){pendingReload=false;showViewer()}
    override fun onResume() {
        super.onResume();active=true;health.setVisible(true,now())
        wallpaper.resume()
        if(pendingReload)reloadModel()
        web?.apply {onResume();evaluateJavascript("window.vrmPreview?.resume()",null)}
        ui.removeCallbacks(poll);ui.post(poll)
    }
    override fun onPause() {
        wallpaper.pause()
        active=false;health.setVisible(false,now());ui.removeCallbacks(poll)
        web?.apply {evaluateJavascript("window.vrmPreview?.pause()",null);onPause()};super.onPause()
    }
    private fun disposeWebView(crashed: Boolean=false) {
        ready=false;ui.removeCallbacks(poll)
        val view=web ?: return;web=null
        if(!crashed){view.evaluateJavascript("window.vrmPreview?.dispose()",null);view.stopLoading()}
        (view.parent as? ViewGroup)?.removeView(view);view.destroy()
    }
    override fun onDestroy() {
        wallpaper.close()
        closed=true;active=false;settingsDialog?.dismiss();importer.shutdownNow();ui.removeCallbacksAndMessages(null)
        val cancellation=importCancellation;val stream=importStream.getAndSet(null)
        if(cancellation!=null || stream!=null)Thread({runCatching {stream?.close()};runCatching {cancellation?.cancel()}},"vrm-import-close").start()
        disposeWebView();super.onDestroy()
    }
    override fun onSaveInstanceState(out: Bundle){wallpaper.saveState(out);super.onSaveInstanceState(out)}
}

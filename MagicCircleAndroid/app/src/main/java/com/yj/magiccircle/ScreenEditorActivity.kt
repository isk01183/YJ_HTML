package com.yj.magiccircle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors

class ScreenEditorActivity: Activity() {
    private lateinit var editor: ScreenEditorView
    private lateinit var tools: LinearLayout
    private lateinit var library: MediaLibrary
    private lateinit var name: EditText
    private lateinit var status: TextView
    private var dirty=false
    private var busy=false
    private var initial: EditorDraft?=null
    private var previewDialog: android.app.Dialog?=null
    private var pendingCharacterId: String?=null
    private var foreground=false
    private var vrmNames=emptyMap<String,String>()
    private val ui=Handler(Looper.getMainLooper())
    private val language get()=WebViews.selectedLanguage(this)
    private fun w(k: String,j: String,e: String)=words(language,k,j,e)
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private val saveDraft=Runnable { persistDraft() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        library=MediaLibrary.get(this)
        if(!library.isReadable()) {finish();return}
        val key=state?.getString("key") ?: intent.getStringExtra("themeId") ?: "scene-${UUID.randomUUID()}"
        val purpose=runCatching {ScenePurpose.valueOf(intent.getStringExtra("scenePurpose") ?: "CHARGING")}.getOrDefault(ScenePurpose.CHARGING)
        val scene=library.scene(key) ?: if(SceneRules.isSceneId(key))ScreenScene(key,w("새 화면","新しい画面","New scene"),purpose,emptyList())else null
        if(scene==null && !ThemeSelection.isValid(key) && library.find(key)==null) {finish();return}
        initial=EditorDraft(key,scene,if(scene?.purpose==ScenePurpose.WALLPAPER) emptyList() else library.chargeInfo(key))
        val draft=library.editorDraft(key) ?: initial!!
        dirty=library.editorDraft(key)!=null
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff080e18.toInt());setPadding(dp(16),dp(8),dp(16),dp(8))}
        root.setOnApplyWindowInsetsListener {v,insets->
            @Suppress("DEPRECATION")
            v.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(8)+insets.systemWindowInsetTop,dp(16)+insets.systemWindowInsetRight,dp(8)+insets.systemWindowInsetBottom)
            insets
        }
        name=EditText(this).apply {setText(draft.scene?.name ?: key);setTextColor(0xfff2dfb9.toInt());textSize=22f;typeface=Typeface.create("serif",Typeface.NORMAL);isSingleLine=true;isEnabled=draft.scene!=null;contentDescription=w("작품 이름","作品名","Scene name")}
        root.addView(name,LinearLayout.LayoutParams(-1,dp(52)))
        status=TextView(this).apply {text=w("목록에서 선택 · 드래그로 이동 · 두 손가락으로 확대·회전","一覧で選択・ドラッグで移動・2本指で拡大と回転","Select in list · Drag to move · Two fingers to resize & rotate");setTextColor(0xffadbbcb.toInt());textSize=12f;setPadding(0,dp(4),0,dp(8))}
        root.addView(status)
        val workspace=LinearLayout(this).apply {orientation=if(resources.configuration.screenWidthDp>=700)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        editor=ScreenEditorView(this)
        val wide=workspace.orientation==LinearLayout.HORIZONTAL
        workspace.addView(editor,if(wide)LinearLayout.LayoutParams(0,-1,1f)else LinearLayout.LayoutParams(-1,0,1.1f))
        val scroll=ScrollView(this);tools=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8))};scroll.addView(tools)
        workspace.addView(scroll,if(wide)LinearLayout.LayoutParams(dp(330),-1)else LinearLayout.LayoutParams(-1,0,1f))
        val actions=row(root)
        button(actions,w("취소","取消","Cancel")){leave()}
        button(actions,w("미리보기","プレビュー","Preview")){preview()}
        button(actions,w("저장","保存","Save")){save()}
        setContentView(root)
        editor.onError={status.text=w("이미지 또는 캐릭터를 불러오지 못했습니다. 파일을 확인하거나 캐릭터를 다시 선택하세요.","画像またはキャラクターを読み込めません。ファイルを確認するか再選択してください。","Cannot load image or character. Check files or choose the character again.")}
        editor.setDraft(draft)
        editor.setOnDraftChanged { dirty=true;ui.removeCallbacks(saveDraft);ui.postDelayed(saveDraft,250) }
        editor.onSelectionChanged={renderTools()}
        renderTools()
        if(draft.scene?.vrm!=null)IO.execute {val names=runCatching {VrmModelStore.get(this).entries().associate {it.id to vrmLabel(it)}}.getOrDefault(emptyMap())
            ui.post {if(!isDestroyed && !isFinishing){vrmNames=names;renderTools()}}}
        (if(state!=null)state.getString("pendingCharacterId")else intent.getStringExtra("characterId"))?.let {addCharacter(it)}
        if(Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){leave()}
    }
    private fun current(): EditorDraft {val d=editor.currentDraft();return d.copy(scene=d.scene?.copy(name=name.text.toString().trim()))}
    private fun persistDraft(): Boolean = try {library.saveEditorDraft(current());true}catch(_: Exception){status.text=w("저장 실패 — 기존 작품은 유지됩니다","保存失敗 — 既存作品は保護されています","Save failed — existing scene preserved");false}
    private fun save() {
        if(busy)return
        ui.removeCallbacks(saveDraft)
        try {val d=current();if(d.scene!=null)library.saveScene(d.scene,d.information)else library.saveChargeInfo(d.key,d.information);dirty=false;finish()}
        catch(_: Exception) {persistDraft();status.text=w("이름·파일·저장 공간을 확인하세요","名前・ファイル・空き容量を確認","Check name, files and storage space")}
    }
    private fun leave() {
        if(busy)return
        if(!dirty && current()==initial) {finish();return}
        AlertDialog.Builder(this).setMessage(w("변경 사항을 저장할까요?","変更を保存しますか？","Save changes?"))
            .setPositiveButton(w("저장","保存","Save")){_,_->save()}
            .setNegativeButton(w("버리기","破棄","Discard")){_,_->ui.removeCallbacks(saveDraft);try {library.discardEditorDraft(current().key);dirty=false;finish()}catch(_: Exception){persistDraft()}}
            .setNeutralButton(w("계속 편집","編集を続ける","Keep editing"),null).show()
    }
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ uses the native dispatcher registered in onCreate.
    @Deprecated("API 23–32 fallback") override fun onBackPressed()=leave()
    override fun onSaveInstanceState(out: Bundle) {ui.removeCallbacks(saveDraft);persistDraft();out.putString("key",current().key);out.putString("pendingCharacterId",pendingCharacterId);super.onSaveInstanceState(out)}
    override fun onStart() {super.onStart();foreground=true;if(::editor.isInitialized && previewDialog==null)editor.setRenderingEnabled(true)
        if(intent.getBooleanExtra("previewScene",false)){intent.removeExtra("previewScene");ui.post {if(foreground && !isFinishing)preview()}}}
    override fun onStop() {foreground=false;previewDialog?.dismiss();if(::editor.isInitialized)editor.setRenderingEnabled(false);super.onStop();if(!isFinishing && ::editor.isInitialized) {ui.removeCallbacks(saveDraft);persistDraft()}}
    override fun onDestroy() {previewDialog?.dismiss();ui.removeCallbacksAndMessages(null);if(::editor.isInitialized)editor.close();super.onDestroy()}
    private fun row(parent: LinearLayout)=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;parent.addView(this,LinearLayout.LayoutParams(-1,-2))}
    private fun button(parent: LinearLayout,label: String,action:()->Unit)=Button(this).apply {
        text=label;textSize=12f;isAllCaps=false;minHeight=dp(48);setTextColor(0xfff3dfb6.toInt())
        backgroundTintList=android.content.res.ColorStateList.valueOf(0xff202b3a.toInt())
        val params=if(parent.orientation==LinearLayout.HORIZONTAL)LinearLayout.LayoutParams(0,-2,1f)else LinearLayout.LayoutParams(-1,-2)
        params.setMargins(dp(2),dp(2),dp(2),dp(2));parent.addView(this,params)
        setOnClickListener {if(!busy)action()}
    }
    private fun heading(text: String) {tools.addView(TextView(this).apply {this.text=text;setTextColor(0xffc0cede.toInt());textSize=13f;setPadding(0,dp(14),0,dp(6))})}
    private fun fieldName(f: InfoField)=when(f){
        InfoField.BATTERY->w("배터리 %","バッテリー %","Battery %");InfoField.STATUS->w("충전 상태","充電状態","Charge status")
        InfoField.TEMPERATURE->w("온도","温度","Temperature");InfoField.HEALTH->w("배터리 상태","バッテリー状態","Health")
        InfoField.CONNECTION->w("연결 방식","接続方式","Connection");InfoField.METER->w("충전 막대","充電バー","Meter");InfoField.MESSAGE->w("단계 메시지","段階メッセージ","Stage message")}
    private fun renderTools() {
        tools.removeAllViews();val d=editor.currentDraft();val s=d.scene
        if(s!=null) {
            if(s.purpose==ScenePurpose.WALLPAPER) {
                heading(w("캐릭터 레이어","キャラクターレイヤー","Character layer"))
                button(tools,w("보관함에서 캐릭터 선택","一覧からキャラクターを選択","Choose saved character")){chooseCharacter()}
                s.vrm?.let { v->
                    button(tools,(if(editor.selectedVrm)"● " else "○ ")+(vrmNames[v.modelId] ?: "VRM · ${v.modelId.take(8)}")){editor.selectedVrm=true;renderTools()}
                    if(editor.selectedVrm) {
                        heading(w("정면 고정 · 이동·확대는 두 손가락으로도 가능","正面固定・指で移動と拡大","Front view · Drag and pinch to position"))
                        slider("X %",(v.placement.screenX()*100).toInt(),15,85){n->editor.modifyVrm {it.copy(placement=it.placement.withScreenPosition(n/100f,it.placement.screenY()))}}
                        slider("Y %",(v.placement.screenY()*100).toInt(),15,85){n->editor.modifyVrm {it.copy(placement=it.placement.withScreenPosition(it.placement.screenX(),n/100f))}}
                        slider(w("크기 %","サイズ %","Size %"),(v.placement.scale*100).toInt(),50,150){n->editor.modifyVrm {it.copy(placement=it.placement.copy(scale=n/100f))}}
                        button(tools,w("가운데로","中央へ","Center")){editor.modifyVrm {it.copy(placement=it.placement.withScreenPosition(.5f,.5f))};renderTools()}
                        val options=row(tools)
                        button(options,w("표시 / 숨김","表示 / 非表示","Show / Hide")){editor.modifyVrm {it.copy(visible=!it.visible)};renderTools()}
                        button(options,w("눈 깜박임: ","まばたき: ","Blink: ")+(if(v.placement.blink)"ON" else "OFF")){editor.modifyVrm {it.copy(placement=it.placement.copy(blink=!it.placement.blink))};renderTools()}
                        slider(w("겹침 순서","重なり順","Layer order"),v.beforeImage,0,s.layers.size){n->editor.modifyVrm {it.copy(beforeImage=n)}}
                        val order=row(tools)
                        button(order,w("이미지 뒤로","画像の背面","Behind images")){editor.modifyVrm {it.copy(beforeImage=0)};renderTools()}
                        button(order,w("이미지 앞으로","画像の前面","Above images")){editor.modifyVrm {it.copy(beforeImage=editor.currentDraft().scene!!.layers.size)};renderTools()}
                        button(tools,w("캐릭터 레이어 제거","キャラクターを削除","Remove character layer")){val latest=editor.currentDraft();editor.change(latest.copy(scene=latest.scene!!.copy(vrm=null)));editor.selectedVrm=false;renderTools()}
                    }
                }
                s.character?.let { c->
                    button(tools,(if(editor.selectedCharacter)"● " else "○ ")+c.definition.name){editor.selectedCharacter=true;renderTools()}
                    if(editor.selectedCharacter) {
                        position(c.x,c.y){x,y->editor.modifyCharacter {it.copy(x=x,y=y)}}
                        slider(w("크기 %","サイズ %","Size %"),(c.width*100).toInt(),5,400){v->editor.modifyCharacter {it.copy(width=v/100f)}}
                        slider(w("각도 °","角度 °","Rotation °"),c.angle.toInt(),-180,179){v->editor.modifyCharacter {it.copy(angle=v.toFloat())}}
                        val actions=row(tools)
                        button(actions,w("좌우 반전","左右反転","Flip")){editor.modifyCharacter {it.copy(flipX=!it.flipX)}}
                        button(actions,w("표시 / 숨김","表示 / 非表示","Show / Hide")){editor.modifyCharacter {it.copy(visible=!it.visible)}}
                        val order=row(tools)
                        button(order,w("이미지 뒤로","画像の背面","Behind images")){editor.modifyCharacter {it.copy(beforeImage=0)}}
                        button(order,w("이미지 앞으로","画像の前面","Above images")){editor.modifyCharacter {it.copy(beforeImage=s.layers.size)}}
                        slider(w("겹침 순서","重なり順","Layer order"),c.beforeImage,0,s.layers.size){v->editor.modifyCharacter {it.copy(beforeImage=v)}}
                        button(tools,w("보관함의 최신 외형 가져오기","最新の外見を取り込む","Refresh from saved character")){addCharacter(c.definition.id)}
                        button(tools,w("캐릭터 레이어 제거","キャラクターを削除","Remove character layer")){val latest=editor.currentDraft();editor.change(latest.copy(scene=latest.scene!!.copy(character=null)));editor.selectedCharacter=false;renderTools()}
                    }
                }
            }
            heading(w("이미지 레이어 · ${s.layers.size}/8","画像レイヤー · ${s.layers.size}/8","Image layers · ${s.layers.size}/8"))
            val add=row(tools);button(add,w("파일 추가","ファイル追加","Import")){importFile()};button(add,w("보관함","ライブラリ","Library")){chooseExisting()}
            for((i,l) in s.layers.withIndex()) button(tools,(if(l.id==editor.selectedLayer)"● " else "○ ")+"${i+1}. "+(library.find(l.mediaId)?.name ?: "Image")+(if(l.visible)"" else " ◌")) {editor.selectedLayer=l.id;editor.selectedField=null;renderTools()}
            val layer=s.layers.find {it.id==editor.selectedLayer}
            if(layer!=null) {
                position(layer.x,layer.y){x,y->editor.modifyLayer {it.copy(x=x,y=y)}}
                slider(w("크기 %","サイズ %","Size %"),(layer.width*100).toInt(),5,400){v->editor.modifyLayer {it.copy(width=v/100f)}}
                slider(w("각도 °","角度 °","Rotation °"),layer.angle.toInt(),-180,179){v->editor.modifyLayer {it.copy(angle=v.toFloat())}}
                val r=row(tools);button(r,w("좌우 반전","左右反転","Flip")){editor.modifyLayer {it.copy(flipX=!it.flipX)};renderTools()}
                button(r,w("표시 / 숨김","表示 / 非表示","Show / Hide")){editor.modifyLayer {it.copy(visible=!it.visible)};renderTools()}
                val order=row(tools);button(order,w("뒤로","背面へ","Back")){moveLayer(-1)};button(order,w("앞으로","前面へ","Front")){moveLayer(1)}
                button(tools,w("레이어 제거","レイヤー削除","Remove layer")){val latest=editor.currentDraft();latest.scene?.let {editor.change(latest.copy(scene=it.copy(layers=it.layers.filter {item->item.id!=layer.id})))};editor.selectedLayer=null;renderTools()}
            }
        }
        if(s?.purpose!=ScenePurpose.WALLPAPER) {
            heading(w("충전 정보 · 위치와 표시","充電情報 · 位置と表示","Charge information · Position & visibility"))
            for(f in InfoField.entries) {
                val p=editor.information().single {it.field==f}
                val r=row(tools);button(r,(if(editor.selectedField==f)"● " else "")+fieldName(f)){editor.selectedField=f;editor.selectedLayer=null;renderTools()}
                button(r,if(p.visible)w("켜짐","表示","On")else w("꺼짐","非表示","Off")) {
                    editor.toggleField(f);renderTools()
                }
            }
            editor.information().find {it.field==editor.selectedField}?.let {p->position(p.x,p.y){x,y->editor.modifyField {it.copy(x=x,y=y)}}}
            button(tools,w("단계별 문구 편집","段階別テキストを編集","Edit stage text")){editMessages()}
            val stages=row(tools)
            for((label,progress) in listOf(w("연결","接続","Connected") to .1f,w("충전","充電","Charging") to .5f,w("완료","完了","Complete") to .95f))
                button(stages,label){editor.showEditorStage(progress)}
            button(tools,w("정보 기본 배치","情報の初期配置","Reset information")){editor.resetInformation();renderTools()}
            heading(w("애니메이션 표시 시간","アニメーション表示時間","Animation duration"))
            val r=row(tools);for(ms in listOf(1000,3000,5000,7000)) button(r,(if(library.durationMs()==ms)"● " else "")+"${ms/1000}s") {try {library.setDurationMs(ms);renderTools()}catch(_:Exception){persistDraft()}}
        }
    }
    private fun editMessages() {
        val saved=editor.information().single {it.field==InfoField.MESSAGE}.messages
        val values=saved ?: ChargeInfoView.defaultMessages(language)
        val form=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        form.addView(TextView(this).apply {text=w("각 단계 최대 120자 · 빈 문구는 표시하지 않습니다","各段階120文字まで・空欄は非表示","Up to 120 characters per stage · Leave empty to hide")})
        val inputs=listOf(w("연결 감지","接続検知","Connected") to values.connected,w("충전 시작","充電開始","Charging") to values.charging,w("완료","完了","Complete") to values.complete).map { (label,value) ->
            form.addView(TextView(this).apply {text=label;setPadding(0,dp(12),0,0)})
            EditText(this).apply {setText(value);contentDescription=label;maxLines=3;filters=arrayOf(android.text.InputFilter.LengthFilter(120));form.addView(this,LinearLayout.LayoutParams(-1,-2))}
        }
        val scroll=ScrollView(this).apply {addView(form)}
        fun apply(messages: StageMessages?) {
            val d=editor.currentDraft()
            editor.change(d.copy(information=editor.information().map {if(it.field==InfoField.MESSAGE)it.copy(messages=messages,visible=true)else it}))
            editor.selectedField=InfoField.MESSAGE;editor.selectedLayer=null;editor.showEditorStage(.1f);renderTools()
        }
        val dialog=AlertDialog.Builder(this).setTitle(w("단계별 문구","段階別テキスト","Stage text")).setView(scroll)
            .setPositiveButton(w("적용","適用","Apply"),null)
            .setNeutralButton(w("기본 문구","初期テキスト","Default text")){_,_->apply(null)}
            .setNegativeButton(w("취소","取消","Cancel"),null).create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text=StageMessages(inputs[0].text.toString(),inputs[1].text.toString(),inputs[2].text.toString())
            if(runCatching {SceneRules.validateInformation(listOf(InfoPlacement(InfoField.MESSAGE,.5f,.5f,true,text)))}.isSuccess) {apply(text);dialog.dismiss()}
            else inputs.first().error=w("지원하지 않는 제어 문자가 있습니다","使用できない制御文字があります","Unsupported control character")
        }}
        dialog.show()
    }
    private fun position(x: Float,y: Float,change:(Float,Float)->Unit) {
        slider("X %",(x*100).toInt(),0,100){value->val d=editor.currentDraft();val l=d.scene?.layers?.find {it.id==editor.selectedLayer};val c=if(editor.selectedCharacter)d.scene?.character else null;val p=editor.information().find {it.field==editor.selectedField};change(value/100f,l?.y ?: c?.y ?: p?.y ?: y)}
        slider("Y %",(y*100).toInt(),0,100){value->val d=editor.currentDraft();val l=d.scene?.layers?.find {it.id==editor.selectedLayer};val c=if(editor.selectedCharacter)d.scene?.character else null;val p=editor.information().find {it.field==editor.selectedField};change(l?.x ?: c?.x ?: p?.x ?: x,value/100f)}
        button(tools,w("가운데로","中央へ","Center")){change(.5f,.5f);renderTools()}
    }
    private fun slider(label: String,value: Int,min: Int,max: Int,change:(Int)->Unit) {
        var currentValue=value
        val row=row(tools);val text=TextView(this).apply {this.text="$label  $value";setTextColor(Color.LTGRAY);textSize=12f;gravity=Gravity.CENTER_VERTICAL;row.addView(this,LinearLayout.LayoutParams(0,dp(48),1f))}
        button(row,w("숫자 입력","数値入力","Value")) {
            val input=EditText(this).apply {inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED;setText(currentValue.toString())}
            AlertDialog.Builder(this).setTitle("$label ($min–$max)").setView(input).setPositiveButton("OK"){_,_->input.text.toString().toIntOrNull()?.let {change(it.coerceIn(min,max));renderTools()}}.setNegativeButton(w("취소","取消","Cancel"),null).show()
        }
        tools.addView(SeekBar(this).apply {this.max=max-min;progress=value-min;contentDescription=label;minimumHeight=dp(48)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {override fun onProgressChanged(s: SeekBar,v: Int,user: Boolean){if(user){currentValue=v+min;text.text="$label  $currentValue";change(currentValue)}};override fun onStartTrackingTouch(s: SeekBar){};override fun onStopTrackingTouch(s: SeekBar){} })},LinearLayout.LayoutParams(-1,dp(48)))
    }
    private fun moveLayer(delta: Int) {val d=editor.currentDraft();val s=d.scene ?: return;val list=s.layers.toMutableList();val i=list.indexOfFirst {it.id==editor.selectedLayer};val j=i+delta;if(i>=0 && j in list.indices){java.util.Collections.swap(list,i,j);editor.change(d.copy(scene=s.copy(layers=list)));renderTools()}}
    private fun addLayer(id: String) {
        val d=current();val s=d.scene ?: return
        val next=s.copy(layers=s.layers+ImageLayer(UUID.randomUUID().toString(),id,.5f,.5f,1f,0f,false,true))
        try {SceneRules.validate(next,library.items().associate {it.id to it.mime});editor.change(d.copy(scene=next));editor.selectedLayer=next.layers.last().id;editor.selectedField=null;renderTools();persistDraft()}
        catch(_:Exception){status.text=w("이미지 8개 / GIF 2개까지만 추가할 수 있습니다","画像8枚 / GIF2枚までです","Up to 8 images / 2 GIF layers")}
    }
    private fun chooseExisting() {val items=library.items();AlertDialog.Builder(this).setTitle(w("보관함에서 추가","ライブラリから追加","Add from library")).setItems(items.map {it.name}.toTypedArray()){_,i->addLayer(items[i].id)}.show()}
    private fun chooseCharacter() {
        AlertDialog.Builder(this).setTitle(w("캐릭터 종류","キャラクターの種類","Character type"))
            .setItems(arrayOf(w("VRM · 가져온 3D 캐릭터","VRM · 読み込んだ3Dキャラクター","VRM · Imported 3D characters"),"2.5D")){_,i->if(i==0)chooseVrm()else chooseIllustratedCharacter()}.show()
    }
    private fun vrmLabel(entry: VrmEntry)="${entry.name} · VRM ${if(entry.format==VrmFormat.V1)"1" else "0"} · ${entry.id.take(8)}"
    private fun chooseVrm() {
        busy=true
        IO.execute {val result=runCatching {VrmModelStore.get(this).entries()};ui.post {
            if(isDestroyed || isFinishing)return@post;busy=false
            result.onSuccess {entries->
                vrmNames=entries.associate {it.id to vrmLabel(it)}
                AlertDialog.Builder(this).setTitle(w("VRM 캐릭터 선택","VRMキャラクター選択","Choose VRM character"))
                    .setItems(entries.map(::vrmLabel).toTypedArray()){_,i->
                        val d=editor.currentDraft();val s=d.scene ?: return@setItems
                        val v=s.vrm?.copy(modelId=entries[i].id) ?: VrmSceneLayer(entries[i].id,beforeImage=s.layers.size)
                        editor.change(d.copy(scene=s.copy(character=null,vrm=v)));editor.selectedVrm=true;renderTools();persistDraft()
                    }.setPositiveButton(w("VRM 가져오기 / 관리","VRM読込 / 管理","Import / Manage VRM")){_,_->
                        persistDraft();startActivityForResult(Intent(this,VrmPreviewActivity::class.java),82)
                    }.setNegativeButton(w("닫기","閉じる","Close"),null).show()
            }.onFailure {status.text=w("VRM 보관함을 읽지 못했습니다. 기존 작품은 유지됩니다.","VRM一覧を読み込めません。作品は保持されます。","Cannot read VRM library. Existing scene preserved.")}
        }}
    }
    private fun chooseIllustratedCharacter() {
        busy=true
        IO.execute {val result=runCatching {CharacterStore.get(this).list()};ui.post {
            if(isDestroyed || isFinishing)return@post;busy=false
            result.onSuccess { list->if(list.isEmpty())status.text=w("먼저 캐릭터를 만들고 저장하세요","先にキャラクターを保存してください","Create and save a character first")
                else AlertDialog.Builder(this).setTitle(w("캐릭터 선택","キャラクター選択","Choose character")).setItems(list.map {it.name}.toTypedArray()){_,i->addCharacter(list[i].id)}.show()
            }.onFailure {status.text=w("캐릭터 보관함을 읽지 못했습니다","一覧を読み込めません","Could not read character library")}
        }}
    }
    private fun addCharacter(id: String) {
        if(editor.currentDraft().scene?.purpose!=ScenePurpose.WALLPAPER)return
        busy=true;pendingCharacterId=id
        IO.execute {val result=runCatching {CharacterStore.get(this).find(id) ?: error("Character missing")};ui.post {
            if(isDestroyed || isFinishing)return@post;busy=false;pendingCharacterId=null
            result.onSuccess { c->
                val d=editor.currentDraft();val s=d.scene ?: return@onSuccess
                editor.change(d.copy(scene=s.copy(vrm=null,character=s.character?.copy(definition=c) ?: CharacterLayer(c,beforeImage=s.layers.size))))
                editor.selectedCharacter=true;renderTools();persistDraft()
            }.onFailure {status.text=w("캐릭터를 찾지 못했습니다. 기존 작품은 유지됩니다.","キャラクターが見つかりません。作品は保持されます。","Character unavailable. Existing scene preserved.")}
        }}
    }
    private fun importFile() {persistDraft();try {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/jpeg","image/png","image/gif")).putExtra(Intent.EXTRA_LOCAL_ONLY,true),81)}catch(_:Exception){status.text=w("파일 선택기를 열 수 없습니다","ファイル選択不可","File picker unavailable")}}
    @Deprecated("Platform document picker") override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==82){chooseVrm();return}
        if(requestCode!=81 || resultCode!=RESULT_OK)return
        val uri=data?.data ?: return
        busy=true;status.text=w("이미지를 안전하게 가져오는 중…","画像を読み込み中…","Importing image…")
        val key=current().key
        IO.execute {val result=runCatching {library.importEditorDocument(uri)};ui.post {
            if(isDestroyed || isFinishing)return@post
            busy=false
            if(current().key==key) result.onSuccess {addLayer(it);status.text=w("이미지를 추가했습니다","画像を追加しました","Image added")}.onFailure {status.text=w("파일 형식·크기를 확인하세요","形式とサイズを確認","Check file type and size")}
        }}
    }
    private fun preview() {
        previewDialog?.dismiss()
        val d=current();val dialog=android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen)
        if(d.scene?.vrm!=null) {
            val scene=d.scene
            var lease: AutoCloseable?=null
            try {
                lease=library.leaseMedia(scene.layers.map {it.mediaId})
                editor.setRenderingEnabled(false)
                val state=TextView(this).apply {setTextColor(Color.WHITE);text=w("캐릭터 준비 중…","準備中…","Preparing character…");setPadding(dp(16),dp(28),dp(16),0)}
                val host=VrmSceneView(this,scene,{VrmModelStore.get(this).openModel(scene.vrm!!.modelId)},{library.open(it,false)},
                    {state.text=""},{state.text=w("미리보기를 불러오지 못했습니다. 캐릭터나 파일을 다시 선택하세요.","プレビューを読み込めません。再選択してください。","Cannot load preview. Choose the character or files again.")})
                val frame=FrameLayout(this).apply {addView(host,FrameLayout.LayoutParams(-1,-1));addView(state,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))}
                val bar=LinearLayout(this).apply {setPadding(dp(12),dp(24),dp(12),dp(24))}
                button(bar,w("닫기","閉じる","Close")){dialog.dismiss()}
                frame.addView(bar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
                previewDialog=dialog;dialog.setContentView(frame)
                dialog.setOnDismissListener {host.close();lease.close();if(previewDialog===dialog)previewDialog=null;if(foreground && !isFinishing)editor.setRenderingEnabled(true)}
                dialog.show()
            } catch(_: Exception){lease?.close();if(foreground)editor.setRenderingEnabled(true);editor.onError?.invoke()}
            return
        }
        if(d.scene?.purpose==ScenePurpose.WALLPAPER) {
            val host=WallpaperScenePreview(this,d.scene)
            val frame=FrameLayout(this).apply {addView(host,FrameLayout.LayoutParams(-1,-1))}
            val bar=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(dp(12),dp(24),dp(12),dp(24))}
            button(bar,w("닫기","閉じる","Close")){dialog.dismiss()}
            button(bar,w("정지 / 움직임","静止 / 動き","Still / Motion")){host.animateScene=!host.animateScene;host.invalidate()}
            frame.addView(bar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
            previewDialog=dialog;dialog.setContentView(frame);dialog.setOnDismissListener {host.close();if(previewDialog===dialog)previewDialog=null};dialog.show();return
        }
        val host=ChargingSceneView(this,d.key,d.information,d.scene)
        val timeout=Runnable {dialog.dismiss()}
        previewDialog=dialog
        dialog.setContentView(host);dialog.setOnDismissListener {ui.removeCallbacks(timeout);host.close();if(previewDialog===dialog)previewDialog=null};dialog.show()
        dialog.window?.apply {setBackgroundDrawableResource(android.R.color.transparent);clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);setLayout(-1,-1)}
        ui.postDelayed(timeout,2000)
        host.prepare(Runnable {ui.removeCallbacks(timeout);val now=android.os.SystemClock.uptimeMillis();host.start(now,now+library.durationMs());ui.postDelayed(timeout,library.durationMs().toLong())},Runnable {dialog.dismiss()})
    }
    companion object {private val IO=Executors.newSingleThreadExecutor()}
}

/** Wallpaper preview has no charging timeout and owns its render resources. */
private class WallpaperScenePreview(context: android.content.Context,private val scene: ScreenScene): View(context),AutoCloseable {
    var animateScene=true
    private var artwork: WallpaperArtwork?=null
    private var generation=0
    private var closed=false
    private val started=android.os.SystemClock.uptimeMillis()
    private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=16*resources.displayMetrics.scaledDensity}
    private var failed=false
    private val ui=Handler(Looper.getMainLooper())
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        if(w<=0 || h<=0 || closed)return
        val token=++generation
        IO.execute {
            var next: WallpaperArtwork?=null
            val result=runCatching {next=WallpaperArtwork(scene.id,context,scene);next!!.prepare(w,h);next!!}
            if(result.isFailure)next?.close()
            ui.post {if(closed || token!=generation) {result.getOrNull()?.close();return@post}
                artwork?.close();artwork=result.getOrNull();failed=result.isFailure;invalidate()}
        }
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        canvas.drawColor(Color.BLACK)
        artwork?.draw(canvas,android.os.SystemClock.uptimeMillis()-started,animateScene)
        if(failed)canvas.drawText(when(WebViews.selectedLanguage(context)){"ja"->"プレビューを読み込めません";"en"->"Could not load preview";else->"미리보기를 불러오지 못했습니다"},20f,height/2f,paint)
        if(!closed && isShown && windowVisibility==VISIBLE && animateScene && artwork?.animated==true)postInvalidateDelayed(34)
    }
    override fun onWindowVisibilityChanged(visibility: Int){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate()}
    override fun close(){closed=true;generation++;artwork?.close();artwork=null}
    companion object {private val IO=Executors.newSingleThreadExecutor()}
}

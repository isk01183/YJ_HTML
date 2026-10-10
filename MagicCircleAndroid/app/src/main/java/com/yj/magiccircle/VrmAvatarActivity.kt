package com.yj.magiccircle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebView
import android.widget.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Saved avatars are recipes over immutable local VRMs, never replacements for their source files. */
class VrmAvatarActivity: Activity() {
    companion object {private val io=Executors.newSingleThreadExecutor()}
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var store: VrmAvatarStore
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var tools: LinearLayout?=null
    private var stage: FrameLayout?=null
    private var web: WebView?=null
    private var value: VrmAvatarDefinition?=null
    private var saved: VrmAvatarDefinition?=null
    private var pendingSave: VrmAvatarDefinition?=null
    private var entries=emptyList<VrmEntry>()
    private var parts=emptyList<VrmHairPart>()
    private var receipts=emptyMap<String,VrmHairReceipt>()
    private var candidate: VrmAvatarAppearance?=null
    private var thumbnail: VrmAvatarAppearance?=null
    private var renderTarget: VrmAvatarAppearance?=null
    private val failedThumbnails=mutableSetOf<String>()
    private var partsFailed=false
    private var capturing=false
    private var generateThumbnails=true
    private var tab="hair"
    private var dirty=false
    private var active=false
    private var ready=false
    private var busy=false
    private var epoch=0
    private var operation=0
    private var invalid=false
    private var syncing=false
    private var rawHex: String?=null
    private var restoredName: String?=null
    private var nameField: EditText?=null
    private val health=VrmRenderHealth()
    private val draftTask=Runnable {persistDraft()}
    private fun w(k: String,j: String,e: String)=when(WebViews.selectedLanguage(this)){"ja"->j;"en"->e;else->k}
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(text: String,size: Float=14f)=TextView(this).apply {this.text=text;textSize=size;setTextColor(0xff354052.toInt());setPadding(dp(8),dp(5),dp(8),dp(5))}
    private fun row(parent: LinearLayout)=LinearLayout(this).also {parent.addView(it)}
    private fun button(parent: LinearLayout,text: String,tag: String,action:()->Unit)=Button(this).apply {
        this.text=text;this.tag=tag;isAllCaps=false;textSize=12f;minHeight=dp(48)
        setTextColor(0xff354052.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xffe5e0d6.toInt())
        parent.addView(this,if(parent.orientation==LinearLayout.HORIZONTAL)LinearLayout.LayoutParams(0,-2,1f)else LinearLayout.LayoutParams(-1,-2))
        setOnClickListener {if(!busy&&candidate==null&&thumbnail==null)action()}
    }
    private fun shell() {
        closeWeb();tools=null;stage=null;nameField=null
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xfff6f3ec.toInt())}
        root.setOnApplyWindowInsetsListener {v,i->@Suppress("DEPRECATION")
            v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i}
        status=label("",12f).apply {tag="avatar-status";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        root.addView(status)
        setContentView(root)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);store=VrmAvatarStore.get(this);shell()
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){leave()}
        tab=state?.getString("tab") ?: "hair";rawHex=state?.getString("rawHex");invalid=state?.getBoolean("invalid") ?: false
        restoredName=state?.getString("nameRaw")
        val restored=state?.getString("value")?.let {runCatching {VrmAvatarRules.fromJson(JSONObject(it))}.getOrNull()}
        if(restored!=null) {
            value=restored;dirty=state.getBoolean("dirty");saved=state.getString("saved")?.let {VrmAvatarRules.fromJson(JSONObject(it))}
            pendingSave=state.getString("pendingSave")?.let {VrmAvatarRules.fromJson(JSONObject(it))}
            val request=pendingSave
            if(request==null)loadModels {showEditor()}
            else async({VrmModelStore.get(this).entries() to store.find(request.id)}) {data->
                // This read follows the in-flight write on the shared serial executor.
                entries=data.first
                if(data.second==request.copy(revision=request.revision+1)) {
                    value=data.second;saved=data.second;dirty=false;restoredName=null
                }
                pendingSave=null;loadModels {showEditor()}
            }
        } else if(intent.getBooleanExtra("library",false))showLibrary()
        else open(intent.getStringExtra("avatarId"))
    }
    private fun <T> async(work:()->T,done:(T)->Unit) {
        val token=++operation;busy=true;controls()
        io.execute {val result=runCatching(work);ui.post {
            if(isDestroyed||isFinishing||operation!=token)return@post
            busy=false;result.fold(done){status.text=w("처리하지 못했습니다. 기존 자료는 유지됩니다. 다시 열어 주세요.","処理できません。既存データは保持されます。開き直してください。","Could not complete. Existing data is preserved. Reopen and retry.")};controls()
        }}
    }
    private fun assemblyBudget(): Long {
        val manager=getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory=android.app.ActivityManager.MemoryInfo().also {manager.getMemoryInfo(it)}
        return minOf(128L*1024*1024,Runtime.getRuntime().maxMemory()/2,VrmMemoryPolicy.budget(memory.totalMem,memory.availMem,memory.threshold,memory.lowMemory,manager.isLowRamDevice))
    }
    private fun loadModels(done:()->Unit) {
        status.text=w("헤어 자료 확인 중… 처음 준비할 때는 몇 분 걸릴 수 있습니다.","ヘアを確認中…初回は数分かかる場合があります。","Checking hair assets… first preparation may take a few minutes.")
        async({
            val result=runCatching {val store=VrmHairPartStore.get(this);val found=store.prepare(assemblyBudget());found to found.associate {it.id to store.compose(it.id,assemblyBudget())}}
            VrmModelStore.get(this).entries() to result
        }) {data->
            entries=data.first;partsFailed=data.second.isFailure
            parts=data.second.getOrNull()?.first ?: emptyList();receipts=data.second.getOrNull()?.second ?: emptyMap();done()
        }
    }
    private fun open(id: String?) {
        async({Triple(VrmModelStore.get(this).entries(),id?.let {store.find(it)},id?.let {store.draft(it)})}) {data->
            entries=data.first;saved=data.second
            if(id!=null&&data.second==null&&data.third==null){status.text=w("캐릭터를 찾을 수 없습니다.","キャラクターが見つかりません。","Character not found.");return@async}
            value=data.third ?: saved ?: VrmAvatarDefinition(UUID.randomUUID().toString(),0,w("나의 캐릭터","私のキャラクター","My character"),VrmAvatarRules.original())
            dirty=data.third!=null;loadModels {showEditor()}
        }
    }
    private fun showLibrary() {
        operation++;candidate=null;thumbnail=null;value=null;saved=null;dirty=false;shell()
        root.addView(label(w("저장한 VRM 캐릭터","保存したVRMキャラクター","Saved VRM characters"),23f))
        val bar=row(root);button(bar,w("← 뒤로","← 戻る","← Back"),"avatar-back"){finish()}
        button(bar,w("＋ 새 캐릭터","＋ 新規","＋ New"),"avatar-new"){open(null)}
        val list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(this).apply {addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        async({store.list() to store.drafts()}) {data->
            if(data.first.isEmpty())list.addView(label(w("헤어와 색상을 꾸민 뒤 이름을 붙여 저장하세요.","髪と色を編集して名前を付けて保存。","Customize hair and colors, then save with a name.")))
            data.first.forEach {avatar->button(list,"${avatar.name} · r${avatar.revision}","avatar-saved-${avatar.id}"){open(avatar.id)}}
            if(data.second.isNotEmpty())list.addView(label(w("작성 중인 초안","編集中の下書き","Unfinished drafts"),18f))
            data.second.forEach {avatar->button(list,avatar.name,"avatar-draft-${avatar.id}"){open(avatar.id)}}
        }
    }
    private fun showEditor() {
        shell();val current=value ?: return
        val top=row(root)
        button(top,w("← 목록","← 一覧","← Library"),"avatar-back"){leave()}
        button(top,w("VRM 등록","VRM読込","Import VRM"),"avatar-import"){persistDraft();closeWeb();startActivity(Intent(this,VrmPreviewActivity::class.java))}
        root.addView(label(w("캐릭터 꾸미기","キャラクター編集","Character atelier"),23f))
        val workspace=LinearLayout(this);val wide=resources.configuration.screenWidthDp>=700
        workspace.orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        stage=FrameLayout(this).also {workspace.addView(it,if(wide)LinearLayout.LayoutParams(0,-1,1f)else LinearLayout.LayoutParams(-1,0,1.1f))}
        val side=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        workspace.addView(side,if(wide)LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>=1000)540 else 360),-1)else LinearLayout.LayoutParams(-1,0,1f))
        val tabs=row(side)
        button(tabs,w("헤어","ヘア","Hair"),"avatar-tab-hair"){switchTab("hair")}
        button(tabs,w("눈","目","Eyes"),"avatar-tab-iris-color"){switchTab("iris-color")}
        button(tabs,w("입","口","Mouth"),"avatar-tab-mouth"){switchTab("mouth")}
        button(tabs,w("얼굴형","顔型","Face"),"avatar-tab-face"){switchTab("face")}
        tools=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(8),0)}
        side.addView(ScrollView(this).apply {addView(tools)},LinearLayout.LayoutParams(-1,0,1f))
        nameField=EditText(this).apply {tag="avatar-name";setSingleLine();setText(restoredName ?: current.name);setTextColor(0xff354052.toInt());setHintTextColor(0xff65717b.toInt());hint=w("캐릭터 이름","名前","Character name");contentDescription=hint;filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
        restoredName=null
        nameField!!.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int){}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {val next=value?.copy(name=s.toString().trim()) ?: return;dirty=true;if(runCatching {VrmAvatarRules.validate(next)}.isSuccess){value=next;changed()}}
            override fun afterTextChanged(s: Editable?){}
        })
        root.addView(nameField,LinearLayout.LayoutParams(-1,dp(48)))
        val actions=row(root)
        button(actions,w("새 캐릭터로 저장","新規保存","Save as new"),"avatar-save-new"){save(true)}
        button(actions,w("수정 저장","変更を保存","Save changes"),"avatar-save"){save(false)}
        val secondary=row(root)
        button(secondary,w("저장값 복원","保存値に戻す","Revert saved"),"avatar-revert"){revert()}
        button(secondary,w("다시 시도","再試行","Retry"),"avatar-retry"){openWeb()}
        button(root,w("저장한 캐릭터로 배경화면 만들기","保存したキャラクターで壁紙作成","Create wallpaper with saved character"),"avatar-wallpaper"){
            val avatar=saved ?: return@button
            if(dirty || invalid)return@button
            closeWeb();startActivity(Intent(this,ScreenEditorActivity::class.java).putExtra("scenePurpose","WALLPAPER")
                .putExtra("avatarDefinition",VrmAvatarRules.toJson(avatar).toString()))
        }
        renderTools();openWeb();controls()
    }
    private fun switchTab(next: String) {if(invalid){status.text=w("올바른 색상을 입력하거나 원본으로 복원하세요.","正しい色を入力するか元に戻してください。","Enter a valid color or restore original.");return};tab=if(next=="hair-color")"hair"else next;rawHex=null;renderTools()}
    private fun thumbFile(id: String)=File(cacheDir,"avatar-part-thumb-v1-${VrmAvatarRules.BASE}-$id.png")
    private fun renderTools() {
        val panel=tools ?: return;panel.removeAllViews();val current=value ?: return
        if(tab=="hair") {
            panel.addView(label(w("검증된 E 모델 전용 · 원본 의상·몸은 유지","検証済みEモデル専用・衣装と体を保持","Verified E models only · body and outfit unchanged"),12f))
            val horizontal=resources.configuration.screenWidthDp>=1000
            val area=LinearLayout(this).apply {orientation=if(horizontal)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL};panel.addView(area)
            val gallery=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};area.addView(gallery,if(horizontal)LinearLayout.LayoutParams(0,-2,1f)else LinearLayout.LayoutParams(-1,-2))
            val cards=row(gallery)
            parts.forEach {part->
                val selected=current.appearance.parts["hair"]==part.id||(current.appearance.profileVersion==1&&current.appearance.hairId==part.styleId)
                val name=if(part.styleId=="e-original")w("E 기본","E 基本","E original")else"Hair 02"
                val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(3),dp(3),dp(3),dp(3));background=android.graphics.drawable.GradientDrawable().apply {setColor(0xffebe6dd.toInt());setStroke(dp(if(selected)2 else 1),if(selected)0xffba8b3b.toInt()else 0xffc7c4bd.toInt());cornerRadius=dp(8).toFloat()}}
                cards.addView(column,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),dp(3),dp(3),dp(3))})
                val file=thumbFile(part.id)
                if(file.isFile)column.addView(ImageView(this).apply {tag="avatar-photo-${part.id}";setImageBitmap(BitmapFactory.decodeFile(file.path));contentDescription=name;scaleType=ImageView.ScaleType.CENTER_CROP;setOnClickListener {selectHairPart(part.id)}},LinearLayout.LayoutParams(-1,dp(130)))
                else column.addView(label(w("사진 준비 중","写真準備中","Preparing portrait"),12f),LinearLayout.LayoutParams(-1,dp(130)))
                button(column,(if(selected)"✓ " else "")+name,"avatar-hair-${part.styleId}"){}.apply {
                    isSelected=selected;contentDescription=name+if(selected)w(" · 선택됨","・選択中"," · selected")else""
                    setOnClickListener {selectHairPart(part.id)}
                }
            }
            if(parts.size<2)gallery.addView(label(if(partsFailed)w("파츠 검증 실패. 기존 외형은 유지됩니다. 다시 열어 확인하세요.","パーツ検証失敗。元の外見は保持されます。","Part validation failed. Existing appearance is preserved. Reopen to retry.")else w("VRM 등록: AvatarSample_E.vrm + AvatarSample_E_Hair02.vrm\n새 VRM은 호환성 확인 후 목록에 추가합니다.","VRM読込：E + Hair02。新しいVRMは互換性確認後に追加。","Import AvatarSample_E.vrm + AvatarSample_E_Hair02.vrm. Future VRMs need compatibility review."),12f))
            val colors=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};area.addView(colors,if(horizontal)LinearLayout.LayoutParams(0,-2,1f)else LinearLayout.LayoutParams(-1,-2));colorTools(colors)
        } else {
            panel.addView(label(w("모양 교체용 파츠 준비 중 · 현재 외형 유지","形状パーツ準備中・現在の外見を保持","Shape parts not supplied yet · current appearance retained"),14f))
            if(tab=="iris-color")colorTools(panel)
        }
        controls()
    }
    private fun selectHairPart(partId: String) {
        if(invalid||pendingSave!=null||parts.none {it.id==partId}||!active)return
        val old=value ?: return;thumbnail=null;candidate=null;closeWeb()
        generateThumbnails=false
        status.text=w("선택한 헤어 준비 중…","選択したヘアを準備中…","Preparing selected hair…")
        async({runCatching {VrmHairPartStore.get(this).compose(partId,assemblyBudget())}}) {result->
            result.fold({receipt->candidate=receipt.appearance(old.appearance.dye);openWeb()}, {openWeb();status.text=w("헤어를 적용하지 못했습니다. 이전 외형을 유지합니다.","ヘアを適用できません。元の外見を保持します。","Hair could not be applied. Previous appearance retained.")})
        }
    }
    private fun colorTools(panel: LinearLayout) {
        val hair=tab=="hair"||tab=="hair-color";val dye=value!!.appearance.dye
        val selected=if(hair)dye.hair else dye.iris
        panel.addView(label(if(hair)w("머리카락만 염색","髪だけ染色","Hair only")else w("홍채만 염색 · 동공·흰자 유지","虹彩だけ染色・瞳孔と白目を保持","Iris only · pupil and eye whites preserved"),15f))
        val palette=listOf("#191C23","#F4EBDD","#EE2038","#204EFF","#AC73DB","#CF9B70","#69AD91","#ED91B9")
        palette.chunked(4).forEach {colors->val bar=row(panel);colors.forEach {hex->button(bar,"●", "avatar-palette-$hex"){setDye(hex);rawHex=hex;renderTools()}.apply {setTextColor(android.graphics.Color.parseColor(hex));textSize=26f;contentDescription=hex}}}
        val input=EditText(this).apply {tag="avatar-hex";setSingleLine();setText(rawHex ?: selected ?: "#FFFFFF");setTextColor(0xff354052.toInt());setHintTextColor(0xff65717b.toInt());hint="#RRGGBB";contentDescription="HEX #RRGGBB";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS}
        panel.addView(input,LinearLayout.LayoutParams(-1,dp(48)))
        val rgb=runCatching {android.graphics.Color.parseColor(input.text.toString())}.getOrDefault(-1)
        val channels=mutableListOf<SeekBar>()
        listOf("R","G","B").forEachIndexed {index,channel->
            val display=label("");panel.addView(display)
            val slider=SeekBar(this).apply {tag="avatar-${channel.lowercase()}";max=255;progress=(rgb shr (16-8*index)) and 255;minimumHeight=dp(48);contentDescription="$channel 0–255"}
            display.text="$channel  ${slider.progress}";channels.add(slider);panel.addView(slider)
            slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar,p: Int,user: Boolean) {display.text="$channel  $p";if(!syncing&&channels.size==3){val hex=VrmAvatarRules.fromRgb(channels[0].progress,channels[1].progress,channels[2].progress);syncing=true;input.setText(hex);input.error=null;syncing=false;rawHex=hex;invalid=false;setDye(hex)}}
                override fun onStartTrackingTouch(s: SeekBar){};override fun onStopTrackingTouch(s: SeekBar){}
            })
        }
        input.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int){}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {if(!syncing){rawHex=s.toString();invalid=true;controls()}}
            override fun afterTextChanged(s: Editable?){}
        })
        val bar=row(panel)
        button(bar,w("색상 적용","色を適用","Apply color"),"avatar-color-apply"){
            val hex=runCatching {VrmAvatarRules.parseHex(input.text.toString())}.getOrNull()
            if(hex==null){input.error=w("#RRGGBB 형식으로 입력하세요","#RRGGBBで入力","Use #RRGGBB");invalid=true;controls()}
            else {invalid=false;rawHex=hex;syncing=true;input.setText(hex);input.error=null;val c=android.graphics.Color.parseColor(hex);channels.forEachIndexed {i,s->s.progress=(c shr(16-8*i))and 255};syncing=false;setDye(hex)}
        }
        button(bar,w("이 부위 원본","この部位を戻す","Restore this part"),"avatar-color-reset"){invalid=false;rawHex=null;setDye(null);renderTools()}
        if(invalid)input.error=w("색상을 확인하세요","色を確認","Check color")
    }
    private fun setDye(color: String?) {
        val old=value ?: return;val dye=old.appearance.dye
        if(busy||candidate!=null||thumbnail!=null)return
        value=old.copy(appearance=old.appearance.copy(dye=if(tab=="hair"||tab=="hair-color")dye.copy(hair=color)else dye.copy(iris=color)))
        renderTarget=value!!.appearance
        web?.let {VrmWebView.appearance(it,value!!.appearance)};changed();controls()
    }
    private fun changed(){dirty=true;controls();ui.removeCallbacks(draftTask);ui.postDelayed(draftTask,250)}
    private fun persistDraft() {
        val draft=value ?: return;if(!dirty||pendingSave!=null)return
        io.execute {runCatching {store.saveDraft(draft)}.onFailure {ui.post {if(!isDestroyed)status.text=w("초안 저장 실패 — 다시 저장해 주세요","下書き保存失敗・再試行してください","Draft save failed — please retry")}}}
    }
    private fun controls() {
        if(!::root.isInitialized)return
        val locked=busy||candidate!=null||thumbnail!=null
        fun inputs(view: View) {
            if(view is EditText||view is SeekBar){view.isEnabled=!locked;if(locked)view.clearFocus()}
            if(view is Button && view.tag?.toString()?.startsWith("avatar-hair-")==true)view.isEnabled=pendingSave==null&&!invalid
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount)inputs(view.getChildAt(i))
        }
        inputs(root)
        root.findViewWithTag<Button>("avatar-save-new")?.isEnabled=ready&&!busy&&!invalid
        root.findViewWithTag<Button>("avatar-save")?.isEnabled=ready&&!busy&&!invalid&&saved!=null
        root.findViewWithTag<Button>("avatar-revert")?.isEnabled=!locked&&saved!=null
        root.findViewWithTag<Button>("avatar-wallpaper")?.isEnabled=ready&&!busy&&!invalid&&!dirty&&saved!=null
    }
    private fun save(asNew: Boolean) {
        if(!ready||invalid||busy)return
        val old=value ?: return;val next=old.copy(id=if(asNew&&old.revision>0)UUID.randomUUID().toString()else old.id,revision=if(asNew)0 else old.revision,name=nameField!!.text.toString().trim())
        if(runCatching {VrmAvatarRules.validate(next)}.isFailure){nameField!!.error=w("이름은 1–40자로 입력하세요","名前は1–40文字","Enter a name, 1–40 characters");return}
        ui.removeCallbacks(draftTask);pendingSave=next
        async({runCatching {store.save(next,if(asNew)null else saved?.revision)}}) {result->
            pendingSave=null
            result.fold({committed->
                value=committed;saved=committed;dirty=false;status.text=w("저장 완료 · 목록에서 다시 열 수 있습니다","保存済み・一覧から開けます","Saved · reopen from the library")
            },{status.text=w("저장하지 못했습니다. 변경 내용은 유지됩니다. 다시 시도하세요.","保存できません。編集内容は保持されます。再試行してください。","Could not save. Your edits are preserved. Please retry.")})
            if(active&&web==null)openWeb()
            controls()
        }
    }
    private fun revert() {
        val original=saved ?: return;ui.removeCallbacks(draftTask)
        async({store.discardDraft(original.id)}) {value=original;dirty=false;invalid=false;rawHex=null;showEditor()}
    }
    private fun openWeb() {
        closeWeb();val current=value ?: return;val container=stage ?: return
        val target=candidate ?: thumbnail ?: current.appearance;renderTarget=target
        if(entries.none {it.id==target.modelId}) {if(candidate!=null||thumbnail!=null)fail(VrmFailure.MODEL)else {status.text=w("필요한 VRM 파일을 먼저 등록하세요.","必要なVRMファイルを読み込んでください。","Import the required VRM file first.");controls()};return}
        val token=epoch;status.text=if(thumbnail!=null)w("실제 헤어 사진 준비 중…","ヘア写真を準備中…","Preparing real hair portraits…")else w("원본 화질로 불러오는 중…","元の画質で読込中…","Loading original-quality textures…")
        io.execute {
            val checked=runCatching {checkNotNull(VrmModelStore.get(this).openModel(target.modelId)).use {VrmModelStore.verifyOutput(target.modelId,it)};VrmHairPartStore.get(this).resolve(target)}
            ui.post {
                if(isDestroyed||isFinishing||token!=epoch||!active)return@post
                if(checked.isFailure){fail(VrmFailure.MODEL);return@post}
                val view=VrmWebView.create(this,{VrmModelStore.get(this).openModel(target.modelId)},{kind->if(token==epoch)fail(kind)},initialAppearance={target},initialPartReceipt={checked.getOrNull()}) {v->
                    if(web===v&&token==epoch){renderTarget?.let {VrmWebView.appearance(v,it)};v.evaluateJavascript("window.vrmPreview?.resume()",null)}
                }
                web=view;view.tag="avatar-web";container.addView(view,FrameLayout.LayoutParams(-1,-1))
                health.newAttempt(SystemClock.elapsedRealtime());health.setVisible(true,SystemClock.elapsedRealtime())
                view.loadUrl(VrmWebView.url(WebViews.selectedLanguage(this),true,false)+"&editor=1")
                ui.post(poll)
            }
        }
    }
    private val poll=object: Runnable {
        override fun run() {
            val view=web ?: return;if(!active)return
            val token=epoch
            view.evaluateJavascript("window.vrmPreview?.info || null") {raw->
                if(token!=epoch||web!==view||!active)return@evaluateJavascript
                val info=runCatching {JSONObject(raw)}.getOrNull()
                if(info?.optString("state")=="error"){fail(if(info.optString("failure")=="MEMORY")VrmFailure.MEMORY else VrmFailure.MODEL);return@evaluateJavascript}
                val matching=info?.optJSONObject("appearance")?.let {runCatching {VrmAvatarRules.readAppearance(it)==renderTarget}.getOrDefault(false)}==true
                val displayed=info?.optString("state")=="ready"&&info.optLong("frames")>1&&matching
                health.sample(displayed,info?.optLong("frames") ?: 0,SystemClock.elapsedRealtime())?.let {fail(it);return@evaluateJavascript}
                if(displayed&&!ready&&!capturing){
                    candidate?.let {next->value=value!!.copy(appearance=next);candidate=null;changed();renderTools()}
                    if(thumbnail==null){ready=!generateThumbnails||parts.all {thumbFile(it.id).isFile||it.id in failedThumbnails};status.text=w("머리와 눈 색상은 각각 따로 저장됩니다","髪と瞳の色は個別に保存されます","Hair and eye colors are saved independently")}
                    captureThumbnail(view,token);controls()
                }
            }
            ui.postDelayed(this,250)
        }
    }
    private fun captureThumbnail(view: WebView,token: Int) {
        val part=parts.firstOrNull {it.styleId==renderTarget?.hairId} ?: return
        val file=thumbFile(part.id)
        if(file.isFile){nextThumbnail();return}
        capturing=true
        view.evaluateJavascript("window.vrmPreview?.thumbnail()") {raw->
            if(token!=epoch||web!==view)return@evaluateJavascript
            val data=runCatching {JSONTokener(raw).nextValue() as String}.getOrNull()
            if(data==null||!data.startsWith("data:image/png;base64,")||data.length>512_000){capturing=false;failedThumbnails.add(part.id);nextThumbnail();return@evaluateJavascript}
            io.execute {val ok=runCatching {val bytes=android.util.Base64.decode(data.substringAfter(','),android.util.Base64.DEFAULT);val target=android.util.AtomicFile(file);val output=target.startWrite();try {output.write(bytes);target.finishWrite(output)}catch(e: Exception){target.failWrite(output);throw e}}.isSuccess
                ui.post {if(token==epoch&&!isDestroyed){capturing=false;if(!ok)failedThumbnails.add(part.id);if(tab=="hair")renderTools();nextThumbnail()}}}
        }
    }
    private fun nextThumbnail() {
        if(!active||busy||candidate!=null||invalid||!generateThumbnails)return
        val next=parts.firstOrNull {!thumbFile(it.id).isFile&&it.id !in failedThumbnails}
        if(next!=null){thumbnail=receipts[next.id]?.appearance();if(thumbnail!=null)openWeb()}
        else if(thumbnail!=null){thumbnail=null;openWeb()}
        else {ready=true;controls()}
    }
    private fun fail(kind: VrmFailure) {
        val recover=candidate!=null||thumbnail!=null
        thumbnail?.let {t->parts.firstOrNull {it.styleId==t.hairId}?.let {failedThumbnails.add(it.id)}}
        candidate=null;thumbnail=null;closeWeb(kind==VrmFailure.RENDERER)
        if(recover&&active)openWeb()
        status.text=if(kind==VrmFailure.MEMORY)w("원본 화질을 표시할 메모리가 부족합니다. 다른 앱을 닫고 다시 시도하세요.","メモリ不足です。他のアプリを閉じて再試行してください。","Not enough memory for original textures. Close other apps and retry.")else w("표시하지 못했습니다. 이전 외형과 저장 자료를 유지합니다.","表示できません。以前の外見と保存データは保持されます。","Could not render. Previous appearance and saved data are preserved.");controls()
    }
    private fun closeWeb(crashed: Boolean=false) {
        epoch++;ready=false;capturing=false;ui.removeCallbacks(poll);val view=web;web=null
        if(!crashed){runCatching {view?.evaluateJavascript("window.vrmPreview?.dispose()",null)};runCatching {view?.stopLoading()}}
        stage?.removeAllViews();runCatching {view?.destroy()};controls()
    }
    private fun leave() {
        if(busy)return
        if(value==null){finish();return}
        if(!dirty&&!invalid){showLibrary();return}
        AlertDialog.Builder(this).setMessage(w("변경 내용을 어떻게 할까요?","変更をどうしますか？","What would you like to do with your edits?"))
            .setPositiveButton(w("저장","保存","Save")){_,_->save(saved==null)}
            .setNeutralButton(w("초안 유지 후 목록","下書きを保持して一覧","Keep draft & library")){_,_->persistDraft();ui.removeCallbacks(draftTask);showLibrary()}
            .setNegativeButton(w("계속 편집","編集を続ける","Keep editing"),null).show()
    }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("API 23–32 fallback") override fun onBackPressed()=leave()
    override fun onResume() {super.onResume();active=true;if(value!=null&&stage!=null&&web==null&&!busy)loadModels {openWeb()}}
    override fun onStop() {active=false;candidate=null;thumbnail=null;if(pendingSave==null){operation++;busy=false};ui.removeCallbacks(draftTask);persistDraft();closeWeb();super.onStop()}
    override fun onSaveInstanceState(out: Bundle) {value?.let {out.putString("value",VrmAvatarRules.toJson(it).toString())};saved?.let {out.putString("saved",VrmAvatarRules.toJson(it).toString())};pendingSave?.let {out.putString("pendingSave",VrmAvatarRules.toJson(it).toString())};out.putString("tab",tab);out.putBoolean("dirty",dirty);out.putString("rawHex",rawHex);out.putString("nameRaw",nameField?.text?.toString());out.putBoolean("invalid",invalid);super.onSaveInstanceState(out)}
    override fun onDestroy(){operation++;closeWeb();ui.removeCallbacksAndMessages(null);super.onDestroy()}
}

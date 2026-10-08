package com.yj.magiccircle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.*
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class CharacterActivity: Activity() {
    private val ui=Handler(Looper.getMainLooper())
    companion object {private val io=Executors.newSingleThreadExecutor()}
    private lateinit var store: CharacterStore
    private lateinit var root: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var status: TextView
    private var preview: CharacterPreviewView?=null
    private var value: CharacterDefinition?=null
    private var tab=0
    private var dirty=false
    private var busy=false
    private var generation=0
    private val raw=linkedMapOf<String,String>()
    private val invalid=hashSetOf<String>()
    private var colorDialog: AlertDialog?=null
    private var colorKey: String?=null
    private var colorRaw: List<String>?=null
    private var colorSource=0
    private var toolScroll: ScrollView?=null
    private val thumbnails=android.util.LruCache<String,Bitmap>(36)
    private val draftTask=Runnable { persistDraft() }
    private val ink=0xff343b50.toInt()
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun w(k: String,j: String,e: String)=when(WebViews.selectedLanguage(this)){"ja"->j;"en"->e;else->k}
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store=CharacterStore.get(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or if(Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        state?.getBundle("raw")?.let { b->b.keySet().forEach { raw[it]=b.getString(it) ?: "" } }
        invalid.addAll(state?.getStringArrayList("invalid") ?: emptyList())
        tab=state?.getInt("tab") ?: 0
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){leave()}
        val restored=state?.getString("definition")?.let { runCatching {CharacterRules.fromJson(JSONObject(it))}.getOrNull() }
        if(restored!=null) {
            value=restored; dirty=state.getBoolean("dirty");showEditor()
            toolScroll?.post {toolScroll?.scrollTo(0,state.getInt("scroll"))}
            preview?.restoreView(state.getFloat("zoom",1f),state.getFloat("panX"),state.getFloat("panY"))
            state.getString("colorKey")?.let { key->colorRaw=state.getStringArrayList("colorRaw");colorSource=state.getInt("colorSource");openColor(key) }
        } else {
            shell(); val id=intent.getStringExtra("characterId")
            if(id!=null) load(id) else showLibrary()
        }
    }
    private fun shell(): LinearLayout {
        preview?.close(); preview=null
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xfff6f3ec.toInt());setPadding(dp(16),dp(8),dp(16),dp(8))}
        root.setOnApplyWindowInsetsListener {v,i -> @Suppress("DEPRECATION")
            v.setPadding(dp(16)+i.systemWindowInsetLeft,dp(8)+i.systemWindowInsetTop,dp(16)+i.systemWindowInsetRight,dp(8)+i.systemWindowInsetBottom);i }
        status=label(w("나만의 작은 세계","自分だけの小さな世界","A little world of your own"),12f).apply {setTextColor(0xff767080.toInt())}
        setContentView(root);return root
    }
    private fun label(text: String,size: Float=14f) = TextView(this).apply { this.text=text;textSize=size;setTextColor(ink);setPadding(dp(4),dp(6),dp(4),dp(6)) }
    private fun row(parent: LinearLayout) = LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;parent.addView(this,LinearLayout.LayoutParams(-1,-2))}
    private fun button(parent: LinearLayout,text: String,tag: String="",selected: Boolean=false,action:()->Unit): Button = Button(this).apply {
        this.text=text; this.tag=tag; isAllCaps=false;textSize=12f;minHeight=dp(48);setTextColor(if(selected)0xffffffff.toInt() else ink)
        background=GradientDrawable().apply {cornerRadius=dp(12).toFloat();setColor(if(selected)0xff606e89.toInt()else 0xffeae5db.toInt());setStroke(dp(1),0xffd3c8b7.toInt())}
        val p=if(parent.orientation==LinearLayout.HORIZONTAL)LinearLayout.LayoutParams(0,-2,1f)else LinearLayout.LayoutParams(-1,-2)
        p.setMargins(dp(3),dp(4),dp(3),dp(4));parent.addView(this,p);setOnClickListener {if(!busy)action()}
    }
    private fun error() {status.text=w("저장하지 못했습니다. 기존 자료는 보존됩니다.","保存できません。既存データは保護されます。","Could not save. Existing data is preserved.")}
    private fun enable(view: View,enabled: Boolean) {view.isEnabled=enabled;if(view is android.view.ViewGroup)for(i in 0 until view.childCount)enable(view.getChildAt(i),enabled)}
    private fun <T> async(work:()->T,done:(T)->Unit) {
        busy=true;enable(root,false);val token=++generation
        io.execute { val result=runCatching(work);ui.post {if(isDestroyed || token!=generation)return@post;busy=false;enable(root,true);result.fold(done){error()} } }
    }
    private fun showLibrary() {
        value=null;dirty=false;shell();root.addView(label(w("캐릭터 아틀리에","キャラクターアトリエ","Character atelier"),26f));root.addView(status)
        val bar=row(root)
        button(bar,w("← 돌아가기","← 戻る","← Back")){finish()}
        button(bar,w("＋ 새 캐릭터","＋ 新規","＋ Create"),"character-new") {
            value=CharacterRules.defaults(UUID.randomUUID().toString(),w("별빛","星あかり","Starlight"));raw.clear();invalid.clear();tab=0;dirty=true;showEditor();scheduleDraft()
        }
        button(root,w("VRM 3D 미리보기","VRM 3Dプレビュー","VRM 3D preview"),"vrm-preview") {
            startActivity(Intent(this,VrmPreviewActivity::class.java))
        }
        val scroll=ScrollView(this);val list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        async({store.list() to store.drafts()}) { (entries,drafts) ->
            if(drafts.isNotEmpty()) {
                list.addView(label(w("작성 중 · 이어서 편집","下書き・編集を続ける","Drafts · Continue editing"),18f))
                drafts.forEach { d->button(list,d.name){load(d.id)} }
                list.addView(label(w("저장한 캐릭터","保存したキャラクター","Saved characters"),18f))
            }
            if(entries.isEmpty())list.addView(label(w("외형과 의상을 꾸미고 배경화면에 초대하세요.","外見と衣装を選んで壁紙へ。","Create a character and invite them to your wallpaper.")))
            entries.forEach { c ->
                val card=row(list)
                card.addView(ImageView(this).apply {setImageBitmap(thumbnail(c));contentDescription=c.name},LinearLayout.LayoutParams(dp(100),dp(144)))
                val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};card.addView(column,LinearLayout.LayoutParams(0,-2,1f))
                column.addView(label(c.name,19f));button(column,w("꾸미기","編集","Customize")){load(c.id)}
                button(column,w("배경화면에 사용","壁紙に使う","Use in wallpaper")){useWallpaper(c)}
                val actions=row(list)
                button(actions,w("복제","複製","Duplicate")){async({store.save(c.copy(id=UUID.randomUUID().toString(),name=(c.name+" 2").take(40)))}){showLibrary()}}
                button(actions,w("이름 변경","名前変更","Rename")){rename(c)}
                button(actions,w("삭제","削除","Delete")){AlertDialog.Builder(this).setMessage(w("이 캐릭터를 삭제할까요? 기존 배경화면은 유지됩니다.","削除しますか？既存の壁紙は変わりません。","Delete this character? Existing wallpapers are unchanged."))
                    .setNegativeButton(w("취소","取消","Cancel"),null).setPositiveButton(w("삭제","削除","Delete")){_,_->async({store.delete(c.id)}){showLibrary()}}.show()}
            }
        }
    }
    private fun rename(c: CharacterDefinition) {
        val field=EditText(this).apply {setText(c.name);isSingleLine=true}
        val dialog=AlertDialog.Builder(this).setTitle(w("이름 변경","名前変更","Rename")).setView(field).setNegativeButton(w("취소","取消","Cancel"),null).setPositiveButton(w("저장","保存","Save"),null).create()
        dialog.setOnShowListener {dialog.getButton(-1).setOnClickListener {
            val next=c.copy(name=field.text.toString().trim())
            if(runCatching {CharacterRules.validate(next)}.isFailure) {field.error=w("1–40자의 이름을 입력하세요","1–40文字で入力","Enter 1–40 characters");return@setOnClickListener}
            async({store.rename(c.id,next.name)}){dialog.dismiss();showLibrary()}
        }};dialog.show()
    }
    private fun load(id: String) {
        async({(store.draft(id) ?: store.find(id)) ?: throw IllegalArgumentException("Unknown character")}) { c->
            value=c;dirty=true;raw.clear();invalid.clear();showEditor()
        }
    }
    private fun showEditor() {
        val c=value ?: return;shell()
        val header=row(root)
        button(header,w("← 보관함","← 一覧","← Library")){leave()}
        header.addView(label(w("캐릭터 꾸미기","キャラクター編集","Customize"),21f),LinearLayout.LayoutParams(0,-2,2f))
        root.addView(status)
        val workspace=LinearLayout(this).apply {orientation=if(resources.configuration.screenWidthDp>=700)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL}
        root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        val stage=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val wide=workspace.orientation==LinearLayout.HORIZONTAL
        workspace.addView(stage,if(wide)LinearLayout.LayoutParams(0,-1,1.2f)else LinearLayout.LayoutParams(-1,0,1f))
        preview=CharacterPreviewView(this).apply {setCharacter(c)}
        stage.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        val viewTools=row(stage)
        button(viewTools,"2.5D",selected=true){}
        button(viewTools,"3D"){AlertDialog.Builder(this).setMessage(w("별도의 VRM 파일을 미리 봅니다. 현재 2.5D 캐릭터를 변환하거나 배경화면에 적용하지 않습니다.","別のVRMファイルを表示します。2.5Dキャラクターの変換や壁紙への適用は行いません。","Preview a separate VRM file. This does not convert your 2.5D character or apply a wallpaper."))
            .setNegativeButton(w("취소","取消","Cancel"),null).setPositiveButton(w("VRM 열기","VRMを開く","Open VRM")){_,_->startActivity(Intent(this,VrmPreviewActivity::class.java))}.show()}
        button(viewTools,w("얼굴 확대","顔を拡大","Face")){preview?.faceView()}
        button(viewTools,w("보기 초기화","表示リセット","Reset view")){preview?.resetView()}
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        workspace.addView(panel,if(wide)LinearLayout.LayoutParams(0,-1,1f)else LinearLayout.LayoutParams(-1,0,1.08f))
        val name=EditText(this).apply {tag="character-name";isSingleLine=true;setText(raw["name"] ?: c.name);setTextColor(ink);textSize=18f;contentDescription=w("캐릭터 이름","名前","Character name")}
        panel.addView(name,LinearLayout.LayoutParams(-1,dp(48)))
        watch(name) { s->raw["name"]=s;val next=value!!.copy(name=s.trim());if(runCatching {CharacterRules.validate(next)}.isSuccess){invalid.remove("name");change(next)}else{invalid.add("name");name.error=w("이름은 1–40자","名前は1–40文字","Name must be 1–40 characters")} }
        val tabs=row(panel)
        val titles=listOf(w("외형","外見","Look"),w("체형","体型","Body"),w("의상","衣装","Outfit"),w("색상","色","Color"),w("동작","動作","Motion"))
        titles.forEachIndexed { i,title -> button(tabs,title,"character-tab-$i",tab==i) {tab=i;renderTools();toolScroll?.scrollTo(0,0);for(n in 0 until tabs.childCount) {val b=tabs.getChildAt(n) as Button;b.isSelected=n==i;b.setTextColor(if(n==i)0xffffffff.toInt()else ink);(b.background as GradientDrawable).setColor(if(n==i)0xff606e89.toInt()else 0xffeae5db.toInt())}} }
        val scroll=ScrollView(this);toolScroll=scroll;tools=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};scroll.addView(tools);panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val actions=row(root)
        button(actions,w("저장","保存","Save"),"character-save"){save(false)}
        button(actions,w("저장하고 배경화면 만들기","保存して壁紙を作る","Save & create wallpaper")){save(true)}
        renderTools()
    }
    private fun change(c: CharacterDefinition) {value=c;dirty=true;preview?.setCharacter(c);scheduleDraft()}
    private fun scheduleDraft() {ui.removeCallbacks(draftTask);ui.postDelayed(draftTask,300)}
    private fun persistDraft() {
        val c=value ?: return;if(!dirty)return
        io.execute {runCatching {store.saveDraft(c)}.onFailure {ui.post {if(!isDestroyed)error()}}}
    }
    private fun renderTools() {
        tools.removeAllViews();val c=value ?: return
        when(tab) {
            0->CharacterRules.appearanceIds.forEachIndexed { group,ids ->
                tools.addView(label(appearanceName(group),16f))
                val choices=row(tools)
                ids.forEach { id ->
                    val values=CharacterRules.appearanceValues(c.appearance).toMutableList();values[group]=id
                    val variant=c.copy(appearance=CharacterRules.appearance(values))
                    val cell=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};choices.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
                    cell.addView(ImageView(this).apply {setImageBitmap(thumbnail(variant,group!=0));contentDescription=partName(id)},LinearLayout.LayoutParams(-1,dp(if(group==0)100 else 78)))
                    button(cell,partName(id),"appearance-${CharacterRules.appearanceKeys[group]}-$id",CharacterRules.appearanceValues(c.appearance)[group]==id) {
                        val current=CharacterRules.appearanceValues(value!!.appearance).toMutableList();current[group]=id
                        change(value!!.copy(appearance=CharacterRules.appearance(current)));renderTools()
                    }
                }
            }
            1->{
                tools.addView(label(w("키는 전체 비율을 유지합니다. 핀치는 보기 배율만 바꿉니다.","身長は比率を維持。ピンチは表示のみ。","Height keeps proportions. Pinch changes only the view."),12f))
                c.body.values().forEachIndexed { i,f ->
                    val key=CharacterRules.bodyKeys[i]; val factor=if(i==0)1f else 100f
                    tools.addView(label(bodyName(i)))
                    val r=row(tools);val input=EditText(this).apply {tag="body-$key";isSingleLine=true;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(raw[key] ?: (f*factor).toInt().toString());setTextColor(ink);contentDescription=bodyName(i)}
                    r.addView(input,LinearLayout.LayoutParams(dp(85),dp(48)))
                    val min=(CharacterRules.range(i).start*factor).toInt();val max=(CharacterRules.range(i).endInclusive*factor).toInt()
                    val slider=SeekBar(this).apply {this.max=max-min;progress=(f*factor).toInt()-min;contentDescription=bodyName(i)};r.addView(slider,LinearLayout.LayoutParams(0,dp(48),1f))
                    watch(input) { s->raw[key]=s;val n=s.toFloatOrNull()?.div(factor)
                        if(n!=null && n.isFinite() && n in CharacterRules.range(i)) {invalid.remove(key);slider.progress=(n*factor).toInt()-min;change(value!!.copy(body=value!!.body.with(i,n)))}
                        else {invalid.add(key);input.error="$min–$max"}
                    }
                    slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar,p: Int,user: Boolean) {if(user)input.setText((p+min).toString())}
                        override fun onStartTrackingTouch(s: SeekBar){};override fun onStopTrackingTouch(s: SeekBar){}
                    })
                }
                button(tools,w("체형 초기화","体型リセット","Reset body")){CharacterRules.bodyKeys.forEach {raw.remove(it);invalid.remove(it)};change(value!!.copy(body=BodyProportions()));renderTools()}
            }
            2->OutfitSlot.entries.forEach { slot ->
                tools.addView(label(slotName(slot),16f));val choices=row(tools)
                CharacterRules.outfitIds.getValue(slot).forEach { id -> button(choices,partName(id),"outfit-${slot.name}-$id",c.outfit[slot]==id) {change(value!!.copy(outfit=value!!.outfit+(slot to id)));renderTools()} }
            }
            3->{CharacterRules.colorKeys.forEach { key ->
                val hex=CharacterColors.hex(c.colors.getValue(key));button(tools,colorName(key)+"  "+hex,"color-$key") {openColor(key)}.compoundDrawablePadding=dp(8)
            }}
            4->CharacterMotion.entries.forEach { motion->button(tools,when(motion){CharacterMotion.STILL->w("가만히","静止","Still");CharacterMotion.IDLE->w("호흡 · 눈 깜박임","呼吸・まばたき","Breathe & blink");CharacterMotion.WAVE->w("손 흔들기","手を振る","Wave")},selected=c.motion==motion){change(value!!.copy(motion=motion));renderTools()} }
        }
    }
    private fun openColor(key: String) {
        val original=value ?: return;val color=original.colors.getValue(key)
        colorKey=key
        val labels=listOf("HEX #RRGGBB","R · 0–255","G · 0–255","B · 0–255")
        val texts=colorRaw ?: listOf(CharacterColors.hex(color),(color ushr 16 and 255).toString(),(color ushr 8 and 255).toString(),(color and 255).toString())
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0)}
        val fields=labels.mapIndexed { i,label->panel.addView(this.label(label));EditText(this).apply {isSingleLine=true;inputType=if(i==0)InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER;setText(texts[i]);contentDescription=label;panel.addView(this)} }
        var updating=false;var candidate: Int?=color;var accepted=false
        val dialog=AlertDialog.Builder(this).setTitle(colorName(key)).setView(panel).setNegativeButton(w("취소","取消","Cancel"),null).setNeutralButton(w("기본색","初期色","Default"),null).setPositiveButton(w("적용","適用","Apply"),null).create()
        fun recalc(source: Int) {
            if(updating)return
            colorSource=source
            candidate=if(source==0)CharacterColors.parseHex(fields[0].text.toString()) else CharacterColors.parseRgb(fields[1].text.toString(),fields[2].text.toString(),fields[3].text.toString())
            val next=candidate
            if(next!=null) {
                updating=true
                if(source==0) { fields[1].setText((next ushr 16 and 255).toString());fields[2].setText((next ushr 8 and 255).toString());fields[3].setText((next and 255).toString()) }
                else fields[0].setText(CharacterColors.hex(next))
                updating=false;preview?.setCharacter(original.copy(colors=original.colors+(key to next)))
            }
            fields[source].error=if(next==null)w("유효한 값을 입력하세요","有効な値を入力","Enter a valid value")else null
            colorRaw=fields.map {it.text.toString()};dialog.getButton(-1)?.isEnabled=next!=null
        }
        fields.forEachIndexed { i,f->watch(f){recalc(i)} }
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {candidate?.let { next->accepted=true;change(original.copy(colors=original.colors+(key to next)));dialog.dismiss();renderTools()} }
            dialog.getButton(-3).setOnClickListener {fields[0].setText(CharacterColors.hex(CharacterRules.defaults(original.id,original.name).colors.getValue(key)))}
            recalc(colorSource.coerceIn(0,3))
        }
        dialog.setOnDismissListener {if(!accepted && !isDestroyed)preview?.setCharacter(value ?: original);colorKey=null;colorRaw=null;colorSource=0;colorDialog=null}
        colorDialog=dialog;dialog.show()
    }
    private fun save(wallpaper: Boolean) {
        if(invalid.isNotEmpty()) {status.text=w("입력 오류를 먼저 수정하세요","入力エラーを修正してください","Correct the highlighted inputs first");return}
        val c=value ?: return;ui.removeCallbacks(draftTask)
        async({store.save(c)}) {dirty=false;if(wallpaper)useWallpaper(c)else showLibrary()}
    }
    private fun useWallpaper(c: CharacterDefinition) {startActivity(Intent(this,ScreenEditorActivity::class.java).putExtra("characterId",c.id).putExtra("scenePurpose","WALLPAPER"))}
    private fun leave() {
        if(busy)return
        if(value==null){finish();return}
        if(!dirty){showLibrary();return}
        AlertDialog.Builder(this).setMessage(w("변경 사항을 저장할까요?","変更を保存しますか？","Save changes?"))
            .setPositiveButton(w("저장","保存","Save")){_,_->save(false)}
            .setNegativeButton(w("버리기","破棄","Discard")){_,_->ui.removeCallbacks(draftTask);val id=value!!.id;async({store.discardDraft(id)}){dirty=false;showLibrary()}}
            .setNeutralButton(w("계속 편집","編集を続ける","Keep editing"),null).show()
    }
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ uses the native dispatcher registered in onCreate.
    @Deprecated("API 23–32 fallback") override fun onBackPressed()=leave()
    override fun onSaveInstanceState(out: Bundle) {
        value?.let {out.putString("definition",CharacterRules.toJson(it).toString())};out.putBoolean("dirty",dirty);out.putInt("tab",tab);out.putInt("scroll",toolScroll?.scrollY ?: 0)
        out.putBundle("raw",Bundle().also {b->raw.forEach { (k,v)->b.putString(k,v) }});out.putStringArrayList("invalid",ArrayList(invalid))
        preview?.let {out.putFloat("zoom",it.zoom);out.putFloat("panX",it.panX);out.putFloat("panY",it.panY)}
        out.putString("colorKey",colorKey);out.putInt("colorSource",colorSource);colorRaw?.let {out.putStringArrayList("colorRaw",ArrayList(it))};super.onSaveInstanceState(out)
    }
    override fun onStart(){super.onStart();preview?.setActive(true)}
    override fun onStop(){preview?.setActive(false);ui.removeCallbacks(draftTask);if(!busy)persistDraft();super.onStop()}
    override fun onDestroy(){generation++;ui.removeCallbacksAndMessages(null);preview?.close();preview=null;colorDialog?.dismiss();thumbnails.evictAll();super.onDestroy()}
    private fun watch(field: EditText,changed:(String)->Unit) {field.addTextChangedListener(object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int){};override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int){changed(s.toString())};override fun afterTextChanged(s: Editable?){}
    })}
    private fun thumbnail(c: CharacterDefinition,face: Boolean=false): Bitmap {
        val key=CharacterRules.toJson(c).toString()+face;thumbnails.get(key)?.let {return it}
        val image=Bitmap.createBitmap(144,176,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);canvas.drawColor(0xffe4e0eb.toInt())
        val renderer=CharacterRenderer(c.copy(motion=CharacterMotion.STILL));renderer.prepare(144,244)
        if(face) {canvas.scale(2.5f,2.5f,72f,0f);canvas.translate(0f,-48f)}else canvas.scale(.72f,.72f,72f,0f)
        renderer.draw(canvas,0,false);renderer.close();thumbnails.put(key,image);return image
    }
    private fun appearanceName(i: Int)=listOf(w("몸체","体型","Body type"),w("헤어","髪","Hair"),w("얼굴형","顔型","Face shape"),w("눈","目","Eyes"),w("눈썹","眉","Brows"),w("코","鼻","Nose"),w("입","口","Mouth"))[i]
    private fun bodyName(i: Int)=listOf(w("키 (cm)","身長 (cm)","Height (cm)"),w("머리 크기 (%)","頭 (%)","Head (%)"),w("어깨너비 (%)","肩幅 (%)","Shoulders (%)"),w("상체 길이 (%)","胴の長さ (%)","Torso length (%)"),w("팔 길이 (%)","腕の長さ (%)","Arm length (%)"),w("다리 길이 (%)","脚の長さ (%)","Leg length (%)"),w("몸통 두께 (%)","胴の幅 (%)","Torso width (%)"),w("팔 두께 (%)","腕の幅 (%)","Arm width (%)"),w("다리 두께 (%)","脚の幅 (%)","Leg width (%)"))[i]
    private fun slotName(s: OutfitSlot)=when(s){OutfitSlot.HALO->w("헤일로","輪","Halo");OutfitSlot.HEAD->w("머리장식","髪飾り","Headwear");OutfitSlot.TORSO->w("몸통","服","Clothing");OutfitSlot.GLOVES->w("장갑","手袋","Gloves");OutfitSlot.SHOES->w("신발","靴","Shoes");OutfitSlot.WINGS->w("날개","翼","Wings")}
    private fun colorName(key: String): String {
        val first=key.substringBefore('.');val title=when(first){"skin"->w("피부","肌","Skin");"hair"->w("헤어","髪","Hair");"eyes"->w("눈동자","瞳","Iris");else->slotName(OutfitSlot.valueOf(first.uppercase(java.util.Locale.ROOT)))}
        return title+if(key.endsWith(".base"))w(" · 기본색","・基本色"," · Base")else if(key.endsWith(".accent"))w(" · 보조색","・装飾色"," · Accent")else ""
    }
    private fun partName(id: String)=when(id) {
        "feminine"->w("여성형","女性型","Feminine");"masculine"->w("남성형","男性型","Masculine");"bob"->w("단발","ボブ","Bob");"long"->w("긴 형태","ロング","Long");"tied"->w("묶은 머리","結んだ髪","Tied")
        "round"->w("둥근 형태","丸型","Round");"oval"->w("타원형","卵型","Oval");"soft-square"->w("부드러운 각형","角型","Soft square");"almond"->w("아몬드형","アーモンド","Almond");"soft"->w("부드러운 형태","柔らかい","Soft")
        "straight"->w("일자형","直線","Straight");"arched"->w("아치형","アーチ","Arched");"small"->w("작은 형태","小さい","Small");"defined"->w("선명한 형태","くっきり","Defined");"smile"->w("미소","笑顔","Smile")
        "none"->w("없음","なし","None");"ring"->w("빛의 고리","光の輪","Ring");"star"->w("별 장식","星","Star");"ribbon"->w("리본","リボン","Ribbon");"tunic"->w("튜닉","チュニック","Tunic");"coat"->w("코트","コート","Coat");"robe"->w("로브","ローブ","Robe")
        "short"->w("짧은 장갑","短い手袋","Short");"boots"->w("부츠","ブーツ","Boots");"flats"->w("플랫 슈즈","フラット","Flats");"feather"->w("깃털 날개","羽根","Feather");"fairy"->w("요정 날개","妖精","Fairy");else->id
    }
}

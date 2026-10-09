package com.yj.magiccircle

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** The editor and wallpaper picker share the same full-size, first-frame-gated preview. */
internal class VrmSceneDialog(activity: Activity,scene: ScreenScene,onApply: (()->Unit)?=null,
    onClosed: ()->Unit={}): Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen) {
    private val library=MediaLibrary.get(activity)
    private val lease=library.leaseMedia(scene.layers.map {it.mediaId})
    private fun w(ko: String,ja: String,en: String)=when(WebViews.selectedLanguage(context)){"ja"->ja;"en"->en;else->ko}
    init {
        val state=TextView(context).apply {setTextColor(Color.WHITE);text=w("캐릭터 준비 중…","準備中…","Preparing character…");setPadding(24,48,24,0)}
        val apply=Button(context).apply {tag="vrm-compose-apply";isEnabled=false;text=w("시스템에서 적용","システムで設定","Apply in system settings")}
        val host=VrmSceneView(context,scene,{VrmModelStore.get(activity).openModel(scene.vrm!!.modelId)},{library.open(it,false)},
            {state.text="";apply.isEnabled=true},{apply.isEnabled=false;state.text=w("미리보기를 불러오지 못했습니다. 파일이나 캐릭터를 다시 선택하세요.","プレビューを読み込めません。再選択してください。","Cannot load preview. Choose the character or files again.")})
        val frame=FrameLayout(context).apply {addView(host,FrameLayout.LayoutParams(-1,-1));addView(state,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))}
        val bar=LinearLayout(context).apply {setPadding(16,24,16,36)}
        bar.addView(Button(context).apply {text=w("닫기","閉じる","Close");tag="vrm-compose-close";setOnClickListener {dismiss()}},LinearLayout.LayoutParams(0,-2,1f))
        if(onApply!=null){bar.addView(apply,LinearLayout.LayoutParams(0,-2,1f));apply.setOnClickListener {if(host.ready){dismiss();onApply()}}}
        frame.addView(bar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(frame)
        setOnDismissListener {host.close();lease.close();onClosed()}
    }
}

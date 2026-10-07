package com.yj.magiccircle

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

internal fun words(language: String, ko: String, ja: String, en: String) = when(language) { "ko"->ko; "ja"->ja; else->en }

class ChargeInfoView(context: Context): FrameLayout(context) {
    private var information=emptyList<InfoPlacement>()
    private var snapshot=ChargeSnapshot(null,null,null,null,null)
    private var language="ko"
    private var progress=.5f
    private val labels=InfoField.entries.associateWith { field -> TextView(context).apply {
        gravity=Gravity.CENTER; setTextColor(0xfff5e8cb.toInt())
        typeface=Typeface.create(if(field==InfoField.BATTERY) "serif" else "sans-serif-medium",Typeface.NORMAL)
        textSize=if(field==InfoField.BATTERY) 40f else 13f
        setShadowLayer(3f,0f,1f,0xff070d18.toInt())
        maxLines=2; visibility=View.GONE
        addView(this, LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT))
    } }
    fun setInformation(items: List<InfoPlacement>) { SceneRules.validateInformation(items); information=items.toList(); refresh(); requestLayout() }
    fun update(snapshot: ChargeSnapshot,language: String,progress: Float) {
        this.snapshot=snapshot; this.language=language; this.progress=progress; refresh()
    }
    private fun refresh() {
        fun w(k: String,j: String,e: String)=words(language,k,j,e)
        val unknown=w("알 수 없음","不明","Unknown")
        val connection=when(snapshot.plugged) { 0->w("미연결","未接続","Disconnected");1->"AC";2->"USB";4->w("무선","ワイヤレス","Wireless");8->w("도크","ドック","Dock");else->unknown }
        val status=if(snapshot.plugged==0) connection else when(snapshot.status) {2->w("충전 중","充電中","Charging");3->w("방전 중","放電中","Discharging");4->w("충전 대기","充電待機","Not charging");5->w("충전 완료","充電完了","Fully charged");else->unknown}
        val health=when(snapshot.health) {2->w("양호","良好","Good");3->w("과열","過熱","Overheated");4->w("수명 저하","劣化","Dead");5->w("과전압","過電圧","Overvoltage");6->w("오류","異常","Failure");7->w("저온","低温","Cold");else->unknown}
        for((field,label) in labels) {
            label.visibility=if(information.any { it.field==field && it.visible }) VISIBLE else GONE
            label.text=when(field) {
                InfoField.BATTERY->snapshot.percent?.let { "$it%" } ?: "—"
                InfoField.STATUS->status
                InfoField.TEMPERATURE->(snapshot.temperatureC?.let { String.format(Locale.ROOT,"%.1f°C",it) } ?: "—")+"\n"+w("배터리 온도","バッテリー温度","Temperature")
                InfoField.HEALTH->health+"\n"+w("배터리 상태","バッテリー状態","Battery health")
                InfoField.CONNECTION->connection+"\n"+w("연결 방식","接続方式","Connection")
                InfoField.METER->"⚡ "+(snapshot.percent?.let { "▰".repeat(it/10)+"▱".repeat(10-it/10) } ?: "—")
                InfoField.MESSAGE->when {progress<.28f->w("충전 단자가 연결되었습니다","充電端子が接続されました","Charger connected");progress<.83f->w("충전 시작","充電開始","Charging begins");else->w("연결 완료","接続完了","Connection complete")}
            }
        }
        contentDescription=labels.values.filter { it.visibility==VISIBLE }.joinToString(". ") { it.text }
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        labels.values.forEach { it.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    }
    override fun onMeasure(w: Int,h: Int) {
        val width=MeasureSpec.getSize(w); val height=MeasureSpec.getSize(h)
        setMeasuredDimension(width,height)
        labels.values.forEach { it.measure(MeasureSpec.makeMeasureSpec(maxOf(1,width-paddingLeft-paddingRight),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(maxOf(1,height-paddingTop-paddingBottom),MeasureSpec.AT_MOST)) }
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        val w=width-paddingLeft-paddingRight;val h=height-paddingTop-paddingBottom
        for(p in information) { val v=labels.getValue(p.field)
            val x=(paddingLeft+p.x*w-v.measuredWidth/2).toInt().coerceIn(paddingLeft,maxOf(paddingLeft,width-paddingRight-v.measuredWidth))
            val y=(paddingTop+p.y*h-v.measuredHeight/2).toInt().coerceIn(paddingTop,maxOf(paddingTop,height-paddingBottom-v.measuredHeight))
            v.layout(x,y,x+v.measuredWidth,y+v.measuredHeight)
        }
    }
    fun placementAt(x: Float,y: Float): InfoField? = information.asReversed().firstOrNull { p -> val v=labels.getValue(p.field);p.visible && x>=v.left && x<=v.right && y>=v.top && y<=v.bottom }?.field
    companion object {
        @JvmStatic fun defaultInformation(id: String): List<InfoPlacement> {
            val center=id in listOf("native-N01","premium","ref-U04")
            return InfoField.entries.map { field ->
                val pos=when(field) {InfoField.BATTERY->.5f to if(center) .47f else .82f; InfoField.STATUS->.5f to if(center) .53f else .89f
                    InfoField.TEMPERATURE->.19f to .90f;InfoField.HEALTH->.5f to .90f;InfoField.CONNECTION->.81f to .90f
                    InfoField.METER->.5f to .86f;InfoField.MESSAGE->.5f to .16f}
                InfoPlacement(field,pos.first,pos.second,when(field) {
                    InfoField.BATTERY,InfoField.STATUS->id!="classic"
                    InfoField.TEMPERATURE,InfoField.HEALTH,InfoField.CONNECTION->id=="native-N01" || id=="premium"
                    InfoField.METER->id=="layered" || id=="raphael"
                    InfoField.MESSAGE->id=="classic"
                })
            }
        }
    }
}

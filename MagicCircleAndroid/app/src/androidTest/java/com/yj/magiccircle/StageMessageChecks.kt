package com.yj.magiccircle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object StageMessageChecks {
    fun run(context: Context) {
        val messages=JSONObject().put("connected","연결 <별>& 빛").put("charging","魔力充填中").put("complete","")
        val placement=JSONObject().put("field","MESSAGE").put("x",.5).put("y",.7).put("visible",true).put("messages",messages)
        val source=SceneData().json().put("layouts",JSONObject().put("classic",JSONArray().put(placement)))
        val data=SceneData.read(source,emptyMap())
        val view=ChargeInfoView(context)
        view.setInformation(data.layouts.getValue("classic"))
        val snapshot=ChargeSnapshot(69,32.5f,2,2,2)
        view.update(snapshot,"ko",.1f)
        check(view.contentDescription.toString()=="연결 <별>& 빛") {"Connected stage ignored the saved custom text"}
        view.update(snapshot,"en",.5f)
        check(view.contentDescription.toString()=="魔力充填中") {"Charging stage text changed with app language"}
        view.update(snapshot,"ja",.95f)
        check(view.contentDescription.isNullOrEmpty()) {"Empty stage must be hidden"}
        val roundTrip=SceneData.read(data.json(),emptyMap())
        check(roundTrip==data) {"Custom stage text lost during reload"}
        check(data.json().getJSONObject("layouts").getJSONArray("classic").getJSONObject(0).getJSONObject("messages").getString("connected")=="연결 <별>& 빛")
        val root=java.io.File(context.cacheDir,"message-check-${java.util.UUID.randomUUID()}").apply {check(mkdirs())}
        var library=MediaLibrary(context,root,"classic")
        val info=data.layouts.getValue("classic")
        library.saveEditorDraft(EditorDraft("classic",null,info))
        library=MediaLibrary(context,root,"classic")
        check(library.editorDraft("classic")!!.information==info) {"Draft stage text lost on reload"}
        library.saveChargeInfo("classic",info)
        library=MediaLibrary(context,root,"classic")
        check(library.chargeInfo("classic")==info && library.editorDraft("classic")==null)
        library.saveChargeInfo("classic",null)
        check(MediaLibrary(context,root,"classic").chargeInfo("classic")==null) {"Reset failed"}
        view.setInformation(listOf(InfoPlacement(InfoField.MESSAGE,.5f,.7f,true)))
        view.update(snapshot,"ko",.1f)
        check(view.contentDescription.toString()=="충전 단자가 연결되었습니다") {"Legacy localized default changed"}
        view.setInformation(listOf(InfoPlacement(InfoField.MESSAGE,.5f,.5f,true,StageMessages("1\n2\n3\n4\n5","", ""))))
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(1920,1073741824))
        view.layout(0,0,1080,1920)
        val label=(0 until view.childCount).map {view.getChildAt(it)}.filterIsInstance<android.widget.TextView>().single {it.visibility==android.view.View.VISIBLE}
        check(label.lineCount==5 && label.layout.getLineBottom(4)<=label.height-label.totalPaddingTop-label.totalPaddingBottom) {"Accepted stage text was clipped"}
        for(field in listOf("MESSAGE","BATTERY")) {
            placement.put("field",field)
            messages.put("connected",if(field=="MESSAGE") "가".repeat(121) else "text")
            check(runCatching {SceneData.read(source,emptyMap())}.isFailure) {"Invalid stage text was accepted"}
        }
    }
}

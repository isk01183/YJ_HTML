package com.yj.magiccircle

import android.app.Instrumentation
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object VrmHairChecks {
    fun run(test: Instrumentation,baseId: String,assembledId: String,pixels: Long) {
        val output=File(test.targetContext.getExternalFilesDir(null),"vrm-hair-${UUID.randomUUID()}")
        check(output.mkdir())
        var anchor: JSONObject?=null
        val report=JSONObject().put("baseId",baseId).put("assembledId",assembledId)
        VrmMemoryChecks.run(test,baseId,assembledId,pixels) {web,isBase->
            fun js(script: String): JSONObject {
                val latch=CountDownLatch(1);var result="null"
                test.runOnMainSync {web.evaluateJavascript(script){result=it;latch.countDown()}}
                check(latch.await(15,TimeUnit.SECONDS)) {"Review script timed out"}
                return JSONObject(result)
            }
            if(isBase)anchor=js("window.vrmPreview.reviewAnchor()")
            val fixed=checkNotNull(anchor)
            val label=if(isBase)"E-original" else "E-Hair02-assembled"
            val views=JSONObject()
            for(frame in listOf("body","face"))for(yaw in listOf(0,90,180,270)) {
                val settings=JSONObject(fixed.getJSONObject(frame).toString()).put("yaw",yaw).put("blink",0)
                val state=js("window.vrmPreview.reviewView($settings)")
                check(state.toString()==js("window.vrmPreview.reviewView($settings)").toString()) {"Unstable fixed camera"}
                views.put("$frame-$yaw",state)
                capture(test,web,File(output,"$label-$frame-$yaw.png"))
            }
            val blink=JSONObject(fixed.getJSONObject("face").toString()).put("yaw",0).put("blink",1)
            check(js("window.vrmPreview.reviewView($blink)").getDouble("blink")==1.0)
            capture(test,web,File(output,"$label-blink.png"))
            val motion=js("window.vrmPreview.reviewMotion()")
            check(motion.getInt("jointCount")>0&&motion.getInt("jointCount")==motion.getInt("uniqueBones")) {"Missing/duplicate spring joints: $motion"}
            check(motion.getBoolean("finite")&&motion.getJSONArray("moved").length()>0) {"Spring update failed: $motion"}
            report.put(label,JSONObject().put("views",views).put("motion",motion))
            Log.i("VrmChecks","HAIR_REVIEW $label motion=$motion")
        }
        report.put("anchor",anchor)
        File(output,"review.json").writeText(report.toString(2))
        Log.i("VrmChecks","HAIR_CAPTURE_PATH ${output.absolutePath}")
    }

    private fun capture(test: Instrumentation,web: WebView,file: File) {
        val ready=CountDownLatch(1)
        test.runOnMainSync {web.postVisualStateCallback(1,object: WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long){ready.countDown()}
        })}
        check(ready.await(15,TimeUnit.SECONDS)) {"Review frame not submitted"}
        Thread.sleep(250)
        val window=(web.context as Activity).window
        val image=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
        val copied=CountDownLatch(1);var status=-1
        try {
            // Android CLI may own UiAutomation; copy this app's hardware window without competing for it.
            test.runOnMainSync {PixelCopy.request(window,image,{status=it;copied.countDown()},Handler(Looper.getMainLooper()))}
            check(copied.await(15,TimeUnit.SECONDS)&&status==PixelCopy.SUCCESS) {"Window capture failed: $status"}
            file.outputStream().use {check(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        }
        finally {image.recycle()}
    }
}

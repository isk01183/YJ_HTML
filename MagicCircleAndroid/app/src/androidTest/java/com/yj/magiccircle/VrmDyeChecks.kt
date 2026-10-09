package com.yj.magiccircle

import android.app.Instrumentation
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object VrmDyeChecks {
    fun run(test: Instrumentation) {
        val output=File(test.targetContext.getExternalFilesDir(null),"vrm-dye-${UUID.randomUUID()}").apply {check(mkdir())}
        VrmMemoryChecks.run(test,VrmAvatarRules.BASE,VrmAvatarRules.HAIR02,54_067_328L) {web,isBase->
            fun js(script: String): JSONObject {
                val ready=CountDownLatch(1);var result="null"
                test.runOnMainSync {web.evaluateJavascript("(()=>{try{return $script}catch(e){return {error:e.message}}})()") {result=it;ready.countDown()}}
                check(ready.await(15,TimeUnit.SECONDS));val json=JSONObject(result);check(!json.has("error")){json.toString()};return json
            }
            val anchor=js("window.vrmPreview.reviewAnchor()").getJSONObject("face").put("yaw",0).put("blink",0)
            val original=js("window.vrmPreview.info")
            val hairId=if(isBase)"e-original" else "e-hair02"
            val appearance=VrmAvatarAppearance(1,VrmAvatarRules.BASE,hairId,VrmAvatarRules.modelId(hairId))
            for(part in listOf("hair","iris"))for(color in listOf<String?>(null,"#000000","#F4EBDD","#EE2038","#204EFF")) {
                val settings=appearance.copy(dye=if(part=="hair")VrmDye(hair=color) else VrmDye(iris=color))
                val json=VrmAvatarRules.appearanceJson(settings)
                js("(window.vrmPreview.appearance($json),window.vrmPreview.reviewView($anchor))")
                val capture=File(output,"$hairId-$part-${color?.drop(1) ?: "original"}.png")
                VrmHairChecks.capture(test,web,capture)
                if(part=="hair"&&color=="#F4EBDD") {
                    val bitmap=android.graphics.BitmapFactory.decodeFile(capture.absolutePath)
                    try {if(bitmap.height>bitmap.width) {
                        var clipped=0;var total=0
                        for(y in (bitmap.height*.39).toInt()..(bitmap.height*.42).toInt())for(x in (bitmap.width*.43).toInt()..(bitmap.width*.56).toInt()) {
                            val pixel=bitmap.getPixel(x,y);total++
                            if(android.graphics.Color.red(pixel)>250&&android.graphics.Color.green(pixel)>250&&android.graphics.Color.blue(pixel)>250)clipped++
                        }
                        check(clipped.toDouble()/total<.20){"Platinum hair lost texture: clipped=$clipped/$total"}
                    }}finally{bitmap.recycle()}
                }
                val state=js("window.vrmPreview.info")
                for(key in listOf("texturePixels","gpuTextures","liveBitmaps","decodedImages"))check(state.getLong(key)==original.getLong(key)){"Dye grew $key"}
            }
            repeat(80) {i->js("(window.vrmPreview.appearance(${VrmAvatarRules.appearanceJson(appearance.copy(dye=VrmDye(if(i%2==0)"#000000" else null,"#FF2255")))}),window.vrmPreview.reviewView($anchor))")}
            js("(window.vrmPreview.appearance(null),window.vrmPreview.reviewView($anchor))")
            VrmHairChecks.capture(test,web,File(output,"$hairId-restored.png"))
        }
        android.util.Log.i("VrmChecks","DYE_CAPTURE_PATH ${output.absolutePath}")
    }
}

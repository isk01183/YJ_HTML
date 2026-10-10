package com.yj.magiccircle

import android.app.Instrumentation
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.webkit.WebView
import android.app.Activity
import android.os.SystemClock
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object VrmHairPartEditorChecks {
    fun run(test: Instrumentation,swaps: Int=20) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"))
        require(swaps in 0..20)
        val context=test.targetContext;val selected=VrmModelStore.get(context).selected()?.id
        val field=VrmAvatarStore::class.java.getDeclaredField("instance").apply {isAccessible=true};val previous=field.get(null)
        val store=VrmAvatarStore(File(context.cacheDir,"hair-editor-${UUID.randomUUID()}"));field.set(null,store)
        val untouched=store.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Unchanged",VrmAvatarRules.original()),null)
        var activity: Activity?=null
        val output=File(context.getExternalFilesDir(null),"hair-editor-${UUID.randomUUID()}").apply {check(mkdir())}
        fun main(action:()->Unit){var error: Throwable?=null;test.runOnMainSync {try{action()}catch(e: Throwable){error=e}};error?.let {throw it}}
        fun waitFor(label: String,condition:()->Boolean){val end=SystemClock.elapsedRealtime()+600_000;while(!condition()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);check(condition()){label}}
        fun button(tag: String)=activity!!.window.decorView.findViewWithTag<Button>(tag)
        fun ready(): Boolean {var result=false;main {result=button("avatar-save-new")?.isEnabled==true};return result}
        fun info(): JSONObject {val latch=CountDownLatch(1);var text="null";main {activity!!.window.decorView.findViewWithTag<WebView>("avatar-web").evaluateJavascript("window.vrmPreview.info"){text=it;latch.countDown()}};check(latch.await(20,TimeUnit.SECONDS));return JSONObject(text)}
        fun capture(name: String){val bitmap=test.uiAutomation.takeScreenshot();checkNotNull(bitmap);File(output,name).outputStream().use {check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
        try {
            activity=test.startActivitySync(Intent(context,VrmAvatarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Gallery never became ready",::ready)
            val parts=VrmHairPartStore.get(context).prepare(128L*1024*1024);check(parts.size==2)
            main {
                check(button("avatar-tab-face")!=null&&button("avatar-tab-mouth")!=null)
                parts.forEach {check(activity!!.window.decorView.findViewWithTag<ImageView>("avatar-photo-${it.id}")!=null){"Missing real portrait"}}
                check(button("avatar-hair-e-original").isSelected)
                activity!!.window.decorView.findViewWithTag<EditText>("avatar-hex").setText("#204EFF");button("avatar-color-apply").performClick()
                check(activity!!.window.decorView.findViewWithTag<SeekBar>("avatar-r").progress==32)
                button("avatar-tab-iris-color").performClick();activity!!.window.decorView.findViewWithTag<EditText>("avatar-hex").setText("#EE2038");button("avatar-color-apply").performClick()
                button("avatar-tab-hair").performClick()
            }
            capture("gallery-original.png")
            main {button("avatar-hair-e-hair02").performClick()};waitFor("Hair02 not ready",::ready)
            var appearance=VrmAvatarRules.readAppearance(info().getJSONObject("appearance"));check(appearance.profileVersion==2&&appearance.hairId=="e-hair02"&&appearance.dye==VrmDye("#204EFF","#EE2038"))
            check(VrmHairPartStore.get(context).resolve(appearance)!=null)
            main {check(button("avatar-hair-e-hair02").isSelected)};capture("gallery-hair02.png")
            // Queued completion from older choices cannot publish over the final choice.
            main {repeat(20){button(if(it%2==0)"avatar-hair-e-hair02"else"avatar-hair-e-original").performClick()}}
            waitFor("Latest rapid choice not ready",::ready);check(info().getJSONObject("appearance").getString("hairId")=="e-original")
            val pixels=mutableMapOf<String,Long>()
            repeat(swaps){n->val style=if(n%2==0)"e-hair02"else"e-original"
                main {button("avatar-hair-$style").performClick()};waitFor("Repeated swap $n failed",::ready)
                val rendered=info();check(rendered.getJSONObject("appearance").getString("hairId")==style)
                val actual=rendered.getLong("texturePixels");check(actual>0);pixels[style]?.let {check(it==actual)};pixels[style]=actual
                android.util.Log.i("VrmChecks","HAIR_SWAP_OK ${n+1} $style pixels=$actual")
            }
            check(store.find(untouched.id)==untouched)
            main {button("avatar-save-new").performClick()};waitFor("New character not saved"){store.list().size==2};waitFor("Save still busy",::ready)
            val saved=store.list().single {it.id!=untouched.id};check(store.draft(saved.id)==null)
            val monitor=test.addMonitor(VrmAvatarActivity::class.java.name,null,false)
            main {button("avatar-hair-e-hair02").performClick();activity!!.recreate()}
            activity=test.waitForMonitorWithTimeout(monitor,15000);test.removeMonitor(monitor);check(activity!=null)
            waitFor("Rotation did not restore committed appearance",::ready)
            check(VrmAvatarRules.readAppearance(info().getJSONObject("appearance"))==saved.appearance)
            val candidate=VrmAvatarActivity::class.java.getDeclaredField("candidate").apply {isAccessible=true}
            val fail=VrmAvatarActivity::class.java.getDeclaredMethod("fail",VrmFailure::class.java).apply {isAccessible=true}
            main {button("avatar-hair-e-hair02").performClick()}
            waitFor("Candidate never loaded"){var pending=false;main {pending=candidate.get(activity)!=null};pending}
            main {fail.invoke(activity,VrmFailure.MODEL)};waitFor("Failed candidate did not recover",::ready)
            check(VrmAvatarRules.readAppearance(info().getJSONObject("appearance"))==saved.appearance)
            check(store.find(saved.id)==saved&&store.draft(saved.id)==null)
            main {button("avatar-hair-e-hair02").performClick()}
            val active=VrmAvatarActivity::class.java.getDeclaredField("active").apply {isAccessible=true}
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Selection did not stop"){var stopped=false;main {stopped=!active.getBoolean(activity)};stopped}
            context.startActivity(Intent(context,VrmAvatarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("Selection did not resume",::ready)
            check(VrmAvatarRules.readAppearance(info().getJSONObject("appearance"))==saved.appearance)
            main {button("avatar-hair-e-hair02").performClick()};waitFor("Retry failed",::ready)
            check(info().getJSONObject("appearance").getString("hairId")=="e-hair02")
            main {button("avatar-revert").performClick()};waitFor("Revert failed",::ready)
            main {button("avatar-hair-e-hair02").performClick()}
            waitFor("Back candidate never loaded"){var pending=false;main {pending=candidate.get(activity)!=null};pending}
            main {VrmAvatarActivity::class.java.getDeclaredMethod("leave").apply {isAccessible=true}.invoke(activity)}
            waitFor("Back failed to leave candidate"){var library=false;main {library=button("avatar-new")!=null};library}
            check(store.find(saved.id)==saved&&store.draft(saved.id)==null)
            check(store.find(untouched.id)==untouched&&VrmModelStore.get(context).selected()?.id==selected)
            android.util.Log.i("VrmChecks","HAIR_EDITOR_CAPTURE ${output.path}")
        }finally {main {activity?.finish()};test.waitForIdleSync();field.set(null,previous)}
    }
}

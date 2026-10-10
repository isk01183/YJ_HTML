package com.yj.magiccircle

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object VrmAvatarEditorChecks {
    fun run(test: Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"))
        val type=Class.forName("com.yj.magiccircle.VrmAvatarActivity")
        val field=VrmAvatarStore::class.java.getDeclaredField("instance").apply {isAccessible=true}
        val prior=field.get(null)
        val store=VrmAvatarStore(File(test.targetContext.cacheDir,"avatar-editor-${UUID.randomUUID()}"))
        val saved=store.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Editor check",VrmAvatarRules.original()),null)
        field.set(null,store)
        var activity: Activity?=null
        val output=File(test.targetContext.getExternalFilesDir(null),"vrm-avatar-editor-${UUID.randomUUID()}").apply {check(mkdir())}
        fun main(action:()->Unit) {var error: Throwable?=null;test.runOnMainSync {try{action()}catch(e: Throwable){error=e}};error?.let {throw it}}
        fun waitUntil(label: String,condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+600_000
            while(!condition()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100)
            check(condition()){label}
        }
        fun button(tag: String)=activity!!.window.decorView.findViewWithTag<Button>(tag)
        fun ready(): Boolean {var yes=false;main {yes=button("avatar-save-new")?.isEnabled==true};return yes}
        fun js(script: String): JSONObject {
            val latch=CountDownLatch(1);var result="null"
            main {activity!!.window.decorView.findViewWithTag<WebView>("avatar-web").evaluateJavascript(script){result=it;latch.countDown()}}
            check(latch.await(15,TimeUnit.SECONDS));return JSONObject(result)
        }
        try {
            activity=test.startActivitySync(Intent(test.targetContext,type).putExtra("avatarId",saved.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil("Avatar editor did not render",::ready)
            // draftDoesNotAlterSavedAvatar + rgbHexSyncAndValidation
            main {button("avatar-tab-hair").performClick();activity!!.window.decorView.findViewWithTag<EditText>("avatar-hex").setText("#204EFF");button("avatar-color-apply").performClick()}
            waitUntil("Draft did not persist"){store.draft(saved.id)?.appearance?.dye?.hair=="#204EFF"}
            check(store.find(saved.id)==saved)
            main {
                val root=activity!!.window.decorView
                check(root.findViewWithTag<SeekBar>("avatar-r").progress==32)
                root.findViewWithTag<EditText>("avatar-hex").setText("#12");button("avatar-color-apply").performClick()
                check(root.findViewWithTag<EditText>("avatar-hex").error!=null)
                check(!button("avatar-save-new").isEnabled)
                root.findViewWithTag<EditText>("avatar-hex").setText("#204EFF");button("avatar-color-apply").performClick()
                root.findViewWithTag<SeekBar>("avatar-r").progress=90
                check(root.findViewWithTag<EditText>("avatar-hex").text.toString()=="#5A4EFF")
                button("avatar-tab-iris-color").performClick()
                root.findViewWithTag<EditText>("avatar-hex").setText("#EE2038");button("avatar-color-apply").performClick()
            }
            waitUntil("Independent colors not persisted"){store.draft(saved.id)?.appearance?.dye==VrmDye("#5A4EFF","#EE2038")}
            VrmHairChecks.capture(test,activity!!.window.decorView.findViewWithTag("avatar-web"),File(output,"colors.png"))
            // rotationRestoresUnsavedDraft: platform recreation, not a manual field copy.
            val monitor=test.addMonitor(type.name,null,false)
            main {activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").setText("Unsaved name")}
            main {activity!!.recreate()}
            activity=test.waitForMonitorWithTimeout(monitor,15000);test.removeMonitor(monitor)
            check(activity!=null);waitUntil("Recreated avatar did not render",::ready)
            main {check(activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").text.toString()=="Unsaved name"){"Rotation lost unsaved name"}}
            main {check(activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").currentTextColor==0xff354052.toInt()){ "Name contrast is unreadable" }}
            check(js("window.vrmPreview.info.appearance").getString("hair")=="#5A4EFF")
            check(store.find(saved.id)==saved)
            // latestHairSelectionWins, including callbacks from the destroyed previous WebViews.
            main {button("avatar-tab-hair").performClick();button("avatar-hair-e-hair02").performClick();button("avatar-hair-e-original").performClick();button("avatar-hair-e-hair02").performClick()}
            waitUntil("Latest hair selection not rendered",::ready)
            check(js("window.vrmPreview.info.appearance").getString("hairId")=="e-hair02")
            VrmHairChecks.capture(test,activity!!.window.decorView.findViewWithTag("avatar-web"),File(output,"hair.png"))
            // cancelRestoresSavedValues: explicit reset to the saved revision.
            main {button("avatar-revert").performClick()}
            waitUntil("Saved revision was not restored",::ready)
            check(js("window.vrmPreview.info.appearance").isNull("hair"));check(store.find(saved.id)==saved)
            check(store.draft(saved.id)==null)
            // Real save buttons keep two names for one source, then edit only the selected saved ID.
            main {activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").setText("Saved one");button("avatar-save-new").performClick()}
            waitUntil("First new avatar not saved"){store.list().any {it.name=="Saved one"}}
            waitUntil("First save still busy",::ready)
            main {activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").setText("Saved two");button("avatar-save-new").performClick()}
            waitUntil("Second new avatar not saved"){store.list().any {it.name=="Saved two"}}
            check(store.list().size==3&&store.find(saved.id)==saved)
            waitUntil("Second save still busy",::ready)
            val second=store.list().single {it.name=="Saved two"}
            main {button("avatar-tab-hair").performClick();activity!!.window.decorView.findViewWithTag<EditText>("avatar-hex").setText("#ED91B9");button("avatar-color-apply").performClick();button("avatar-save").performClick()}
            waitUntil("Edit was not saved"){store.find(second.id)?.revision==2}
            main {activity!!.finish()};test.waitForIdleSync()
            activity=test.startActivitySync(Intent(test.targetContext,type).putExtra("avatarId",second.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil("Reopened saved avatar not rendered",::ready)
            check(js("window.vrmPreview.info.appearance").getString("hair")=="#ED91B9")
            check(store.list().single {it.name=="Saved one"}.appearance.dye.hair==null)
            android.util.Log.i("VrmChecks","AVATAR_EDITOR_CAPTURE_PATH ${output.absolutePath}")
        } finally {activity?.let {main {it.finish()}};test.waitForIdleSync();field.set(null,prior)}
    }
}

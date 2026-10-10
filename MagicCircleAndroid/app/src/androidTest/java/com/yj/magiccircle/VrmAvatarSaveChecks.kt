package com.yj.magiccircle

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.util.AtomicFile
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object VrmAvatarSaveChecks {
    fun run(test: Instrumentation,mode: String) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"));require(mode in listOf("identity","busy","rotation","resume"))
        val root=File(test.targetContext.cacheDir,"avatar-save-${UUID.randomUUID()}")
        val blockNext=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val file=object: AtomicFile(File(root,"avatars.json")) {
            override fun startWrite(): FileOutputStream {if(blockNext.compareAndSet(true,false)){entered.countDown();check(release.await(45,TimeUnit.SECONDS))};return super.startWrite()}
        }
        val store=VrmAvatarStore(root,file)
        val saved=if(mode=="identity")null else store.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Before",VrmAvatarRules.original()),null)
        val field=VrmAvatarStore::class.java.getDeclaredField("instance").apply {isAccessible=true};val previous=field.get(null);field.set(null,store)
        var activity: Activity?=null
        fun main(action:()->Unit){var error: Throwable?=null;test.runOnMainSync {try{action()}catch(e: Throwable){error=e}};error?.let {throw it}}
        fun waitFor(label: String,check:()->Boolean){val end=SystemClock.elapsedRealtime()+600_000;while(!check()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);check(check()){label}}
        fun button(tag: String)=activity!!.window.decorView.findViewWithTag<Button>(tag)
        fun ready(): Boolean {var result=false;main {result=button("avatar-save-new")?.isEnabled==true};return result}
        try {
            activity=test.startActivitySync(Intent(test.targetContext,VrmAvatarActivity::class.java).apply {saved?.let {putExtra("avatarId",it.id)};addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
            waitFor("Initial avatar not ready",::ready)
            main {activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").setText("Edited");button("avatar-tab-hair").performClick();activity!!.window.decorView.findViewWithTag<SeekBar>("avatar-r").progress=42}
            waitFor("Draft was not saved"){store.drafts().any {it.name=="Edited"}}
            val draft=store.drafts().single()
            if(mode=="identity") {
                main {button("avatar-save-new").performClick()};waitFor("First character not saved"){store.list().isNotEmpty()}
                check(store.find(draft.id)!=null && store.drafts().isEmpty()){ "First save orphaned its draft" }
            } else {
                blockNext.set(true);main {button("avatar-save").performClick()};check(entered.await(10,TimeUnit.SECONDS))
                if(mode=="busy")main {
                    check(!activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").isEnabled){"Name editable during save"}
                    check(!activity!!.window.decorView.findViewWithTag<EditText>("avatar-hex").isEnabled){"HEX editable during save"}
                    listOf("r","g","b").forEach {check(!activity!!.window.decorView.findViewWithTag<SeekBar>("avatar-$it").isEnabled){"RGB editable during save"}}
                }
                if(mode=="rotation") {
                    val monitor=test.addMonitor(VrmAvatarActivity::class.java.name,null,false)
                    main {activity!!.recreate()};activity=test.waitForMonitorWithTimeout(monitor,15000);test.removeMonitor(monitor);check(activity!=null)
                }
                if(mode=="resume") {
                    test.targetContext.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    val active=VrmAvatarActivity::class.java.getDeclaredField("active").apply {isAccessible=true}
                    waitFor("Editor did not stop"){var stopped=false;main {stopped=!active.getBoolean(activity)};stopped}
                    test.targetContext.startActivity(Intent(test.targetContext,VrmAvatarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    waitFor("Editor did not resume"){var resumed=false;main {resumed=active.getBoolean(activity)};resumed}
                }
                release.countDown();waitFor("Saved revision did not publish"){store.find(saved!!.id)?.revision==2};waitFor("Editor did not recover",::ready)
                val current=VrmAvatarActivity::class.java.getDeclaredField("value").apply {isAccessible=true}
                main {check((current.get(activity) as VrmAvatarDefinition).revision==2){"Editor kept stale revision after $mode"}}
                main {activity!!.window.decorView.findViewWithTag<EditText>("avatar-name").setText("After lifecycle");button("avatar-save").performClick()}
                waitFor("Next save stuck on stale revision"){store.find(saved!!.id)?.revision==3}
                check(store.find(saved!!.id)!!.appearance.dye==draft.appearance.dye)
            }
        } finally {release.countDown();main {activity?.finish()};test.waitForIdleSync();field.set(null,previous)}
    }
}
